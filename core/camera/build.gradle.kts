plugins {
    id("siderea.android.library")
    id("siderea.kotlin.serialization")
}

android {
    sourceSets {
        // One real-device fixture, shared by every module's unit tests.
        getByName("test").resources.directories.add("../../docs/device-reports")
    }
    namespace = "io.github.mrdarkdebug.siderea.core.camera"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
