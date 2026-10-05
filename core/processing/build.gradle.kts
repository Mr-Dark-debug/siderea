plugins {
    id("siderea.android.library")
}

android {
    namespace = "io.github.mrdarkdebug.siderea.core.processing"
}

dependencies {
    api(project(":core:export"))
}
