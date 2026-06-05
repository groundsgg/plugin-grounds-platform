import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins { id("gg.grounds.velocity-conventions") }

dependencies {
    implementation(project(":common"))

    testCompileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

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
