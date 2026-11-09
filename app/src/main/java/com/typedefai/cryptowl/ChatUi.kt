package com.typedefai.cryptowl

import android.widget.Toast
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typedefai.cryptowl.R
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Gallery chat colors (light theme), from ui/theme/Theme.kt. */
object ChatColors {
    val userBubbleBg = Color(0xFF32628D)
    val agentBubbleBg = Color(0xFFE9EEF6)
    val taskIcon = Color(0xFF3174F1)
    val link = Color(0xFF32628D)
}

/** Port of the gallery's MessageActionButton: pill with icon + label. */
@Composable
private fun MessageActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha: Float = if (enabled) 1.0f else 0.3f
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(enabled = enabled) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp).offset(x = 6.dp).alpha(alpha),
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp).alpha(alpha),
        )
    }
}

/** Port of the gallery's MessageBodyThinking: collapsible thinking panel, auto-expanded while in progress. */
@Composable
private fun MessageBodyThinking(
    thinkingText: String,
    inProgress: Boolean,
    onCopyClicked: (String) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Auto-expand while thinking is in progress
    if (inProgress) {
        isExpanded = true
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.clickable { isExpanded = !isExpanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_show_thinking),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(
                    if (isExpanded) R.string.chat_hide_thinking else R.string.chat_show_thinking,
                ),
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            val lineColor = MaterialTheme.colorScheme.outlineVariant
            Column(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp, start = 8.dp)
                    .drawBehind {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                    .padding(start = 12.dp),
            ) {
                LongPressCopyContainer(copyText = thinkingText, onCopyClicked = onCopyClicked) {
                    MarkdownText(
                        text = thinkingText,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Port of the gallery's LongPressCopyContainer: long-press opens a copy menu. */
@Composable
private fun LongPressCopyContainer(
    copyText: String,
    modifier: Modifier = Modifier,
    onCopyClicked: (String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    },
                )
            },
    ) {
        content()
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.chat_copy),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.chat_copy),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = {
                    showMenu = false
                    onCopyClicked(copyText)
                },
            )
        }
    }
}

/** Port of the gallery's scrollToBottom: scroll to the absolute end (ScrollState.maxValue). */
private const val SCROLL_ANIMATION_DURATION_MS = 300

/** Gallery LlmChatTaskModule passes showImagePicker=true for the LLM chat task
 *  (Gemma E2B is multimodal); the audio recorder panel is not ported. */
private const val SHOW_IMAGE_PICKER = true

private suspend fun scrollToBottom(
    listState: androidx.compose.foundation.ScrollState,
    animate: Boolean = false,
    animationDurationMs: Int = SCROLL_ANIMATION_DURATION_MS,
) {
    if (animate) {
        listState.animateScrollTo(
            listState.maxValue,
            animationSpec = tween(durationMillis = animationDurationMs, easing = FastOutSlowInEasing),
        )
    } else {
        listState.scrollTo(listState.maxValue)
    }
}

/** Port of the gallery's ScrollToBottomButton: filled circle, outlined down arrow, bouncy fade/scale. */
@Composable
private fun ScrollToBottomButton(isAtBottom: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = !isAtBottom,
        enter =
            fadeIn(animationSpec = tween(durationMillis = 300)) +
                scaleIn(
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                ),
        exit = fadeOut(animationSpec = tween(durationMillis = 200)),
    ) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowDownward,
                contentDescription = stringResource(R.string.chat_scroll_to_bottom),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Port of the gallery's MessageBubbleShape: rounded bubble with one hard top corner. */
