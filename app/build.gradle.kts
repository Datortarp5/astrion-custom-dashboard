import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.custom.astrion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.custom.astrion"
        minSdk = 26
        targetSdk = 34
        // CI (.github/workflows/release.yml) passes a per-run build number,
        // so every published APK has a higher versionCode and a distinct
        // versionName ("1.1.9.<n>") that UpdateChecker sees as newer.
        val ciBuild = (findProperty("ciBuildNumber") as String?)?.toIntOrNull()
        versionCode = if (ciBuild != null) 1000 + ciBuild else 29
        versionName = if (ciBuild != null) "1.1.9.$ciBuild" else "1.1.9"
    }

    // Release signing comes from environment variables set by CI from
    // repository secrets — no keystore or password is ever committed. Using
    // the same keystore for every build is what lets each new APK install
    // over the previous one. Without these variables a local release build
    // is simply left unsigned.
    val releaseKeystore = System.getenv("ASTRION_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ASTRION_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ASTRION_KEY_ALIAS")
                keyPassword = System.getenv("ASTRION_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-beta"
        }
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Fails the build on lint errors (not just warnings) — matches
        // ktlint/detekt both being "check" tasks that fail the CI job.
        abortOnError = true
        warningsAsErrors = false
        // HTML report is the one worth opening locally; XML is what CI tools
        // parse if you ever wire up annotations on the PR.
        htmlReport = true
        xmlReport = true
        // Every issue also goes to the build log, not just the first one, so
        // a red CI run says what to fix without downloading the report.
        textReport = true
        textOutput = file("stdout")
        // Baseline: uncomment once you've triaged the current backlog of
        // warnings, to lock in "no new lint issues" without fixing everything
        // that already exists first.
        baseline = file("lint-baseline.xml")
    }
}

ktlint {
    // Matches Android's 4-space/no-wildcard-import conventions instead of
    // ktlint's plain-Kotlin defaults.
    android.set(true)
    verbose.set(true)
    outputToConsole.set(true)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    // Same reasoning as lint{} above — start permissive, tighten later:
    baseline = file("detekt-baseline.xml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(true) // lets GitHub annotate the PR diff directly
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
