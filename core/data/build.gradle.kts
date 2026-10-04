plugins {
    id("siderea.android.library")
    id("siderea.kotlin.serialization")
}

android {
    namespace = "io.github.mrdarkdebug.siderea.core.data"
}

dependencies {
    api(libs.androidx.datastore.preferences)
    api(libs.kotlinx.coroutines.android)
}
