plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)

    id("com.github.ben-manes.versions")
}

android {
    namespace = "eu.kanade.tachiyomi.source"

    defaultConfig {
        consumerProguardFiles("consumer-proguard.pro")
    }
}

dependencies {
    implementation(projects.core.common)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.injekt)
    implementation(libs.rxJava)
    implementation(libs.jsoup)

    implementation(libs.androidx.preference)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)

    // SY -->
    implementation(projects.i18n)
    implementation(projects.i18nSy)
    implementation(libs.kotlin.reflect)
    // SY <--
}
