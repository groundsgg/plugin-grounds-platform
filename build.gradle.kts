import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.4.1"
    id("com.diffplug.spotless") version "8.4.0"
}

group = "gg.grounds"

// release.yml workflow passes the version stripped from the `vX.Y.Z`
// tag via -PversionOverride. Local builds get a SNAPSHOT.
version = (project.findProperty("versionOverride") as? String) ?: "local-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Tests need Paper on both compile + runtime classpaths because
    // Mockito loads the target class to generate the proxy. testCompileOnly
    // alone passes compilation but throws ClassNotFoundException when
    // mock(Server::class) runs.
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Compact JSON parser — sufficient for the small whitelist payloads
    // we get back from forge. Avoids pulling in jackson / gson which
    // would inflate the shadow jar dramatically for two DTOs.
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

kotlin { jvmToolchain(21) }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_21) }

tasks.test { useJUnitPlatform() }

// Substitute the version into plugin.yml at build time so the on-server
// Plugins listing matches what's actually deployed.
tasks.processResources {
    val versionExpansion = mapOf("VERSION" to project.version.toString())
    inputs.properties(versionExpansion)
    filesMatching("plugin.yml") { expand(versionExpansion) }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    // Relocate the few non-Paper deps so they can't collide with another
    // plugin's classpath inside the same JVM.
    relocate("kotlin", "gg.grounds.platform.shaded.kotlin")
    relocate("kotlinx", "gg.grounds.platform.shaded.kotlinx")
    relocate("com.squareup.moshi", "gg.grounds.platform.shaded.moshi")
    relocate("okio", "gg.grounds.platform.shaded.okio")
    mergeServiceFiles()
}

spotless {
    kotlin {
        ktfmt("0.55").kotlinlangStyle()
        target("src/**/*.kt")
    }
    kotlinGradle {
        ktfmt("0.55").kotlinlangStyle()
        target("*.gradle.kts")
    }
}
