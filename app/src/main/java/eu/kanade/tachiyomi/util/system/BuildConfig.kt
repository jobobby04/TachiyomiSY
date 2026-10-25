package eu.kanade.tachiyomi.util.system

import eu.kanade.tachiyomi.BuildConfig
import exh.syDebugVersion

val isDebugBuildType: Boolean
    get() = BuildConfig.BUILD_TYPE == "debug"

val /* SY --> */isPreviewBuildType/* SY <-- */: Boolean
    get() = BuildConfig.BUILD_TYPE /* SY --> */ == "release" && syDebugVersion != "0" /* SY <-- */

val isReleaseBuildType: Boolean
    get() = BuildConfig.BUILD_TYPE == "release" /* SY --> */ && syDebugVersion == "0" /* SY <-- */

val isBenchmarkBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE.contains("nonMinified") || BuildConfig.BUILD_TYPE.contains("benchmark")
