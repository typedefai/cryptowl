package com.typedefai.cryptowl

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class ChatMessage(val isUser: Boolean) {
    var text by mutableStateOf("")
    var thinkingText by mutableStateOf("")
    var tokenSpeed by mutableStateOf(0f)
    var latencyMs by mutableStateOf(-1f)
    var images: List<Bitmap> by mutableStateOf(emptyList())
}

/**
 * Mirrors the AI Edge Gallery's LLM chat model management:
 * - config values are in-memory per session, seeded from the model's defaults
 *   (topK=64, topP=0.95, temperature=1.0, maxTokens=4096 — the E2B allowlist
 *   entry's defaultConfig, slider capped at its 4096 context length)
 * - only the system prompt persists (gallery: DataStore; here: SharedPreferences)
 * - the model initializes when the chat screen opens (INITIALIZING overlay +
 *   error dialog), and cleans up when leaving the screen
 */
class ChatViewModel(private val appContext: Context) : ViewModel() {

    enum class InitStatus { NOT_INITIALIZED, INITIALIZING, INITIALIZED, ERROR }

    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages

    /** Header status line ("Model ready: ..."). */
    var status by mutableStateOf("")
        private set

    var initStatus by mutableStateOf(InitStatus.NOT_INITIALIZED)
        private set

    var initError by mutableStateOf("")
        private set

    var ready by mutableStateOf(false)
        private set

    var generating by mutableStateOf(false)
        private set

    var backendName by mutableStateOf(BACKEND_GPU_LABEL)
        private set

    // Sampler parameters (gallery defaults for Gemma 3n/4 E2B: topK=64,
    // topP=0.95, temperature=1.0, maxTokens=4096 = context length).
    var topK by mutableStateOf(DEFAULT_TOPK)
        private set
    var topP by mutableStateOf(DEFAULT_TOPP)
        private set
    var temperature by mutableStateOf(DEFAULT_TEMPERATURE)
        private set
    var maxTokens by mutableStateOf(DEFAULT_MAX_TOKENS)
        private set

    /** Accelerator ("gpu"/"cpu"), gallery ACCELERATOR segmented config. */
    var accelerator by mutableStateOf(ACCELERATOR_GPU)
        private set

    /** Thinking mode (gallery ENABLE_THINKING toggle; Gemma supports it). */
    var thinking by mutableStateOf(false)

    /** Speculative decoding / MTP (gallery ENABLE_SPECULATIVE_DECODING toggle,
     *  default false like gallery). */
    var speculativeDecoding by mutableStateOf(false)

    // System prompt (gallery SystemPromptRepository: persisted custom prompt,
    // empty default for the plain chat task).
    var systemPrompt by mutableStateOf("")
        private set

    private var backend: Backend = Backend.GPU()
    private var modelDirectory: File? = null
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    // ---------------------------------------------------------------- prompt

    fun loadSystemPrompt() {
        systemPrompt = appContext
            .getSharedPreferences(SYSTEM_PROMPT_PREFS, Context.MODE_PRIVATE)
            .getString("system_prompt", "") ?: ""
    }

    /** Persists and applies immediately (gallery applySystemPromptChange). */
    fun applySystemPromptChange(newPrompt: String, updatedMessage: String) {
        systemPrompt = newPrompt
        appContext.getSharedPreferences(SYSTEM_PROMPT_PREFS, Context.MODE_PRIVATE)
            .edit().putString("system_prompt", newPrompt).apply()
        if (initStatus != InitStatus.INITIALIZED) return
        _messages.add(ChatMessage(isUser = false).also { it.text = updatedMessage })
        viewModelScope.launch(Dispatchers.Default) {
            resetConversationLocked(systemInstruction = Contents.of(newPrompt))
        }
    }

    // ------------------------------------------------------- initialization

    /** Chat screen entry point (gallery ChatView LaunchedEffect). */
    fun initializeIfNeeded() {
        if (initStatus == InitStatus.INITIALIZING) return
        if (initStatus == InitStatus.INITIALIZED && conversation != null) return
        val dir = findModelDirectory(appContext) ?: run {
            initStatus = InitStatus.ERROR
            initError = "No model found. Copy a .litertlm model to:\n" +
                File(appContext.getExternalFilesDir(null), MODEL_DIR).absolutePath
            status = initError
            return
        }
        initializeModel(dir, force = false)
    }

    /** Applies all settings from the config dialog; always re-initializes the
     *  engine (gallery: every config has needReinitialization = true). */
    fun updateSettings(
        newTopK: Int,
        newTopP: Float,
        newTemperature: Float,
        newMaxTokens: Int,
        newAccelerator: String,
        newThinking: Boolean,
        newSpeculativeDecoding: Boolean,
    ) {
        if (generating) return
        topK = newTopK
        topP = newTopP
        temperature = newTemperature
        maxTokens = newMaxTokens
        thinking = newThinking
        speculativeDecoding = newSpeculativeDecoding
        accelerator = newAccelerator
        backend = acceleratorToBackend(newAccelerator)
        backendName = newAccelerator.uppercase()
        // Gallery re-initializes the whole engine on any config change.
        modelDirectory?.let { initializeModel(it, force = true) }
    }

