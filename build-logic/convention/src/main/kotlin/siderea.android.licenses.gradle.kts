// Generates res/raw/aboutlibraries.json from the resolved dependency graph at build time,
// so the in-app licenses list can never drift from what is actually shipped.
plugins {
    id("com.mikepenz.aboutlibraries.plugin.android")
}
