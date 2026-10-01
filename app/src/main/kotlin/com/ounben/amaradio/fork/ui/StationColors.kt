@file:SuppressLint("RestrictedApi")

package com.ounben.amaradio.fork.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Material "content-based" color for the player: a scheme generated from the station's
 * artwork, like Android's own media controls. Returns null (keep the app theme) while the
 * artwork loads, when there is none, or when it has no usable color (grey logos).
 */
@Composable
fun rememberArtworkColorScheme(iconUrl: String?): ColorScheme? {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val seed by produceState<Int?>(initialValue = null, iconUrl) {
        value = iconUrl?.takeIf { it.isNotBlank() && it != "null" }?.let { url ->
            withContext(Dispatchers.Default) { artworkSeedColor(context, url) }
        }
    }
    return remember(seed, dark) { seed?.let { schemeFromSeed(it, dark) } }
}

private suspend fun artworkSeedColor(context: Context, iconUrl: String): Int? = runCatching {
    val request = ImageRequest.Builder(context)
        .data(if (iconUrl.startsWith("file:/")) Uri.parse(iconUrl) else iconUrl)
        .size(SAMPLE_SIZE)
        .allowHardware(false)
        .build()
    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return null
    val bitmap = drawable.toBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
    bitmap.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
    // fallback 0 = "no suitable color"; filter = skip near-grey colors.
    Score.score(QuantizerCelebi.quantize(pixels, 128), 1, 0, true).firstOrNull()?.takeIf { it != 0 }
}.getOrNull()

private fun schemeFromSeed(seed: Int, dark: Boolean): ColorScheme {
    val scheme = SchemeContent(Hct.fromInt(seed), dark, 0.0)
    val roles = MaterialDynamicColors()
    fun c(role: DynamicColor) = Color(role.getArgb(scheme))
    // Every role is given explicitly, so lightColorScheme() serves dark schemes too.
    return lightColorScheme(
        primary = c(roles.primary()),
        onPrimary = c(roles.onPrimary()),
        primaryContainer = c(roles.primaryContainer()),
        onPrimaryContainer = c(roles.onPrimaryContainer()),
        inversePrimary = c(roles.inversePrimary()),
        secondary = c(roles.secondary()),
        onSecondary = c(roles.onSecondary()),
        secondaryContainer = c(roles.secondaryContainer()),
        onSecondaryContainer = c(roles.onSecondaryContainer()),
        tertiary = c(roles.tertiary()),
        onTertiary = c(roles.onTertiary()),
        tertiaryContainer = c(roles.tertiaryContainer()),
        onTertiaryContainer = c(roles.onTertiaryContainer()),
        background = c(roles.background()),
        onBackground = c(roles.onBackground()),
        surface = c(roles.surface()),
        onSurface = c(roles.onSurface()),
        surfaceVariant = c(roles.surfaceVariant()),
        onSurfaceVariant = c(roles.onSurfaceVariant()),
        surfaceTint = c(roles.surfaceTint()),
        inverseSurface = c(roles.inverseSurface()),
        inverseOnSurface = c(roles.inverseOnSurface()),
        error = c(roles.error()),
        onError = c(roles.onError()),
        errorContainer = c(roles.errorContainer()),
        onErrorContainer = c(roles.onErrorContainer()),
        outline = c(roles.outline()),
        outlineVariant = c(roles.outlineVariant()),
        scrim = c(roles.scrim()),
        surfaceBright = c(roles.surfaceBright()),
        surfaceContainer = c(roles.surfaceContainer()),
        surfaceContainerHigh = c(roles.surfaceContainerHigh()),
        surfaceContainerHighest = c(roles.surfaceContainerHighest()),
        surfaceContainerLow = c(roles.surfaceContainerLow()),
        surfaceContainerLowest = c(roles.surfaceContainerLowest()),
        surfaceDim = c(roles.surfaceDim()),
        primaryFixed = c(roles.primaryFixed()),
        primaryFixedDim = c(roles.primaryFixedDim()),
        onPrimaryFixed = c(roles.onPrimaryFixed()),
        onPrimaryFixedVariant = c(roles.onPrimaryFixedVariant()),
        secondaryFixed = c(roles.secondaryFixed()),
        secondaryFixedDim = c(roles.secondaryFixedDim()),
        onSecondaryFixed = c(roles.onSecondaryFixed()),
        onSecondaryFixedVariant = c(roles.onSecondaryFixedVariant()),
        tertiaryFixed = c(roles.tertiaryFixed()),
        tertiaryFixedDim = c(roles.tertiaryFixedDim()),
        onTertiaryFixed = c(roles.onTertiaryFixed()),
        onTertiaryFixedVariant = c(roles.onTertiaryFixedVariant())
    )
}

private const val SAMPLE_SIZE = 112
