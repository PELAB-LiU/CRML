plugins {
    id("java")
    //id("org.xtext.xtend") version "4.0.0"
}

group = "crml"
version = "1.0-SNAPSHOT"


dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    implementation(project(":language"))
    
    //compileOnly("org.eclipse.xtext:org.eclipse.xtext:2.36.0")
    //implementation("org.eclipse.emf:org.eclipse.emf.codegen.ecore:2.39.0")
    //implementation("org.apache.commons:commons-csv:1.13.0")
    //implementation("org.xerial:sqlite-jdbc:3.49.0.0")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("startServer") {
    group = "syntax check"
    description = "Starts HTTP Server for syntax check"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("crml.server.ServerMain")
}