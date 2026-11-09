package com.typedefai.cryptowl.onboarding

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.typedefai.cryptowl.LanguageButton
import com.typedefai.cryptowl.R
import kotlinx.coroutines.launch

private data class FeatureSlide(
    @RawRes val illustration: Int,
    val description: String,
)

@Composable
private fun featureSlides(): List<FeatureSlide> = listOf(
    FeatureSlide(
        illustration = R.raw.undraw_vault,
        description = stringResource(R.string.intro_slide_encryption_desc),
    ),
    FeatureSlide(
        illustration = R.raw.undraw_security,
        description = stringResource(R.string.intro_slide_tiers_desc),
    ),
    FeatureSlide(
        illustration = R.raw.undraw_fingerprint,
        description = stringResource(R.string.intro_slide_biometric_desc),
    ),
    FeatureSlide(
        illustration = R.raw.undraw_photo_album,
        description = stringResource(R.string.intro_slide_media_desc),
    ),
)

/**
 * Feature intro shown at first launch: a swipeable pager ("gallery") of
 * feature slides with dots, Skip and Get Started.
 */
@Composable
fun IntroScreen(onStart: () -> Unit, onRestore: () -> Unit) {
    val slides = featureSlides()
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = slide.illustration,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = slide.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            LanguageButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 4.dp, end = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(slides.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = {
                    if (pagerState.currentPage < slides.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onStart()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (pagerState.currentPage == slides.lastIndex) {
                        stringResource(R.string.intro_get_started)
                    } else {
                        stringResource(R.string.intro_next)
                    },
                )
            }
            TextButton(onClick = onStart) { Text(stringResource(R.string.intro_skip)) }
            TextButton(onClick = onRestore) { Text(stringResource(R.string.restore_intro)) }
        }
    }
}