plugins {
    id("java")
}

group = "crml"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":compiler"))
    implementation(project(":language"))

    testImplementation(project(":util"))
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
