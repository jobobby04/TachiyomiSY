plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)

    alias(mihonx.plugins.spotless)

    alias(libs.plugins.valkyrie)
}

android {
    namespace = "mihon.icons.sy.materialsymbols"
}

spotless {
    format("svg") {
        target("src/**/*.svg")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

valkyrie {
    packageName = "mihon.icons.sy.materialsymbols"
    generateAtSync = true

    imageVector {
        suppressUnusedReceiverWarning = true
    }

    iconPack {
        name = "SYMaterialSymbols"
        targetSourceSet = "main"

        nested {
            name = "Rounded"
            sourceFolder = "rounded"
            autoMirror = false
        }

        nested {
            name = "RoundedFilled"
            sourceFolder = "roundedFilled"
            autoMirror = false
        }

        // https://github.com/ComposeGears/Valkyrie/pull/1019
        nested {
            name = "AutoMirroredRounded"
            sourceFolder = "autoMirroredRounded"
            autoMirror = true
        }

        nested {
            name = "AutoMirroredRoundedFilled"
            sourceFolder = "autoMirroredRoundedFilled"
            autoMirror = true
        }
    }
}

dependencies {
    api(libs.androidx.compose.ui)
}
