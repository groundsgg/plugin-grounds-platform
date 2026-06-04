import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins { id("gg.grounds.paper-conventions") }

// Tests need Paper on both compile + runtime classpaths because Mockito
// loads the target class to generate the proxy.
configurations { testImplementation { extendsFrom(compileOnly.get()) } }

dependencies {
    implementation(project(":common"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

tasks.named<ShadowJar>("shadowJar") {
    // Keep the published asset name `plugin-grounds-platform-<version>-all.jar`
    // (the paper image's GROUNDS_PLATFORM_PLUGIN_VERSION download depends on it).
    archiveBaseName.set("plugin-grounds-platform")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("all")
    // Relocate non-Paper deps so they can't collide with another plugin in the
    // same JVM. Codegen (KSP) makes relocate(kotlin) safe — no moshi reflection.
    relocate("kotlin", "gg.grounds.platform.shaded.kotlin")
    relocate("kotlinx", "gg.grounds.platform.shaded.kotlinx")
    relocate("com.squareup.moshi", "gg.grounds.platform.shaded.moshi")
    relocate("okio", "gg.grounds.platform.shaded.okio")
    mergeServiceFiles()
}
