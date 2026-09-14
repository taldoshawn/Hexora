/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app

import android.app.Application
import dev.hexora.app.di.AppContainer

class HexoraApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }
}
