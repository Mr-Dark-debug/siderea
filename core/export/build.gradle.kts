plugins {
    id("siderea.android.library")
}

android {
    namespace = "io.github.mrdarkdebug.siderea.core.export"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
