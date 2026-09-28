package com.typedefai.cryptowl

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import com.typedefai.cryptowl.onboarding.BiometricSetupScreen
import com.typedefai.cryptowl.onboarding.IntroScreen
import com.typedefai.cryptowl.onboarding.MasterPasswordScreen
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private var splashShownAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash; hold the minimum time, then hand over to the Compose
        // splash (logo + wordmark) until MIN_TOTAL_SPLASH_MS.
        val splashScreen = installSplashScreen()
        splashShownAt = System.currentTimeMillis()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splashScreen.setKeepOnScreenCondition {
            System.currentTimeMillis() - splashShownAt < MIN_SYSTEM_SPLASH_MS ||
                viewModel.screen.value == AppScreen.Loading
        }

        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .components { add(SvgDecoder.Factory()) }
                    .build()
            }
            MaterialTheme {
                val screen by viewModel.screen.collectAsState()
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    LaunchedEffect(Unit) {
                        val remaining = MIN_TOTAL_SPLASH_MS - (System.currentTimeMillis() - splashShownAt)
                        if (remaining > 0) delay(remaining)
                        showSplash = false
                    }
                }
                when {
                    showSplash -> SplashScreenLogo()
                    screen == AppScreen.Loading -> Unit
                    screen == AppScreen.Intro -> IntroScreen(
                        onStart = viewModel::startOnboarding,
                        onRestore = viewModel::openRestore,
                    )
                    screen == AppScreen.PasswordSetup -> MasterPasswordScreen(viewModel)
                    screen == AppScreen.BiometricSetup -> BiometricSetupScreen(viewModel)
                    screen == AppScreen.Home -> VaultHomeScreen(viewModel)
                    screen == AppScreen.Unlock -> UnlockScreen(viewModel)
                    screen == AppScreen.Restore -> RestoreScreen(viewModel)
                    screen == AppScreen.Moments -> MomentsScreen(viewModel)
                    screen == AppScreen.Chat ->
                        ChatScreen(
                            viewModel = viewModel.chat,
                            agentName = stringResource(R.string.chat_agent_name),
                            onBack = viewModel::closeChat,
                        )
                }
            }
        }
    }

    private companion object {
        const val MIN_SYSTEM_SPLASH_MS = 1000L
        const val MIN_TOTAL_SPLASH_MS = 2500L
    }
}

/** Splash second stage: owl logo + wordmark (system splash cannot render text). */
@Composable
private fun SplashScreenLogo() {
    // Soft fade/scale-in so the hand-off from the (blank) system splash feels
    // like a continuous white screen.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val splashAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "splashAlpha",
    )
    Surface(color = SplashBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_adaptive_fore),
                contentDescription = null,
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        alpha = splashAlpha
                        scaleX = 0.94f + 0.06f * splashAlpha
                        scaleY = 0.94f + 0.06f * splashAlpha
                    },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CryptOwl",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8C2B1B),
                modifier = Modifier.graphicsLayer { alpha = splashAlpha },
            )
        }
    }
}

private val SplashBackground = Color(0xFFFFFFFF)
