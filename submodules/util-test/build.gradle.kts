plugins {
    id("java")
    id("antlr")
}

// VIATRA 2.8 is the last release built for Java 8 (2.9 needs Java 11 and Xtext 2.35).
val viatraVersion = "2.8.2"
val xbaseLibVersion: String by rootProject.extra

dependencies {
    // VIATRA Query with its pattern language: .vql text is parsed at test run time,
    // no code generation.
    api("org.eclipse.viatra:viatra-query-language:$viatraVersion")
    // VIATRA's validation framework: patterns annotated @Constraint, whose matches are
    // violations. Only the headless core; its Eclipse runtime needs the workbench.
    // The core runs on EVM, which the transformation runtime brings.
    api("org.eclipse.viatra:org.eclipse.viatra.addon.validation.core:$viatraVersion")
    api("org.eclipse.viatra:viatra-transformation-runtime:$viatraVersion")
    // Constraints, unlike a resolution strategy, also apply in the projects whose tests
    // use :util-test (:language, :compiler).
    constraints {
        // VIATRA 2.8 is built against Xtext 2.20. :model holds org.eclipse.xtext.xbase.lib
        // strictly at the last Java 8 release (xbaseLibVersion); align the rest of Xtext
        // with it, so no classpath mixes two Xtext versions.
        for (artifact in listOf("org.eclipse.xtext", "org.eclipse.xtext.xbase",
                "org.eclipse.xtext.common.types", "org.eclipse.xtext.util", "org.eclipse.xtext.xbase.lib")) {
            api("org.eclipse.xtext:$artifact") { version { require(xbaseLibVersion) } }
        }
        // Xtext reads JDK class files (java.lang.Override, ...) with ASM. The ASM 9.1 it
        // asks for stops at Java 17 class files; 9.7 reads up to Java 23, runs on Java 8.
        for (artifact in listOf("asm", "asm-commons", "asm-tree", "asm-analysis")) {
            api("org.ow2.asm:$artifact") { version { require("9.7.1") } }
        }
        // Xtext's parsers are generated for ANTLR 3.2; ST4 asks for 3.5.2.
        api("org.antlr:antlr-runtime") { version { strictly("3.2") } }
    }

    antlr("org.antlr:antlr4:4.9.2")
    implementation("org.antlr:antlr4:4.9.2")

    implementation(project(":util"))

    // The expectation engine (org.openmodelica.crml.testutil.vql) works on any EMF
    // model. Versions are the ones the root build pins for the whole project.
    val emfCoreVersion: String by rootProject.extra
    val emfCommonVersion: String by rootProject.extra
    implementation("org.eclipse.emf:org.eclipse.emf.ecore:$emfCoreVersion")
    implementation("org.eclipse.emf:org.eclipse.emf.common:$emfCommonVersion")

    implementation("com.j2html:j2html:1.6.0")
    
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    implementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.platform:junit-platform-reporting:1.10.1")
    implementation("org.junit.platform:junit-platform-launcher")

    implementation("com.aventstack:extentreports:5.0.9")
    
}

sourceSets {
    main {
        java {
            srcDir("build/generated-src/antlr/main")
        }
    }
}

tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments.addAll(listOf(
        "-visitor",
        "-long-messages",
        "-Xlog",
        "-listener",
        "-package", "crml.language.grammar",
        "-lib", "src/main/antlr/crml/language/grammar"
    ))
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
    ignoreFailures = true
}
