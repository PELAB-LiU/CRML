plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.0"
}

group = "crml"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":compiler"))
    implementation(project(":language"))

    testImplementation("com.j2html:j2html:1.6.0")

    testImplementation(project(":util"))
    testImplementation(project(":util-test"))
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("llm.generated.dir", rootDir.resolve("LLM/generated").absolutePath)
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.register<JavaExec>("classifyLLMFiles") {
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("crml.experiments.LLMFileClassifier")
    systemProperty("llm.generated.dir", rootDir.resolve("LLM/generated").absolutePath)
    args(rootDir.resolve("LLM/generated").absolutePath)
}
