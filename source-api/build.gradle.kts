import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.github.ben-manes.versions")
}

kotlin {
    androidTarget()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.serialization.json)
                api(libs.injekt)
                api(libs.rxJava)
                api(libs.jsoup)

                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.runtime)

                // SY -->
                api(projects.i18n)
                api(projects.i18nSy)
                api(libs.kotlin.reflect)
                // SY <--
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(projects.core.common)
                api(libs.androidx.preference)

                // Workaround for https://youtrack.jetbrains.com/issue/KT-57605
                implementation(libs.kotlinx.coroutines.android)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

android {
    namespace = "eu.kanade.tachiyomi.source"

    defaultConfig {
        consumerProguardFile("consumer-proguard.pro")
    }
}
