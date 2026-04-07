import mihon.gradle.getBuildTime
import mihon.gradle.getLatestCommitCount
import mihon.gradle.getLatestCommitSha
import mihon.gradle.tasks.ReplaceShortcutsPlaceholderTask

plugins {
    alias(mihonx.plugins.android.application)
    alias(mihonx.plugins.compose)
    alias(mihonx.plugins.spotless)

    kotlin("plugin.parcelize")

    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.kotlin.serialization)

    id("com.github.ben-manes.versions")
}

if (gradle.startParameter.taskRequests.toString().contains("Standard")) {
    pluginManager.apply {
        apply(libs.plugins.google.services.get().pluginId)
        apply(libs.plugins.firebase.crashlytics.get().pluginId)
    }
}

android {
    namespace = "eu.kanade.tachiyomi"

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.sy"

        versionCode = 77
        versionName = "1.12.0"

        buildConfigField("String", "COMMIT_COUNT", "\"${getLatestCommitCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getLatestCommitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLatestCommitTime = false)}\"")
        buildConfigField("boolean", "INCLUDE_UPDATER", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        named("debug") {
            versionNameSuffix = "-${getLatestCommitCount()}"
            applicationIdSuffix = ".debug"
            isPseudoLocalesEnabled = true
        }
        create("releaseTest") {
            applicationIdSuffix = ".rt"
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
            matchingFallbacks.add("release")
        }
        named("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))

            buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLatestCommitTime = true)}\"")
        }
        create("benchmark") {
            initWith(getByName("release"))

            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks.add("release")
            isDebuggable = false
            isProfileable = true
            versionNameSuffix = "-benchmark"
            applicationIdSuffix = ".benchmark"
        }
    }

    sourceSets {
        getByName("benchmark").res.srcDirs("src/debug/res")
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    flavorDimensions.add("default")

    productFlavors {
        create("standard") {
            buildConfigField("boolean", "INCLUDE_UPDATER", "true")
            dimension = "default"
        }
        create("fdroid") {
            dimension = "default"
        }
        create("dev") {
            dimension = "default"
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols += listOf(
                "libandroidx.graphics.path",
                "libarchive-jni",
                "libconscrypt_jni",
                "libimagedecoder",
                "libquickjs",
                "libsqlite3x",
            ).map { "**/$it.so" }
        }
        resources {
            excludes += setOf(
                "kotlin-tooling-metadata.json",
                "LICENSE.txt",
                "META-INF/**/*.properties",
                "META-INF/**/LICENSE.txt",
                "META-INF/*.properties",
                "META-INF/*.version",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/README.md",
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true

        renderScript = false
        shaders = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-Xannotation-default-target=param-property",
        )
    }
}
