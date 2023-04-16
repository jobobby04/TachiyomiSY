plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "tachiyomi.domain"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(project(":source-api"))
    implementation(project(":core"))

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)

    api(libs.sqldelight.android.paging)

    // SY -->
    implementation(libs.injekt.core)
    // SY <--

    testImplementation(libs.bundles.test)
}
