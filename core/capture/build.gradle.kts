plugins {
    id("siderea.android.library")
    id("siderea.kotlin.serialization")
}

android {
    namespace = "io.github.mrdarkdebug.siderea.core.capture"
}

dependencies {
    api(project(":core:camera"))
    api(libs.kotlinx.coroutines.android)
}