class MessageBubbleShape(
    private val radius: Dp,
    private val hardCornerAtLeftOrRight: Boolean = false,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radiusPx = with(density) { radius.toPx() }
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    topLeftCornerRadius =
                        if (hardCornerAtLeftOrRight) CornerRadius(0f, 0f)
                        else CornerRadius(radiusPx, radiusPx),
                    topRightCornerRadius =
                        if (hardCornerAtLeftOrRight) CornerRadius(radiusPx, radiusPx)
                        else CornerRadius(0f, 0f),
                    bottomLeftCornerRadius = CornerRadius(radiusPx, radiusPx),
                    bottomRightCornerRadius = CornerRadius(radiusPx, radiusPx),
                ),
            )
        }
        return Outline.Generic(path)
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel, agentName: String, onBack: (() -> Unit)? = null) {
    val listState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val messages = viewModel.messages

    // Stores the heights of the items in the list, indexed by the item index (gallery ChatPanel).
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }

    // Stores the height of the viewport in pixels.
    var viewportHeightPx by remember { mutableIntStateOf(0) }

    // Turn messages into a derived state to trigger updates when the list is updated.
    val currentMessages by rememberUpdatedState(messages)

    // Track whether the user is scrolled to the bottom (gallery ChatPanel logic).
    var isAtBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { !listState.canScrollForward }
            .collectLatest { rawAtBottom ->
                if (!rawAtBottom) {
                    delay(500)
                }
                isAtBottom = rawAtBottom
            }
    }

    // Stores the index of the last user message as a derived state.
    val lastUserMessageIndex by remember(currentMessages) {
        derivedStateOf {
            currentMessages.indexOfLast { it.isUser }
        }
    }

    // Stores the dynamic bottom padding required to push the last user message to the top edge
    // of the view, so the streaming response is always visible without per-token scrolling.
    val density = LocalDensity.current
    val dynamicBottomPadding by remember {
        derivedStateOf {
            if (lastUserMessageIndex == -1 || viewportHeightPx == 0) return@derivedStateOf 0.dp

            var bottomContentHeight = 0
            for (i in lastUserMessageIndex until currentMessages.size) {
                bottomContentHeight += itemHeights[i] ?: 0
            }

            // The padding required to push the last user message to the top.
            val paddingPx = maxOf(0, viewportHeightPx - bottomContentHeight)
            with(density) { paddingPx.toDp() }
        }
    }

    // Scroll to the bottom when the last user message index changes (i.e. when a new user prompt
    // is sent), like the gallery: await a frame for layout, then animate to the bottom.
    LaunchedEffect(lastUserMessageIndex) {
        if (lastUserMessageIndex != -1) {
            val unused = awaitFrame()
            scrollToBottom(
                listState = listState,
                animate = true,
                animationDurationMs = SCROLL_ANIMATION_DURATION_MS * 2,
            )
        }
    }

    var showParamsDialog by remember { mutableStateOf(false) }
    val systemPromptUpdatedMessage = stringResource(R.string.chat_system_prompt_updated)
    val copyHandler = rememberCopyHandler()

    // Picked images for the next message (gallery MessageInputText pickedImages).
    val context = LocalContext.current
    val pickedImages = remember { mutableStateListOf<Bitmap>() }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                handleImagesSelected(context = context, uris = listOf(uri)) { bitmaps ->
                    pickedImages.addAll(bitmaps)
                }
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraImageUri
        if (success && uri != null) {
            scope.launch(Dispatchers.IO) {
                handleImagesSelected(context = context, uris = listOf(uri)) { bitmaps ->
                    pickedImages.addAll(bitmaps)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.chat_back),
                    )
                }
            }
            Text(
                text = viewModel.status,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )
            IconButton(
                onClick = { viewModel.resetConversation() },
                enabled = viewModel.ready && !viewModel.generating,
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.chat_new_conversation))
            }
            IconButton(
                onClick = { showParamsDialog = true },
                enabled = viewModel.ready && !viewModel.generating,
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.chat_parameters))
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewportHeightPx = it.height },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(listState),
                verticalArrangement = Arrangement.Top,
            ) {
                messages.forEachIndexed { index, message ->
                    ChatMessageItem(
                        message = message,
                        agentName = agentName,
                        generating = viewModel.generating && index == messages.size - 1,
                        onRunAgain = { viewModel.runAgain(it) },
                        onCopy = copyHandler,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                if (itemHeights[index] != size.height) {
                                    itemHeights[index] = size.height
                                }
                            },
                    )
                }

                // The spacer at the bottom to push the content up so that the last user message
                // will be positioned at the top edge of the view when the list is scrolled to the
                // bottom (gallery dynamicBottomPadding).
                Spacer(modifier = Modifier.height(dynamicBottomPadding).fillMaxWidth())
            }

            // "Scroll to bottom" button, only shown when the list is not at the bottom
            // (gallery ChatPanel: centered horizontally at the bottom).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScrollToBottomButton(isAtBottom = isAtBottom) {
                    scope.launch { scrollToBottom(listState, animate = true) }
                }
            }
        }

        MessageInputBar(
            ready = viewModel.ready,
            generating = viewModel.generating,
            pickedImages = pickedImages,
            onRemoveImage = { pickedImages.removeAt(it) },
            showImagePicker = SHOW_IMAGE_PICKER,
            onPickImageFromAlbum = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onTakePicture = {
                val photoFile = File(context.cacheDir, "images/${System.currentTimeMillis()}.jpg").apply {
                    parentFile?.mkdirs()
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                cameraImageUri = uri
                takePicture.launch(uri)
            },
            onSend = { text ->
                viewModel.sendMessage(text, images = pickedImages.toList())
                pickedImages.clear()
            },
            onStop = { viewModel.stopResponse() },
        )
    }

    // Model init on screen open (gallery ChatView LaunchedEffect).
    LaunchedEffect(Unit) {
        viewModel.loadSystemPrompt()
        viewModel.initializeIfNeeded()
    }

    // Full-screen initializing overlay (gallery ChatPanel loading screen).
    if (viewModel.initStatus == ChatViewModel.InitStatus.INITIALIZING) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.chat_initializing_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.chat_initializing_content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    // Error dialog when initialization failed (gallery ChatPanel ErrorDialog).
    if (viewModel.initStatus == ChatViewModel.InitStatus.ERROR) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.chat_error_title)) },
            text = { Text(viewModel.initError) },
            confirmButton = {
                TextButton(onClick = viewModel::cleanup) {
                    Text(stringResource(R.string.chat_back))
                }
            },
        )
    }

    // Block system back while initializing or generating (gallery BackHandler).
    BackHandler {
        if (viewModel.initStatus != ChatViewModel.InitStatus.INITIALIZING && !viewModel.generating) {
            viewModel.cleanup()
            onBack?.invoke()
        }
    }

    if (showParamsDialog) {
        ParametersDialog(
            topK = viewModel.topK,
            topP = viewModel.topP,
            temperature = viewModel.temperature,
            maxTokens = viewModel.maxTokens,
            thinking = viewModel.thinking,
            speculativeDecoding = viewModel.speculativeDecoding,
            accelerator = viewModel.accelerator,
            systemPrompt = viewModel.systemPrompt,
            onDismiss = { showParamsDialog = false },
            onApply = { newTopK, newTopP, newTemperature, newMaxTokens, newAccelerator, newThinking, newSpecDec, newSystemPrompt ->
                showParamsDialog = false
                viewModel.updateSettings(newTopK, newTopP, newTemperature, newMaxTokens, newAccelerator, newThinking, newSpecDec)
                if (newSystemPrompt != viewModel.systemPrompt) {
                    viewModel.applySystemPromptChange(
                        newSystemPrompt,
                        systemPromptUpdatedMessage,
                    )
                }
            },
        )
    }
}

