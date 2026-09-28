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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import com.typedefai.cryptowl.ui.theme.BrandOxblood
import com.typedefai.cryptowl.ui.theme.CryptowlTheme
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private var splashShownAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash; hold the minimum time, then hand over to the Compose
        // splash (logo + wordmark) until MIN_TOTAL_SPLASH_MS.
        installSplashScreen()
        splashShownAt = System.currentTimeMillis()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .components { add(SvgDecoder.Factory()) }
                    .build()
            }
            CryptowlTheme {
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    LaunchedEffect(Unit) {
                        val remaining = MIN_TOTAL_SPLASH_MS - (System.currentTimeMillis() - splashShownAt)
                        if (remaining > 0) delay(remaining)
                        showSplash = false
                    }
                }
                if (showSplash) {
                    SplashScreenLogo()
                } else {
                    CryptowlApp(viewModel)
                }
            }
        }
    }

    private companion object {
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
                color = BrandOxblood,
                modifier = Modifier.graphicsLayer { alpha = splashAlpha },
            )
        }
    }
}

private val SplashBackground = Color(0xFFFFFFFF)
