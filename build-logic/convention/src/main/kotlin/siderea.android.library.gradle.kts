import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.android.library")
    id("siderea.quality")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<LibraryExtension> {
    compileSdk = SiderPlatform.COMPILE_SDK
    defaultConfig {
        minSdk = SiderPlatform.MIN_SDK
    }
    compileOptions {
        sourceCompatibility = SiderPlatform.JAVA_VERSION
        targetCompatibility = SiderPlatform.JAVA_VERSION
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    "testImplementation"(libs.findLibrary("junit").get())
    "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
}