private enum class ValueType { INT, FLOAT }

/** Gallery ConfigDialog.getTextFieldDisplayValue. */
private fun getTextFieldDisplayValue(valueType: ValueType, value: Float): String {
    return try {
        when (valueType) {
            ValueType.FLOAT -> "%.2f".format(value)
            ValueType.INT -> "${value.toInt()}"
        }
    } catch (e: Exception) {
        ""
    }
}

/**
 * Gallery ConfigDialog.NumberSliderRow: a slider with an associated editable
 * numeric text field displaying the current value.
 */
@Composable
private fun NumberSliderRow(
    label: String,
    sliderMin: Float,
    sliderMax: Float,
    value: Float,
    valueType: ValueType,
    onValueChange: (Float) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(modifier = Modifier.fillMaxWidth()) {
        // Field label with range, like gallery: "label (min-max)".
        val minStr = getTextFieldDisplayValue(valueType, sliderMin)
        val maxStr = getTextFieldDisplayValue(valueType, sliderMax)
        Text("$label ($minStr-$maxStr)", style = MaterialTheme.typography.titleSmall)

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            var isFocused by remember { mutableStateOf(false) }

            // The displaying value for the Text field. It allows holding invalid
            // values temporarily while the user is still editing the text.
            var textFieldDisplayValue by remember(value) {
                mutableStateOf(getTextFieldDisplayValue(valueType, value))
            }

            // Number slider.
            Slider(
                modifier = Modifier
                    .height(24.dp)
                    .weight(1f)
                    .padding(end = 8.dp),
                value = value,
                valueRange = sliderMin..sliderMax,
                onValueChange = { newValue ->
                    onValueChange(newValue)
                    textFieldDisplayValue = getTextFieldDisplayValue(valueType, newValue)
                },
            )

            Spacer(modifier = Modifier.width(8.dp))

            // A smaller text field.
            BasicTextField(
                value = textFieldDisplayValue,
                modifier = Modifier
                    .width(80.dp)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        // When leaving focus, display the internal value so that
                        // any invalid value is cleared.
                        if (!isFocused) {
                            textFieldDisplayValue = getTextFieldDisplayValue(valueType, value)
                        }
                    },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                singleLine = true,
                onValueChange = {
                    // Always update the display value to reflect the update on the UI.
                    textFieldDisplayValue = it

                    // Only if the new value could be converted to a float, then
                    // update the internal value, bounded by the slider range.
                    it.toFloatOrNull()?.let { floatValue ->
                        onValueChange(minOf(maxOf(floatValue, sliderMin), sliderMax))
                    }
                },
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            ) { innerTextField ->
                Box(
                    modifier = Modifier.border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color =
                            if (isFocused) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(4.dp),
                    )
                ) {
                    Box(modifier = Modifier.padding(8.dp)) { innerTextField() }
                }
            }
        }
    }
}

