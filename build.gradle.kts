import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.WriteProperties
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PublishPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.SignPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("com.diffplug.spotless") version "8.10.0"
    id("dev.detekt") version "2.0.0-alpha.6"
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktfmt("0.64").kotlinlangStyle()
        licenseHeader("/* Copyright \$YEAR Marimo. All rights reserved. */\n\n")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt("0.64").kotlinlangStyle()
    }
}

detekt {
    config.setFrom("detekt.yml")
    buildUponDefaultConfig = false
    ignoreFailures = false
    failOnSeverity = FailOnSeverity.Error
}

// The telemetry environment is fixed when the artifact is built: only the release workflow passes
// -Ptelemetry.env=production. Every other build — local runIde, side-loaded buildPlugin zips, CI
// checks — stays "development". Live PostHog/Sentry clients are off unless this is a production
// artifact or the build passes -Ptelemetry.live=true, so local opt-in cannot pollute analytics.
val telemetryEnv = providers.gradleProperty("telemetry.env").orElse("development").get()
val telemetryLive =
    providers
        .gradleProperty("telemetry.live")
        .orElse(if (telemetryEnv == "production") "true" else "false")
        .get()
val telemetryResourcesDir = layout.buildDirectory.dir("generated/telemetry-resources")

val generateTelemetryConfig =
    tasks.register<WriteProperties>("generateTelemetryConfig") {
        destinationFile = telemetryResourcesDir.map { it.file("telemetry.properties") }
        property("environment", telemetryEnv)
        property("live", telemetryLive)
        // Baked in so the runtime reports the plugin's own version without querying an internal
        // platform API.
        property("version", providers.provider { project.version.toString() })
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
            // (an explicit untilBuild would otherwise default to the build branch we compile
            // against).
            sinceBuild = "261"
            untilBuild = provider { null }
        }
        // "What's new" on the Marketplace listing is rendered from the matching CHANGELOG.md
        // section,
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
        ides {
            // Fixed targets keep verifier results reproducible as new IDE releases ship.
            create(IntelliJPlatformType.PyCharm, "2026.1.3")
            create(IntelliJPlatformType.PyCharm, "2026.2.1")
        }

        // The verifier runs against a backend IDE image that omits the platform's frontend /
        // split-mode modules. Resolving the bundled Python plugin transitively reaches those, so
        // the
        // verifier can't resolve com.intellij.modules.python and reports every com.jetbrains.python
        // class as "not found" — yet Python is present at runtime in every targeted IDE and the
        // plugin loads fine. The ignore file mutes only that "not found" signature, scoped to
        // com.jetbrains.python, so real method/class-level incompatibilities still fail
        // verification.
        ignoredProblemsFile = layout.projectDirectory.file("verifier-ignored-problems.txt")

        failureLevel =
            listOf(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
                VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
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
    implementation("com.posthog:posthog-server:2.14.2") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("io.sentry:sentry:8.53.0")

    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more:
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // PyCharm is the core target, so build and run against PyCharm (unified since 2025.1; its
        // free core tier covers what the plugin needs). Depending on the bundled PythonCore module
        // —
        // the smallest Python surface — keeps the plugin runnable in IntelliJ IDEA and other
        // JetBrains IDEs whose Python plugin is a superset of PythonCore.
        pycharm("2026.1.3")
        bundledPlugin("PythonCore")
        bundledPlugin("org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Platform)
    }
}

// Sign the downloaded zip. Drop the plugin's assemble-before-publish edge.
val reusePluginArchive = providers.gradleProperty("plugin.reuseArchive")

if (reusePluginArchive.isPresent) {
    val reusedArchive = layout.projectDirectory.file(reusePluginArchive.get())
    val signPlugin = tasks.named<SignPluginTask>("signPlugin")
    signPlugin.configure { archiveFile.set(reusedArchive) }
    tasks.named<PublishPluginTask>("publishPlugin").configure {
        archiveFile.set(signPlugin.flatMap { it.signedArchiveFile })
        setDependsOn(dependsOn.filterNot { dependencyName(it) == "buildPlugin" })
    }
}

fun dependencyName(dependency: Any): String? =
    when (dependency) {
        is Task -> dependency.name
        is TaskProvider<*> -> dependency.name
        is String -> dependency
        else -> null
    }
