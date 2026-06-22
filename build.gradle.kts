import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("com.diffplug.spotless") version "7.0.2"
}

spotless {
    kotlin {
        target("src/**/*.kt")
        licenseHeader("/* Copyright \$YEAR Marimo. All rights reserved. */\n\n")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Floor: 2026.1 — the install/probe path uses Python packaging APIs that don't exist
            // before then. Open-ended ceiling so new IDE releases don't lock the plugin out
            // (an explicit untilBuild would otherwise default to the build branch we compile against).
            sinceBuild = "261"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        // The verifier runs against a backend IDE image that omits the platform's frontend /
        // split-mode modules. Resolving the bundled Python plugin transitively reaches those, so the
        // verifier can't resolve com.intellij.modules.python and reports every com.jetbrains.python
        // class as "not found" — yet Python is present at runtime in every targeted IDE and the
        // plugin loads fine. The ignore file mutes only that "not found" signature, scoped to
        // com.jetbrains.python, so real method/class-level incompatibilities still fail verification.
        ignoredProblemsFile = layout.projectDirectory.file("verifier-ignored-problems.txt")
    }

    // Signing and publishing read their material from environment variables, supplied in CI by the
    // 'release' GitHub environment secrets. They are absent for local builds, where signPlugin and
    // publishPlugin simply aren't run.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // PyCharm is the core target, so build and run against PyCharm (unified since 2025.1; its
        // free core tier covers what the plugin needs). Depending on the bundled PythonCore module —
        // the smallest Python surface — keeps the plugin runnable in IntelliJ IDEA and other
        // JetBrains IDEs whose Python plugin is a superset of PythonCore.
        pycharm("2026.1.3")
        bundledPlugin("PythonCore")
        bundledPlugin("org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Platform)
    }
}

val sampleProjectPath = layout.projectDirectory.dir("examples").asFile.absolutePath

tasks {
    runIde {
        argumentProviders += CommandLineArgumentProvider {
            listOf(sampleProjectPath)
        }
    }
}