/** Copies text to the clipboard with a toast (gallery copyToClipboard). */
@Composable
private fun rememberCopyHandler(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val copied = stringResource(R.string.chat_copied)
    val context = LocalContext.current
    return { text ->
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------------------
// Image picking helpers — verbatim ports from the gallery
// (common/Utils.kt + MessageInputText.kt).
// ---------------------------------------------------------------------------

private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
    // First, decode with inJustDecodeBounds=true to check dimensions
    val options =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            (if (uri.scheme == null || uri.scheme == "file") {
                FileInputStream(uri.path ?: "")
            } else {
                context.contentResolver.openInputStream(uri)
            })
                ?.use { BitmapFactory.decodeStream(it, null, this) }

            // Calculate inSampleSize
            inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            inJustDecodeBounds = false
        }

    return (if (uri.scheme == null || uri.scheme == "file") {
        FileInputStream(uri.path ?: "")
    } else {
        context.contentResolver.openInputStream(uri)
    })
        ?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    // Raw height and width of image
    val height: Int = options.outHeight
    val width: Int = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        // Calculate the ratio of height and width to the requested height and width
        val heightRatio = (height.toFloat() / reqHeight.toFloat()).roundToInt()
        val widthRatio = (width.toFloat() / reqWidth.toFloat()).roundToInt()

        // Choose the largest ratio as inSampleSize value to ensure
        // that both dimensions are smaller than or equal to the requested dimensions.
        inSampleSize = max(heightRatio, widthRatio)
    }

    return inSampleSize
}

