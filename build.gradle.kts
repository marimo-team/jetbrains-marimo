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

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Build against IntelliJ IDEA + the open-source Python (PythonCore) plugin. PythonCore is
        // the smallest Python surface; compiling against it guarantees the plugin also runs in
        // PyCharm and other JetBrains IDEs whose Python plugin is a superset of PythonCore.
        intellijIdea("2026.1.3")
        plugin("PythonCore", "261.25134.95")
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