    fun initializeModel(modelDirectory: File, force: Boolean) {
        if (generating) return
        // Skip if initialized already (gallery ModelManagerViewModel).
        if (!force && initStatus == InitStatus.INITIALIZED && conversation != null) return
        if (initStatus == InitStatus.INITIALIZING) return
        this.modelDirectory = modelDirectory
        initStatus = InitStatus.INITIALIZING
        viewModelScope.launch(Dispatchers.Default) {
            val error = loadModel(modelDirectory)
            if (error == null && conversation != null) {
                initStatus = InitStatus.INITIALIZED
                ready = true
            } else {
                initStatus = InitStatus.ERROR
                initError = error ?: "Unknown error"
                status = initError
                ready = false
            }
        }
    }

    private fun acceleratorToBackend(label: String): Backend =
        if (label == ACCELERATOR_GPU) Backend.GPU() else Backend.CPU()

    private fun findModelDirectory(context: Context): File? {
        val external = File(context.getExternalFilesDir(null), MODEL_DIR)
        if (external.listFiles { f -> f.extension == MODEL_EXT }?.any() == true) return external
        val internal = File(context.filesDir, MODEL_DIR)
        if (internal.listFiles { f -> f.extension == MODEL_EXT }?.any() == true) return internal
        return null
    }

    /** Clears the chat and creates a fresh conversation with the current parameters. */
    fun resetConversation() {
        if (generating) return
        if (initStatus != InitStatus.INITIALIZED) return
        _messages.clear()
        viewModelScope.launch(Dispatchers.Default) {
            resetConversationLocked(systemInstruction = Contents.of(systemPrompt))
            status = "New conversation ($backendName)"
        }
    }

    @OptIn(ExperimentalApi::class)
    private suspend fun resetConversationLocked(systemInstruction: Contents?) {
        val engine = engine ?: return
        try {
            conversation?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Conversation close failed", e)
        }
        conversation = engine.createConversation(createConversationConfig(systemInstruction))
    }

    @OptIn(ExperimentalApi::class)
    private fun createConversationConfig(systemInstruction: Contents?) = ConversationConfig(
        samplerConfig = SamplerConfig(topK = topK, topP = topP.toDouble(), temperature = temperature.toDouble()),
        systemInstruction = systemInstruction,
    )

