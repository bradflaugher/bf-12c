plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versionCodeFromEnvironment = System.getenv("BF12C_VERSION_CODE") ?: "1"
require(versionCodeFromEnvironment.matches(Regex("[1-9]\\d*"))) {
    "BF12C_VERSION_CODE must be a positive integer"
}

// Release signing is all-or-nothing: set all four variables or none.
val signingEnvironmentVariables = listOf(
    "BF12C_KEYSTORE_PATH",
    "BF12C_STORE_PASSWORD",
    "BF12C_KEY_ALIAS",
    "BF12C_KEY_PASSWORD",
)
val configuredSigningVariables = signingEnvironmentVariables.filter { !System.getenv(it).isNullOrEmpty() }
if (configuredSigningVariables.isNotEmpty() && configuredSigningVariables.size != signingEnvironmentVariables.size) {
    val missing = signingEnvironmentVariables - configuredSigningVariables.toSet()
    throw GradleException("Release signing is partially configured. Missing: ${missing.joinToString()}")
}
val releaseSigningEnabled = configuredSigningVariables.size == signingEnvironmentVariables.size

android {
    namespace = "com.bradflaugher.bf12c"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bradflaugher.bf12c"
        minSdk = 37
        targetSdk = 37
        versionCode = versionCodeFromEnvironment.toInt()
        versionName = System.getenv("BF12C_VERSION_NAME") ?: "dev"
    }

    androidResources {
        localeFilters += listOf("en")
    }

    signingConfigs {
        if (releaseSigningEnabled) {
            create("release") {
                storeFile = file(System.getenv("BF12C_KEYSTORE_PATH"))
                storePassword = System.getenv("BF12C_STORE_PASSWORD")
                keyAlias = System.getenv("BF12C_KEY_ALIAS")
                keyPassword = System.getenv("BF12C_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    testImplementation(libs.junit)
}
