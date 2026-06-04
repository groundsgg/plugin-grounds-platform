plugins {
    id("gg.grounds.kotlin-conventions")
    // KSP for moshi-kotlin-codegen — generates JsonAdapter classes at compile
    // time so the shaded JARs don't ship kotlin-reflect. Reflection-based
    // KotlinJsonAdapterFactory + ShadowJar's relocate(kotlin) corrupts the
    // .kotlin_builtins resources and crashes Moshi.adapter() at onEnable;
    // codegen sidesteps the reflection path. (Not pre-wired by conventions.)
    id("com.google.devtools.ksp") version "2.3.7"
}

dependencies {
    // api: on the compile + runtime classpath of :paper and :velocity (and so
    // shaded into their jars). The velocity adapter uses CoroutineScope.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Compact JSON parser for the small forge payloads — internal to the
    // whitelist/command HTTP clients in this module.
    implementation("com.squareup.moshi:moshi:1.15.2")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}
