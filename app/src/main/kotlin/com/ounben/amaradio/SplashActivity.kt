package com.ounben.amaradio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.ui.AMARadioTheme
import com.ounben.amaradio.utils.LocaleUtils
import com.ounben.amaradio.utils.UiScaler
import kotlinx.coroutines.delay
import androidx.preference.PreferenceManager

class SplashActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(newBase)
        val lang = sharedPref.getString("settings_language", "system") ?: "system"
        val localeContext = LocaleUtils.wrapContext(newBase, lang)
        super.attachBaseContext(UiScaler.wrapContext(localeContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AMARadioTheme {
                // Same night color as the system splash (ForkSplashTheme), so the hand-off
                // from the launcher animation is seamless.
                val night = colorResource(R.color.fork_brand_night)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(night),
                    contentAlignment = Alignment.Center
                ) {
                    // The Android 12+ system splash draws the icon foreground at 240 dp in the
                    // centre; drawing the same mark at the same spot makes the hand-off invisible.
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_xiangyin_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(240.dp)
                    )
                    var titleVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { titleVisible = true }
                    val titleAlpha by animateFloatAsState(
                        targetValue = if (titleVisible) 1f else 0f,
                        animationSpec = tween(durationMillis = 450),
                        label = "splash-title"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 112.dp)
                            .graphicsLayer { alpha = titleAlpha }
                    ) {
                        Text(
                            text = stringResource(R.string.fork_app_name),
                            color = Color(0xFFFFF1DC),
                            style = MaterialTheme.typography.displaySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.fork_tagline),
                            color = colorResource(R.color.fork_brand_moon).copy(alpha = 0.85f),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = "v${stringResource(R.string.version_name)}",
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                    )
                }

                LaunchedEffect(Unit) {
                    delay(1000)
                    val intent = Intent(this@SplashActivity, ActivityMain::class.java)
                    getIntent()?.extras?.let { intent.putExtras(it) }
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}
