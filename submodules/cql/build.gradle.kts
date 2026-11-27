// The CQL frontend: syntax tree, parser, IR, semantic analysis, model scope, API and
// backend SPI. See util-test/QUERY_LANGUAGE_ARCHITECTURE.md and
// util-test/QUERY_LANGUAGE_FRONTEND_PLAN.md.
//
// Java 8, and EMF is the only runtime dependency (architecture Q5): the compiler runs
// on a Java 8 JRE and may use the frontend; backends with other needs live in their
// own projects.
plugins {
    id("java-library")
    id("java-test-fixtures")
    id("org.xtext.builder") version "4.0.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

// antlr-runtime is published as "3.2" on Maven Central but the Xcore POM declares the
// range [3.2.0, 3.2.1). Force the available version (as in :model and :modelica).
configurations.all {
    resolutionStrategy {
        force("org.antlr:antlr-runtime:3.2")
    }
}

val emfCoreVersion:   String by rootProject.extra
val emfCommonVersion: String by rootProject.extra
val xcoreVersion:     String by rootProject.extra
val xcoreLibVersion:  String by rootProject.extra
val xtextVersion:     String by rootProject.extra

dependencies {
    // Xcore language support, build time only: the classpath of the Xtext builder that
    // reads the .xcore files and runs the EMF code generator.
    xtextLanguages("org.eclipse.emf:org.eclipse.emf.ecore.xcore:$xcoreVersion")
    xtextLanguages("org.eclipse.emf:org.eclipse.emf.ecore.xcore.lib:$xcoreLibVersion")
    xtextLanguages("org.eclipse.emf:org.eclipse.emf.codegen.ecore:$emfCoreVersion")
    xtextLanguages("org.eclipse.xtext:org.eclipse.xtext:$xtextVersion")
    xtextLanguages("org.eclipse.xtext:org.eclipse.xtext.common.types:$xtextVersion")
    xtextLanguages("org.eclipse.xtext:org.eclipse.xtext.ecore:$xtextVersion")

    // The syntax tree and the IR are EMF models, and the API takes EPackages and
    // ResourceSets, so EMF is part of the API.
    api("org.eclipse.emf:org.eclipse.emf.ecore:$emfCoreVersion")
    api("org.eclipse.emf:org.eclipse.emf.common:$emfCommonVersion")

    // Xcore resolves the type names of a .xcore file (String, int, ...) against the
    // classpath of the source set. The generated code does not use xcore.lib, since
    // the .xcore files have no operation bodies, so it stays off the runtime classpath,
    // and with it xbase.lib and its Java 8 pin (plan F8).
    compileOnly("org.eclipse.emf:org.eclipse.emf.ecore.xcore.lib:$xcoreLibVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // The conformance suite (plan M9) is a JUnit test factory that backends extend.
    testFixturesApi(platform("org.junit:junit-bom:5.10.1"))
    testFixturesApi("org.junit.jupiter:junit-jupiter-api")
}

xtext {
    version.set(xtextVersion)

    sourceSets {
        getByName("main") {
            srcDir("src/main/model")
        }
    }

    languages {
        create("ecore") {
            setup.set("org.eclipse.xtext.ecore.EcoreSupport")
        }
        create("xcore") {
            setup.set("org.eclipse.emf.ecore.xcore.XcoreStandaloneSetup")
            generator {
                // Registers the outlet as a Java source root, so javac compiles the
                // generated classes.
                outlet {
                    producesJava.set(true)
                }
            }
        }
    }
}

sourceSets {
    main {
        resources {
            exclude("**/*.xcore")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// javadoc fails on a source set with nothing but package-info.java files, which is the
// state of a source set before its first class (e.g. testFixtures until plan M9).
tasks.withType<Javadoc>().configureEach {
    onlyIf("it has classes to document") {
        !source.matching { exclude("**/package-info.java") }.isEmpty
    }
}

// Fails when the runtime classpath holds anything but EMF (architecture Q5, plan F8).
// A new runtime dependency is a design decision; widen allowedGroups only with one.
val checkRuntimeClasspath by tasks.registering {
    group = "verification"
    description = "Checks that the runtime classpath of :cql holds only EMF."
    val allowedGroups = setOf("org.eclipse.emf")
    val modules = configurations.runtimeClasspath.flatMap { classpath ->
        classpath.incoming.artifacts.resolvedArtifacts.map { artifacts ->
            artifacts.map { it.id.componentIdentifier }
                .filterIsInstance<ModuleComponentIdentifier>()
                .map { "${it.group}:${it.module}:${it.version}" }
        }
    }
    inputs.property("modules", modules)
    doLast {
        val foreign = modules.get().filter { it.substringBefore(':') !in allowedGroups }
        if (foreign.isNotEmpty()) {
            throw GradleException(
                "The runtime classpath of :cql may hold only EMF, but holds:\n  " +
                    foreign.joinToString("\n  "))
        }
    }
}

tasks.named("check") {
    dependsOn(checkRuntimeClasspath)
}
