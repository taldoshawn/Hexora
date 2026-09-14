/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.hexora.app.ui.HexoraApp
import dev.hexora.app.ui.theme.HexoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HexoraApplication).container
        setContent {
            HexoraTheme {
                HexoraApp(container = container)
            }
        }
    }
}
