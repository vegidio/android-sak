package io.vinicius.sak.view

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun Lottie(
    @RawRes animRes: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    iterations: Int = LottieConstants.IterateForever
) {
    val lottieComposition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(animRes)
    )

    val logoAnimationProgress by animateLottieCompositionAsState(
        composition = lottieComposition,
        iterations = iterations,
    )

    LottieAnimation(
        modifier = modifier,
        composition = lottieComposition,
        progress = { logoAnimationProgress },
        contentScale = contentScale,
    )
}