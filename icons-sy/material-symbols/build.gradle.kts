import mihon.gradle.tasks.GenerateVectorDrawablesTask

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

androidComponents {
    onVariants { variant ->
        val resSource = variant.sources.res ?: return@onVariants

        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<GenerateVectorDrawablesTask>("generate${variantName}VectorDrawables") {
            svgFiles.from(
                listOf(
                    "roundedFilled/menu_book",
                    "rounded/sd_card",
                ).map { layout.projectDirectory.file("src/main/valkyrieResources/$it.svg") },
            )
            // Symbols already in :icons:material-symbols that SY views need as drawables
            svgFiles.from(
                listOf(
                    "rounded/add",
                    "rounded/delete",
                    "rounded/download",
                    "rounded/info",
                    "rounded/sync",
                ).map { rootProject.layout.projectDirectory.file("icons/material-symbols/src/main/valkyrieResources/$it.svg") },
            )
        }
        resSource.addGeneratedSourceDirectory(task) { it.outputDir }
    }
}

dependencies {
    api(libs.androidx.compose.ui)
}