private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1.0f, 1.0f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1.0f, -1.0f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.preScale(-1.0f, 1.0f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.preScale(-1.0f, 1.0f)
        }
        ExifInterface.ORIENTATION_NORMAL -> return bitmap
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Gallery MessageInputText.handleImagesSelected: EXIF-aware decode at ≤1024×1024. */
private fun handleImagesSelected(context: Context, uris: List<Uri>, onImagesSelected: (List<Bitmap>) -> Unit) {
    val images: MutableList<Bitmap> = mutableListOf()
    for (uri in uris) {
        val bitmap: Bitmap? =
            try {
                val inputStream =
                    if (uri.scheme == null || uri.scheme == "file") {
                        FileInputStream(uri.path ?: "")
                    } else {
                        context.contentResolver.openInputStream(uri)
                    }
                if (inputStream != null) {
                    // Read the EXIF metadata from the picture and rotate it correctly.
                    val exif = ExifInterface(inputStream)
                    val orientation =
                        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    // You MUST close the first input stream before opening another one on the same URI.
                    inputStream.close()

                    decodeSampledBitmapFromUri(context, uri, 1024, 1024)?.let { originalBitmap ->
                        rotateBitmap(bitmap = originalBitmap, orientation = orientation)
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        if (bitmap != null) {
            images.add(bitmap)
        }
    }
    if (images.isNotEmpty()) {
        onImagesSelected(images)
    }
}

@Composable
private fun ParametersDialog(
    topK: Int,
    topP: Float,
    temperature: Float,
    maxTokens: Int,
    thinking: Boolean,
    speculativeDecoding: Boolean,
    accelerator: String,
    systemPrompt: String,
    onDismiss: () -> Unit,
    onApply: (Int, Float, Float, Int, String, Boolean, Boolean, String) -> Unit,
) {
    var curTopK by remember { mutableStateOf(topK) }
    var curTopP by remember { mutableStateOf(topP) }
    var curTemperature by remember { mutableStateOf(temperature) }
    var curMaxTokens by remember { mutableStateOf(maxTokens) }
    var curThinking by remember { mutableStateOf(thinking) }
    var curSpeculativeDecoding by remember { mutableStateOf(speculativeDecoding) }
    var curAccelerator by remember { mutableStateOf(accelerator) }
    var curSystemPrompt by remember { mutableStateOf(systemPrompt) }
    var selectedTab by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_parameters)) },
        text = {
            Column {
                // Tabs: model config / system prompt (gallery ConfigDialog).
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.chat_parameters)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.chat_system_prompt)) },
                    )
                }
                if (selectedTab == 0) {
                    Column {
                        // Gallery NumberSliderRow: label with range, slider plus
                        // an editable numeric text field showing the value.
                        NumberSliderRow(
                            label = stringResource(R.string.chat_max_tokens),
                            sliderMin = 2000f,
                            sliderMax = ChatViewModel.DEFAULT_MAX_TOKENS.toFloat(),
                            value = curMaxTokens.toFloat(),
                            valueType = ValueType.INT,
                            onValueChange = { curMaxTokens = it.toInt() },
                        )
                        NumberSliderRow(
                            label = stringResource(R.string.chat_top_k),
                            sliderMin = 1f,
                            sliderMax = 100f,
                            value = curTopK.toFloat(),
                            valueType = ValueType.INT,
                            onValueChange = { curTopK = it.toInt() },
                        )
                        NumberSliderRow(
                            label = stringResource(R.string.chat_top_p),
                            sliderMin = 0f,
                            sliderMax = 1f,
                            value = curTopP,
                            valueType = ValueType.FLOAT,
                            onValueChange = { curTopP = it },
                        )
                        NumberSliderRow(
                            label = stringResource(R.string.chat_temperature),
                            sliderMin = 0f,
                            sliderMax = 2f,
                            value = curTemperature,
                            valueType = ValueType.FLOAT,
                            onValueChange = { curTemperature = it },
                        )
                        // Accelerator: gallery SegmentedButtonConfig (gpu/cpu).
                        Text(stringResource(R.string.chat_accelerator), style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            listOf("gpu", "cpu").forEachIndexed { index, label ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                    selected = curAccelerator == label,
                                    onClick = { curAccelerator = label },
                                ) {
                                    Text(label.uppercase())
                                }
                            }
                        }
                        // Thinking: gallery BooleanSwitchConfig (Gemma 4 supports it).
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.chat_enable_thinking), style = MaterialTheme.typography.titleSmall)
                            Switch(checked = curThinking, onCheckedChange = { curThinking = it })
                        }
                        // Speculative decoding / MTP: gallery BooleanSwitchConfig.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.chat_enable_speculative), style = MaterialTheme.typography.titleSmall)
                            Switch(checked = curSpeculativeDecoding, onCheckedChange = { curSpeculativeDecoding = it })
                        }
                    }
                } else {
                    // System prompt editor (gallery ConfigDialog system prompt tab).
                    OutlinedTextField(
                        value = curSystemPrompt,
                        onValueChange = { curSystemPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 280.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text(stringResource(R.string.chat_system_prompt_placeholder)) },
                    )
                    OutlinedButton(
                        onClick = { curSystemPrompt = "" },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.chat_restore_default))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    curTopK, curTopP, curTemperature, curMaxTokens,
                    curAccelerator, curThinking, curSpeculativeDecoding, curSystemPrompt,
                )
            }) {
                Text(stringResource(R.string.chat_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        },
    )
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    agentName: String,
    generating: Boolean,
    onRunAgain: (String) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUser = message.isUser
    Column(
        modifier = modifier.padding(
            start = if (isUser) 64.dp else 16.dp,
            end = 12.dp,
            top = 6.dp,
            bottom = 6.dp,
        ),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = if (isUser) stringResource(R.string.chat_you) else agentName,
            style = MaterialTheme.typography.titleSmall,
        )

        if (isUser) {
            LongPressCopyContainer(copyText = message.text) {
                Box(
                    modifier = Modifier
                        .clip(MessageBubbleShape(radius = 24.dp, hardCornerAtLeftOrRight = false))
                        .background(ChatColors.userBubbleBg),
                ) {
                    Column {
                        // Attached images (gallery MessageBodyImage).
                        for (image in message.images) {
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        MarkdownText(
                            text = message.text,
                            textColor = Color.White,
                            linkColor = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            // Run again button (gallery MessageActionButton on user messages).
            MessageActionButton(
                label = stringResource(R.string.chat_run_again),
                icon = Icons.Rounded.Refresh,
                enabled = !generating,
                onClick = { onRunAgain(message.text) },
            )
        } else {
            // Agent text: no bubble, like the gallery's agent response rendering.
            LongPressCopyContainer(copyText = message.text) {
                Column {
                    // Thinking panel (gallery MessageBodyThinking above the response).
                    if (message.thinkingText.isNotEmpty()) {
                        MessageBodyThinking(
                            thinkingText = message.thinkingText,
                            inProgress = generating,
                            onCopyClicked = { onCopy(it) },
                        )
                    }
                    MarkdownText(
                        text = message.text,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            // Latency + copy row (gallery ChatPanel agent message actions).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LatencyText(message = message)
                if (!generating && message.text.isNotEmpty()) {
                    IconButton(
                        onClick = { onCopy(message.text) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.chat_copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Port of the gallery's LatencyText. */
@Composable
private fun LatencyText(message: ChatMessage) {
    if (message.latencyMs >= 0) {
        Text(
            "${"%.1f".format(message.tokenSpeed)} t/s · ${message.latencyMs.humanReadableDuration()}",
            modifier = Modifier.alpha(0.5f),
            style = MaterialTheme.typography.labelSmall,
        )
    } else if (message.tokenSpeed > 0f) {
        Text(
            "${"%.1f".format(message.tokenSpeed)} t/s",
            modifier = Modifier.alpha(0.5f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Port of the gallery's Float.humanReadableDuration. */
private fun Float.humanReadableDuration(): String {
    val milliseconds = this
    if (milliseconds < 1000) {
        return "$milliseconds ms"
    }
    val seconds = milliseconds / 1000f
    if (seconds < 60) {
        return "%.1f s".format(seconds)
    }
    val minutes = seconds / 60f
    return "%.1f min".format(minutes)
}

@Composable
private fun MessageInputBar(
    ready: Boolean,
    generating: Boolean,
    pickedImages: List<Bitmap>,
    onRemoveImage: (Int) -> Unit,
    showImagePicker: Boolean,
    onPickImageFromAlbum: () -> Unit,
    onTakePicture: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var showAddContentMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(vertical = 8.dp)
            .heightIn(min = 76.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        // Picked images preview (gallery MessageInputText pickedImages row).
        if (pickedImages.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pickedImages.forEachIndexed { index, bitmap ->
                    Box {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                .clickable { onRemoveImage(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.2.sp),
                placeholder = { Text(stringResource(R.string.chat_type_prompt)) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .offset(y = (-8).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // A plus button to show a popup menu to add stuff to the chat
            // (gallery MessageInputText add-content button).
            Box {
                val enableAddButton = !generating
                OutlinedIconButton(
                    enabled = enableAddButton,
                    onClick = { showAddContentMenu = true },
                    border = IconButtonDefaults.outlinedIconButtonBorder(true),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.chat_add_content),
                        modifier = Modifier.size(24.dp),
                    )
                }

                DropdownMenu(
                    expanded = showAddContentMenu,
                    onDismissRequest = { showAddContentMenu = false },
                ) {
                    if (showImagePicker) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_take_picture)) },
                            leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                            onClick = {
                                showAddContentMenu = false
                                onTakePicture()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_pick_image)) },
                            leadingIcon = { Icon(Icons.Outlined.AddAPhoto, null) },
                            onClick = {
                                showAddContentMenu = false
                                onPickImageFromAlbum()
                            },
                        )
                    }
                }
            }

            if (generating) {
                IconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.Stop,
                        contentDescription = stringResource(R.string.chat_stop),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        onSend(input.trim())
                        input = ""
                    },
                    enabled = ready && (input.isNotBlank() || pickedImages.isNotEmpty()),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = ChatColors.taskIcon,
                        disabledContainerColor = ChatColors.taskIcon.copy(alpha = 0.3f),
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        modifier = Modifier.offset(x = 2.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
