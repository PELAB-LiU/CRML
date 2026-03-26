plugins {
    id("java")
    id("org.xtext.xtend") version "4.0.0"
}

group = "crml"
version = "1.0-SNAPSHOT"

dependencies {
    //compileOnly("org.eclipse.xtext:org.eclipse.xtext:2.36.0")
    implementation("org.eclipse.xtext:org.eclipse.xtext:2.42.0")
    implementation("org.eclipse.emf:org.eclipse.emf.codegen.ecore:2.39.0")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/*tasks.register<JavaExec>("startServer") {
    group = "syntax check"
    description = "Starts HTTP Server for syntax check"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("se.liu.ida.sas.pelab.text2vql.server.ServerMain")
}*/

tasks.named<Test>("test") {
    useJUnitPlatform()
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
/*
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}*/