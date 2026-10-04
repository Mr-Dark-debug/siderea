plugins {
    `kotlin-dsl`
}

group = "io.github.mrdarkdebug.siderea.buildlogic"

dependencies {
    implementation(libs.agp.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.aboutlibraries.gradle.plugin)
}
