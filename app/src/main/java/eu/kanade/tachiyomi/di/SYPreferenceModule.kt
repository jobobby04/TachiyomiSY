package eu.kanade.tachiyomi.di

import android.app.Application
import eu.kanade.tachiyomi.data.storage.gdrive.GoogleDrivePreferences
import eu.kanade.tachiyomi.data.storage.gdrive.GoogleDriveService
import exh.pref.DelegateSourcePreferences
import exh.source.ExhPreferences
import uy.kohesive.injekt.api.InjektRegistrar

class SYPreferenceModule(val application: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory {
            DelegateSourcePreferences(
                preferenceStore = get(),
            )
        }

        addSingletonFactory {
            ExhPreferences(get())
        }

        addSingletonFactory {
            GoogleDrivePreferences(get())
        }

        addSingletonFactory {
            GoogleDriveService(
                context = application,
                networkHelper = get(),
                preferences = get(),
            )
        }

        addSingletonFactory {
            eu.kanade.tachiyomi.data.storage.gdrive.GoogleDriveIndexManager(
                service = get(),
            )
        }
    }
}
