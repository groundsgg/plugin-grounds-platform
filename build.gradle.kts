import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("gg.grounds.base-conventions") version "0.6.0"
    // KSP for moshi-kotlin-codegen — generates JsonAdapter classes at
    // compile time so the shaded JAR doesn't have to ship kotlin-reflect.
    // Reflection-based KotlinJsonAdapterFactory + ShadowJar's `relocate(kotlin)`
    // produced corrupted paths inside the .kotlin_builtins resources
    // (`gg/grounds/platform/shaded/kotlin/gg.grounds.platform.shaded.kotlin.…_builtins`)
    // and crashed Moshi's adapter() at plugin onEnable. Codegen sidesteps the
    // whole reflection path.
    id("com.google.devtools.ksp") version "2.3.7"
}

apply(plugin = "gg.grounds.paper-conventions")

// Tests need Paper on both compile + runtime classpaths because Mockito
// loads the target class to generate the proxy.
configurations { testImplementation { extendsFrom(compileOnly.get()) } }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Compact JSON parser — sufficient for the small whitelist payloads
    // we get back from forge. Avoids pulling in jackson / gson which
    // would inflate the shadow jar dramatically for two DTOs.
    implementation("com.squareup.moshi:moshi:1.15.2")
    // moshi-kotlin (reflection) replaced with codegen — see plugins block.
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set(rootProject.name)
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("all")
    // Relocate the few non-Paper deps so they can't collide with another
    // plugin's classpath inside the same JVM.
    relocate("kotlin", "gg.grounds.platform.shaded.kotlin")
    relocate("kotlinx", "gg.grounds.platform.shaded.kotlinx")
    relocate("com.squareup.moshi", "gg.grounds.platform.shaded.moshi")
    relocate("okio", "gg.grounds.platform.shaded.okio")
    mergeServiceFiles()
}
