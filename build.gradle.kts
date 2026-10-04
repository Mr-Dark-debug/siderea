// All plugins are applied through the convention plugins in `build-logic`.
// This file intentionally stays empty of plugin declarations so that plugin
// versions live in exactly one place: gradle/libs.versions.toml.

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs ktlint and detekt on every module."
    val leaves = subprojects.filter { it.childProjects.isEmpty() }
    dependsOn(leaves.map { "${it.path}:ktlintCheck" }, leaves.map { "${it.path}:detekt" })
}
