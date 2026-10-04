import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("dev.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(files("src"))
}

extensions.configure<KtlintExtension> {
    version.set(libs.findVersion("ktlint").get().requiredVersion)
    android.set(true)
    ignoreFailures.set(false)
}
