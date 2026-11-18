package com.example.mnlists

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mnlists.ui.MnlistsApp
import com.example.mnlists.ui.theme.MnlistsTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity of the app. All navigation happens inside Compose.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MnlistsTheme {
                MnlistsApp()
            }
        }
    }
}
