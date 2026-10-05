package com.bradflaugher.bf12c

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI
import java.net.URLDecoder

/** The strings behind Share app and Send feedback, which no screen test would catch going stale. */
class LinksTest {
    private val app = File(".").canonicalFile.let { if (File(it, "src/main").isDirectory) it else File(it, "app") }

    @Test fun playLinkIsTheReleaseApplicationId() {
        val gradle = File(app, "build.gradle.kts").readText()
        val id = Regex("""applicationId\s*=\s*"([^"]+)"""").find(gradle)!!.groupValues[1]
        assertEquals(id, Links.APPLICATION_ID)
        assertEquals("https://play.google.com/store/apps/details?id=$id", Links.PLAY_URL)
        assertTrue("the shared text carries the link", Links.SHARE_TEXT.endsWith(Links.PLAY_URL))
        assertEquals("market://details?id=$id", Links.MARKET_URL)
    }

    @Test fun feedbackOpensANewIssueWithTheDetailsFilledIn() {
        val url = Links.feedbackUrl("2026.10.05.42", "17", "Google Pixel 8")
        assertTrue(url.startsWith("https://github.com/bradflaugher/bf-12c/issues/new?body="))
        // A valid URI: spaces, newlines and the middle dot are all escaped.
        val uri = URI(url)
        assertFalse(url.contains(' '))
        assertFalse(url.contains('\n'))
        val body = URLDecoder.decode(uri.rawQuery.removePrefix("body="), Charsets.UTF_8)
        assertTrue(body.contains("bf-12c 2026.10.05.42 · Android 17 · Google Pixel 8"))
        assertTrue(body.contains("What happened"))
    }

    @Test fun everyLinkIsHttps() {
        for (url in listOf(Links.PLAY_URL, Links.ISSUES_URL, Links.PRIVACY_POLICY_URL)) {
            assertEquals("https", URI(url).scheme)
        }
    }
}
