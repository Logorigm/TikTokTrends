package com.trendscout.app

import android.content.Context

/** Reads version info from the package (BuildConfig generation is disabled by default in AGP 8). */
object BuildConfigShim {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
}
