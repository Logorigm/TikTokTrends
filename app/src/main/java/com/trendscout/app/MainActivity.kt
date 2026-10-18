package com.trendscout.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.trendscout.app.ui.AppRoot
import com.trendscout.app.ui.theme.TrendScoutTheme
import com.trendscout.core.io.TikTokUrl

class MainActivity : ComponentActivity() {

    /** A TikTok link shared into the app, consumed by the navigation host. */
    private val sharedLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            TrendScoutTheme {
                AppRoot(sharedLink = sharedLink.value, onSharedLinkConsumed = { sharedLink.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            sharedLink.value = TikTokUrl.extract(text) ?: text
        }
    }
}
