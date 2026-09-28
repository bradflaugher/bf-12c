package com.bradflaugher.bf12c

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

import org.junit.Test
import java.io.File

/**
 * Guards the project rules in AGENTS.md that a compiler can't: a pure engine,
 * no permissions, no backups, and CI actions pinned to commit SHAs.
 * Unit tests run with the app module as the working directory.
 */
class InvariantsTest {
    private val app = File(".").canonicalFile.let { if (File(it, "src/main").isDirectory) it else File(it, "app") }
    private val repo = app.parentFile

    @Test fun engineHasNoAndroidImports() {
        val engine = File(app, "src/main/java/com/bradflaugher/bf12c/engine")
        val sources = engine.walk().filter { it.extension == "kt" }.toList()
        assertTrue("engine sources not found in $engine", sources.isNotEmpty())
        for (file in sources) {
            val offending = file.readLines().filter { it.startsWith("import android") || it.startsWith("import androidx") }
            assertTrue("${file.name} imports Android: $offending", offending.isEmpty())
        }
    }

    @Test fun manifestAsksForNothing() {
        val manifest = File(app, "src/main/AndroidManifest.xml").readText()
        assertFalse("no permissions allowed", manifest.contains("<uses-permission"))
        assertTrue("backups must stay off", manifest.contains("android:allowBackup=\"false\""))
    }

    @Test fun backupRulesStayEmpty() {
        val xml = File(app, "src/main/res/xml/data_extraction_rules.xml").readText()
        assertFalse("data_extraction_rules.xml must not include anything", xml.contains("<include"))
        // allowBackup=false stops cloud backup but not device-to-device transfer.
        val transfer = Regex("<device-transfer>(.*?)</device-transfer>", RegexOption.DOT_MATCHES_ALL).find(xml)
        assertTrue(
            "device-to-device transfer must exclude the preferences",
            transfer?.groupValues?.get(1)?.contains("<exclude domain=\"sharedpref\" path=\".\"") == true,
        )
    }

    @Test fun launcherLabel() {
        val strings = File(app, "src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("<string name=\"app_name\">Calculator (bf-12c)</string>"))
    }

    @Test fun workflowActionsArePinnedToShas() {
        val workflows = File(repo, ".github/workflows").listFiles { f -> f.extension == "yml" }.orEmpty()
        assertTrue("no workflows found", workflows.isNotEmpty())
        val uses = Regex("""^\s*(?:-\s*)?uses:\s*(\S+)""")
        for (file in workflows) {
            for (line in file.readLines()) {
                val ref = uses.find(line)?.groupValues?.get(1) ?: continue
                if (ref.startsWith("./")) continue
                assertTrue("${file.name}: $ref is not pinned to a commit SHA", Regex("@[0-9a-f]{40}$").containsMatchIn(ref))
            }
        }
    }

    @Test fun sdkPolicy() {
        val gradle = File(app, "build.gradle.kts").readText()
        fun level(name: String) = Regex("""$name\s*=\s*(\d+)""").find(gradle)?.groupValues?.get(1)?.toInt()
            ?: error("$name not found")
        assertEquals("compileSdk and targetSdk move together", level("compileSdk"), level("targetSdk"))
        assertTrue("minSdk cannot be above targetSdk", level("minSdk") <= level("targetSdk"))

        val checks = File(app, "src/main").walk().filter { it.extension == "kt" }
            .filter { it.readText().contains("SDK_INT") }.map { it.name }.toList()
        assertTrue("no Build.VERSION.SDK_INT checks: $checks", checks.isEmpty())
    }
}
