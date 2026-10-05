package com.bradflaugher.bf12c

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import java.net.URLEncoder

/**
 * The few ways bf-12c reaches outside itself. It has no network access: each one
 * hands an intent to another app (the share sheet, the browser) and that app does
 * the rest. Plain Kotlin up top so the unit tests can check the strings.
 */
object Links {
    /** The release application ID, not the debug build's, so a shared link always works. */
    const val APPLICATION_ID = "com.bradflaugher.bf12c"
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=$APPLICATION_ID"
    const val ISSUES_URL = "https://github.com/bradflaugher/bf-12c/issues/new"
    const val PRIVACY_POLICY_URL = "https://bradflaugher.com/privacy/bf-12c/"

    const val SHARE_TEXT = "bf-12c: an RPN financial calculator with the classic 12c keyboard and 34 digits. Free, no ads. $PLAY_URL"

    /** A new GitHub issue with the details a bug report needs already filled in. */
    fun feedbackUrl(appVersion: String, android: String, device: String): String {
        val body = """
            |**What happened, or what would you like?**
            |
            |
            |**Keys pressed (if it's a calculation)**
            |
            |
            |---
            |bf-12c $appVersion · Android $android · $device
        """.trimMargin()
        return "$ISSUES_URL?body=" + URLEncoder.encode(body, Charsets.UTF_8).replace("+", "%20")
    }
}

/** The installed version name, as the About line and bug reports show it. */
fun Context.appVersion(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

/** Opens the system share sheet with a link to bf-12c on Google Play. False if nothing can share. */
fun Context.shareApp(): Boolean {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "bf-12c RPN Calculator")
        .putExtra(Intent.EXTRA_TEXT, Links.SHARE_TEXT)
    return start(Intent.createChooser(send, "Share bf-12c"))
}

/** Opens a new GitHub issue in the browser. False if there is no browser (some TVs and kiosks). */
fun Context.sendFeedback(): Boolean =
    openWeb(Links.feedbackUrl(appVersion(), Build.VERSION.RELEASE, "${Build.MANUFACTURER} ${Build.MODEL}"))

fun Context.openWeb(url: String): Boolean = start(Intent(Intent.ACTION_VIEW, url.toUri()))

private fun Context.start(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
