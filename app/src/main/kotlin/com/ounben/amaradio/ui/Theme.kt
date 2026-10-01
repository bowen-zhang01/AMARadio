package com.ounben.amaradio.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.ounben.amaradio.Utils
import com.ounben.amaradio.utils.UiScaler

/** AMARadio brand color; seed of the fallback color scheme below. */
val AmaradioAmber = Color(0xFFFF8F00)

const val PREF_DYNAMIC_COLOR = "fork_dynamic_color"
const val PREF_PURE_BLACK = "fork_pure_black"

fun Context.isDynamicColorEnabled(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_DYNAMIC_COLOR, true)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AMARadioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Recompose when any theme-related preference changes, without recreating the activity.
    var preferenceVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_name" || key == PREF_DYNAMIC_COLOR || key == PREF_PURE_BLACK) preferenceVersion++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val prefs = remember(preferenceVersion) { PreferenceManager.getDefaultSharedPreferences(context) }
    val useDarkTheme = when (remember(preferenceVersion) { Utils.getTheme(context) }) {
        "dark" -> true
        "light" -> false
        else -> darkTheme
    }
    val dynamicColor = remember(preferenceVersion) { context.isDynamicColorEnabled() }
    val pureBlack = useDarkTheme && prefs.getBoolean(PREF_PURE_BLACK, false)

    val baseScheme = when {
        dynamicColor && useDarkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        useDarkTheme -> AmberDarkColorScheme
        else -> AmberLightColorScheme
    }
    val colorScheme = if (pureBlack) baseScheme.withPureBlackSurfaces() else baseScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    val scale = UiScaler.getScaleFactor(context)
    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density * scale,
        fontScale = currentDensity.fontScale * scale
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive()
    ) {
        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                content = content
            )
        }
    }
}

private fun ColorScheme.withPureBlackSurfaces(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black
)

// Material 3 "fidelity" scheme generated from AmaradioAmber with material-color-utilities,
// so the fallback palette keeps the brand amber as primary container. Used when dynamic
// color is off or unavailable (Android 11 and older).
internal val AmberLightColorScheme = lightColorScheme(
    primary = Color(0xFF8F4E00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFF8F00),
    onPrimaryContainer = Color(0xFF623400),
    inversePrimary = Color(0xFFFFB77A),
    secondary = Color(0xFF845325),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEBC84),
    onSecondaryContainer = Color(0xFF79491C),
    tertiary = Color(0xFF00658F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF00B7FF),
    onTertiaryContainer = Color(0xFF004563),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF241A11),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF241A11),
    surfaceVariant = Color(0xFFFADDC9),
    onSurfaceVariant = Color(0xFF564334),
    surfaceTint = Color(0xFF8F4E00),
    inverseSurface = Color(0xFF3A2E25),
    inverseOnSurface = Color(0xFFFFEEE2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF897362),
    outlineVariant = Color(0xFFDCC1AE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainer = Color(0xFFFEEADC),
    surfaceContainerHigh = Color(0xFFF9E4D7),
    surfaceContainerHighest = Color(0xFFF3DFD1),
    surfaceContainerLow = Color(0xFFFFF1E8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEAD6C9),
    primaryFixed = Color(0xFFFFDCC2),
    primaryFixedDim = Color(0xFFFFB77A),
    onPrimaryFixed = Color(0xFF2E1500),
    onPrimaryFixedVariant = Color(0xFF6D3A00),
    secondaryFixed = Color(0xFFFFDCC2),
    secondaryFixedDim = Color(0xFFFBB981),
    onSecondaryFixed = Color(0xFF2E1500),
    onSecondaryFixedVariant = Color(0xFF693C0F),
    tertiaryFixed = Color(0xFFC7E7FF),
    tertiaryFixedDim = Color(0xFF85CFFF),
    onTertiaryFixed = Color(0xFF001E2E),
    onTertiaryFixedVariant = Color(0xFF004C6C),
)

internal val AmberDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB87B),
    onPrimary = Color(0xFF4C2700),
    primaryContainer = Color(0xFFFF8F00),
    onPrimaryContainer = Color(0xFF623400),
    inversePrimary = Color(0xFF8F4E00),
    secondary = Color(0xFFFBB981),
    onSecondary = Color(0xFF4C2700),
    secondaryContainer = Color(0xFF693C0F),
    onSecondaryContainer = Color(0xFFE7A872),
    tertiary = Color(0xFF87CFFF),
    onTertiary = Color(0xFF00344C),
    tertiaryContainer = Color(0xFF00B7FF),
    onTertiaryContainer = Color(0xFF004563),
    background = Color(0xFF1B110A),
    onBackground = Color(0xFFF3DFD1),
    surface = Color(0xFF1B110A),
    onSurface = Color(0xFFF3DFD1),
    surfaceVariant = Color(0xFF564334),
    onSurfaceVariant = Color(0xFFDCC1AE),
    surfaceTint = Color(0xFFFFB77A),
    inverseSurface = Color(0xFFF3DFD1),
    inverseOnSurface = Color(0xFF3A2E25),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA48C7A),
    outlineVariant = Color(0xFF564334),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF43372E),
    surfaceContainer = Color(0xFF281E15),
    surfaceContainerHigh = Color(0xFF33281F),
    surfaceContainerHighest = Color(0xFF3E3229),
    surfaceContainerLow = Color(0xFF241A11),
    surfaceContainerLowest = Color(0xFF150C06),
    surfaceDim = Color(0xFF1B110A),
    primaryFixed = Color(0xFFFFDCC2),
    primaryFixedDim = Color(0xFFFFB77A),
    onPrimaryFixed = Color(0xFF2E1500),
    onPrimaryFixedVariant = Color(0xFF6D3A00),
    secondaryFixed = Color(0xFFFFDCC2),
    secondaryFixedDim = Color(0xFFFBB981),
    onSecondaryFixed = Color(0xFF2E1500),
    onSecondaryFixedVariant = Color(0xFF693C0F),
    tertiaryFixed = Color(0xFFC7E7FF),
    tertiaryFixedDim = Color(0xFF85CFFF),
    onTertiaryFixed = Color(0xFF001E2E),
    onTertiaryFixedVariant = Color(0xFF004C6C),
)

