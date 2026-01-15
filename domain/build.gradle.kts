plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("mihon.code.coverage")
}

android {
    namespace = "tachiyomi.domain"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.core.common)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)
    implementation(kotlinx.bundles.serialization)

    implementation(libs.unifile)

    api(libs.sqldelight.android.paging)

    compileOnly(compose.runtime.annotation)

    // SY -->
    implementation(libs.injekt)
    // SY <--

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testImplementation(sylibs.koin.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
