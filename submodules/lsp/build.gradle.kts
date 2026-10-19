plugins {
    id("application")
    id("com.gradleup.shadow") version "9.2.0"
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":language"))
    implementation(project(":model"))

    // LSP4J 1.0 needs Java 11; see the release setting below.
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0")

    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The root build compiles every project for Java 8. The language server alone
// runs on Java 11+, because LSP4J 1.0 does. This runs after the root's setting.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

application {
    mainClass.set("crml.lsp.Main")
}

tasks.shadowJar {
    archiveBaseName.set("crml-language-server")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
    // LifecycleTest starts the shadow jar as a separate process.
    dependsOn(tasks.shadowJar)
    systemProperty("crml.lsp.jar", tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
    testLogging {
        events("passed", "skipped", "failed")
    }
}
