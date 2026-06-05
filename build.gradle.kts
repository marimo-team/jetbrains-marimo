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
        pycharm("2026.1.2")
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
