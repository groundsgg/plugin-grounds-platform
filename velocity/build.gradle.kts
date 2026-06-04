import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins { id("gg.grounds.velocity-conventions") }

dependencies { implementation(project(":common")) }

tasks.named<ShadowJar>("shadowJar") {
    // Published as `plugin-grounds-platform-velocity-<version>-all.jar` — the
    // velocity image downloads this (distinct from the paper artifact).
    archiveBaseName.set("plugin-grounds-platform-velocity")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("all")
    relocate("kotlin", "gg.grounds.platform.shaded.kotlin")
    relocate("kotlinx", "gg.grounds.platform.shaded.kotlinx")
    relocate("com.squareup.moshi", "gg.grounds.platform.shaded.moshi")
    relocate("okio", "gg.grounds.platform.shaded.okio")
    mergeServiceFiles()
}
