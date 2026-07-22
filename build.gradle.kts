import org.gradle.api.tasks.WriteProperties
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

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

// The telemetry environment is fixed when the artifact is built: only the release workflow passes
// -Ptelemetry.env=production. Every other build — local runIde, side-loaded buildPlugin zips, CI
// checks — stays "development", so analytics can exclude non-release traffic. Generated into a
// bundled resource so it travels with the plugin and can't be spoofed by the runtime environment.
val telemetryEnv = providers.gradleProperty("telemetry.env").orElse("development").get()
val telemetryResourcesDir = layout.buildDirectory.dir("generated/telemetry-resources")

val generateTelemetryConfig = tasks.register<WriteProperties>("generateTelemetryConfig") {
    destinationFile = telemetryResourcesDir.map { it.file("telemetry.properties") }
    property("environment", telemetryEnv)
}

sourceSets.named("main") {
    resources.srcDir(telemetryResourcesDir)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(generateTelemetryConfig)
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
        // "What's new" on the Marketplace listing is rendered from the matching CHANGELOG.md section,
        // falling back to [Unreleased] for builds whose version isn't pinned in the changelog yet.
        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(project.version.toString()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
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

        // The plugin deliberately calls a few @ApiStatus.Internal platform methods that have no public
        // equivalent (opening a terminal tab via TerminalToolWindowManager, reading the plugin's own
        // version via PluginManagerCore). Drop INTERNAL_API_USAGES from the default failure set so those
        // don't fail verification, while still failing on real compatibility and override-only problems.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
        )
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
    // The IDE provides the Kotlin stdlib; a second copy leaking in transitively poisons the
    // platform-test classpath (project creation deadlocks and every BasePlatformTestCase hangs),
    // and plugins must not bundle their own stdlib -> https://jb.gg/intellij-platform-kotlin-stdlib
    implementation("com.posthog:posthog-server:2.8.1") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("io.sentry:sentry:7.22.6")

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
