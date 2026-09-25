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
        for (name in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val xml = File(app, "src/main/res/xml/$name").readText()
            assertFalse("$name must not include anything", xml.contains("<include"))
        }
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

    @Test fun sdkLevelsMoveTogether() {
        val gradle = File(app, "build.gradle.kts").readText()
        val levels = listOf("compileSdk", "minSdk", "targetSdk").map { name ->
            Regex("""$name\s*=\s*(\d+)""").find(gradle)?.groupValues?.get(1) ?: error("$name not found")
        }
        assertEquals("compileSdk, minSdk and targetSdk must be equal", 1, levels.toSet().size)
    }
}
