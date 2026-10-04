import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.android.application")
    id("siderea.quality")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<ApplicationExtension> {
    compileSdk = SiderPlatform.COMPILE_SDK
    defaultConfig {
        minSdk = SiderPlatform.MIN_SDK
        targetSdk = SiderPlatform.TARGET_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
