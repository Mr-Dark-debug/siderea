plugins {
    id("siderea.android.application")
    id("siderea.android.compose")
    id("siderea.android.hilt")
    id("siderea.kotlin.serialization")
    id("siderea.android.licenses")
}

val sidereaVersionName = providers.gradleProperty("siderea.versionName").get()

/** 0.1.0 -> 100, 1.2.3 -> 10203. Monotonic as long as minor and patch stay below 100. */
fun versionCodeFor(name: String): Int {
    val (major, minor, patch) = name.substringBefore('-').split('.').map { it.toInt() }
    return major * 10_000 + minor * 100 + patch
}

android {
    sourceSets {
        // One real-device fixture, shared by every module's unit tests.
        getByName("test").resources.directories.add("../docs/device-reports")
    }
    namespace = "io.github.mrdarkdebug.siderea"

    defaultConfig {
        applicationId = "io.github.mrdarkdebug.siderea"
        versionName = sidereaVersionName
        versionCode = versionCodeFor(sidereaVersionName)
    }

    // Real signing comes from CI secrets (or a local keystore) through these variables. We never
    // commit keystores or passwords. When they are absent the release build type falls back to the
    // debug key so that CI can still produce an installable, clearly-labelled APK.
    val releaseKeystore = System.getenv("SIDEREA_KEYSTORE_FILE")
    val hasReleaseKey = !releaseKeystore.isNullOrBlank() && file(releaseKeystore).exists()
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = System.getenv("SIDEREA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIDEREA_KEY_ALIAS")
                keyPassword = System.getenv("SIDEREA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig =
                if (hasReleaseKey) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:camera"))
    implementation(project(":core:data"))
    implementation(project(":core:capture"))
    implementation(project(":core:export"))
    implementation(project(":core:processing"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.aboutlibraries.compose.m3)

    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
