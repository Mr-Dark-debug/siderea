plugins {
    id("siderea.android.library")
    id("siderea.kotlin.serialization")
}

android {
    namespace = "io.github.mrdarkdebug.siderea.core.camera"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