    private fun closeEngine() {
        try {
            conversation?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Conversation close failed", e)
        }
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Engine close failed", e)
        }
        engine = null
        conversation = null
    }

    /** Leaving the chat screen (gallery cleans up all task models on back nav). */
    fun cleanup() {
        closeEngine()
        initStatus = InitStatus.NOT_INITIALIZED
        ready = false
        status = ""
    }

    @OptIn(ExperimentalApi::class)
    private fun loadModel(modelDirectory: File): String? {
        val modelFile = modelDirectory.listFiles { file -> file.extension == MODEL_EXT }?.firstOrNull()
            ?: return "No model found in:\n${modelDirectory.absolutePath}"
        return try {
            Log.d(TAG, "loadModel: model=${modelFile.name}, backend=${backendName}")
            // Mirror gallery: disable experimental flags explicitly around engine
            // and conversation creation.
            ExperimentalFlags.enableSpeculativeDecoding = false

            // Check if the model file supports speculative decoding (gallery
            // LlmChatModelHelper: Capabilities(modelPath).hasSpeculativeDecodingSupport()).
            var supportsSpeculativeDecoding = false
            try {
                Capabilities(modelFile.absolutePath).use {
                    supportsSpeculativeDecoding = it.hasSpeculativeDecodingSupport()
                }
            } catch (e: Exception) {
                // Ignore exceptions and assume not supported.
            }
            val speculativeDecodingEnabled = supportsSpeculativeDecoding && speculativeDecoding
            ExperimentalFlags.enableSpeculativeDecoding = speculativeDecodingEnabled
            Log.d(TAG, "loadModel: speculativeDecoding enabled=$speculativeDecodingEnabled")

            var loaded: Engine? = null
            try {
                Log.d(TAG, "loadModel: creating engine with $backendName backend")
                loaded = Engine(engineConfig(modelFile, backend))
                Log.d(TAG, "loadModel: engine initialize ($backendName)")
                loaded.initialize()
                Log.d(TAG, "loadModel: initialize done ($backendName)")
            } catch (e: Exception) {
                Log.w(TAG, "${backendName} backend failed, falling back to CPU", e)
                runCatching { loaded?.close() }
                backend = Backend.CPU()
                backendName = BACKEND_CPU_LABEL
                Log.d(TAG, "loadModel: creating engine with $backendName backend (fallback)")
                loaded = Engine(engineConfig(modelFile, backend))
                loaded.initialize()
                Log.d(TAG, "loadModel: initialize done (CPU fallback)")
            }
            ExperimentalFlags.enableSpeculativeDecoding = false

            engine = loaded
            Log.d(TAG, "loadModel: creating conversation")
            conversation = loaded.createConversation(createConversationConfig(Contents.of(systemPrompt)))
            Log.d(TAG, "loadModel: success")
            status = "Model ready: ${modelFile.name} ($backendName)"
            null
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed", e)
            "Model load failed: ${e.message}"
        }
    }

    private fun engineConfig(modelFile: File, backend: Backend) = EngineConfig(
        modelPath = modelFile.absolutePath,
        backend = backend,
        // Gemma E2B is multimodal; the gallery enables vision+audio backends
        // for it even in chat (llmSupportImage/llmSupportAudio).
        visionBackend = Backend.GPU(),
        audioBackend = Backend.CPU(),
        maxNumTokens = maxTokens,
    )

    // ------------------------------------------------------------ inference

    fun sendMessage(text: String, images: List<Bitmap> = emptyList()) {
        val conversation = conversation ?: return
        _messages.add(ChatMessage(isUser = true).also {
            it.text = text
            it.images = images
        })
        val reply = ChatMessage(isUser = false)
        _messages.add(reply)
        generating = true

        // Token speed + latency, like the gallery's latencyMs bookkeeping.
        val start = System.currentTimeMillis()
        var tokenCount = 0
        var firstTokenAt = 0L

        viewModelScope.launch(Dispatchers.Default) {
            val extraContext =
                if (thinking) mapOf(THINKING_CONTEXT_KEY to "true") else emptyMap()

            // Gallery LlmChatModelHelper.sendToModel: image contents first
            // (PNG bytes), then the text "for the accurate last token".
            val contents = mutableListOf<Content>()
            for (image in images) {
                contents.add(Content.ImageBytes(image.toPngByteArray()))
            }
            if (text.trim().isNotEmpty()) {
                contents.add(Content.Text(text))
            }

            conversation.sendMessageAsync(
                Contents.of(contents),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        // Thinking channel is captured regardless of the text token
                        // (gallery: partialThinking passed even when the text is a
                        // "<ctrl..." control token).
                        val thinkingDelta = message.channels[THOUGHT_CHANNEL]
                        if (!thinkingDelta.isNullOrEmpty()) {
                            reply.thinkingText += thinkingDelta
                        }

                        val delta = message.toString()
                        if (delta.startsWith("<ctrl")) {
                            return
                        }
                        if (firstTokenAt == 0L) {
                            firstTokenAt = System.currentTimeMillis()
                        }
                        reply.text += delta
                        // Estimate tokens from characters: with speculative
                        // decoding (MTP) one callback can carry several tokens,
                        // so counting callbacks would under-report the speed.
                        tokenCount += (delta.length / AVG_CHARS_PER_TOKEN).coerceAtLeast(1)
                        val decodeMs = (System.currentTimeMillis() - firstTokenAt).coerceAtLeast(1)
                        reply.tokenSpeed = tokenCount / (decodeMs / 1000f)
                    }

                    override fun onDone() {
                        reply.latencyMs = (System.currentTimeMillis() - start).toFloat()
                        generating = false
                    }

                    override fun onError(throwable: Throwable) {
                        Log.e(TAG, "Inference failed", throwable)
                        reply.text = "Error: ${throwable.message}"
                        reply.latencyMs = (System.currentTimeMillis() - start).toFloat()
                        generating = false
                    }
                },
                extraContext,
            )
        }
    }

    fun stopResponse() {
        try {
            conversation?.cancelProcess()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Conversation is not alive, cannot cancel process", e)
        }
    }

    /** Re-sends a user prompt (gallery runAgain). */
    fun runAgain(text: String) {
        sendMessage(text)
    }

    override fun onCleared() {
        closeEngine()
    }

    /** Bitmap → PNG bytes (gallery LlmChatModelHelper.toPngByteArray). */
    private fun Bitmap.toPngByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    /** Public close for non-ViewModelProvider owners (MainViewModel holds this instance). */
    fun close() {
        closeEngine()
        initStatus = InitStatus.NOT_INITIALIZED
        ready = false
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MODEL_EXT = "litertlm"
        private const val MODEL_DIR = "model"
        private const val SYSTEM_PROMPT_PREFS = "cryptowl.chat.settings"
        private const val BACKEND_GPU_LABEL = "GPU"
        private const val BACKEND_CPU_LABEL = "CPU"
        private const val ACCELERATOR_GPU = "gpu"

        // Gallery Consts.kt + Gemma E2B model defaults (model_allowlist.json).
        const val DEFAULT_MAX_TOKENS = 4096
        const val DEFAULT_TOPK = 64
        const val DEFAULT_TOPP = 0.95f
        const val DEFAULT_TEMPERATURE = 1.0f

        // Gallery Consts.kt + LlmChatViewModel.
        private const val THOUGHT_CHANNEL = "thought"
        private const val THINKING_CONTEXT_KEY = "enable_thinking"
        private const val AVG_CHARS_PER_TOKEN = 4
    }
}
