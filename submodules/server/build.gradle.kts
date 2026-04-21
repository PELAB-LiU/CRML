plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.0"
    //id("org.xtext.xtend") version "4.0.0"
}

group = "crml"
version = "1.0-SNAPSHOT"


dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    implementation(project(":language"))
    
    testImplementation(project(":util"))
    testImplementation(project(":util-test"))

    //compileOnly("org.eclipse.xtext:org.eclipse.xtext:2.36.0")
    //implementation("org.eclipse.emf:org.eclipse.emf.codegen.ecore:2.39.0")
    //implementation("org.apache.commons:commons-csv:1.13.0")
    //implementation("org.xerial:sqlite-jdbc:3.49.0.0")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// There is some concern that gradle build messages might interfere with the RPC, but it seems to work just fine.
tasks.register<JavaExec>("startConsoleServer") {
    group = "mcp"
    description = "Starts the MCP server for CRML (stdio transport)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("crml.server.run.ConsoleServerMain")
    standardInput = System.`in`
}

tasks.register<JavaExec>("startHttpServer") {
    group = "mcp"
    description = "Starts the MCP server for CRML (HTTP transport, default port 63029)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("crml.server.run.HttpServerMain")
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "crml.server.run.HttpServerMain"
    }
    archiveFileName.set("crml-mcp-server.jar")
}
tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
    ignoreFailures = true
}
