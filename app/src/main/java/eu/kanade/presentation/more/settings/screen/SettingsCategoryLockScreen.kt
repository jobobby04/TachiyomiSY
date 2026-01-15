package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.library.components.CategoryPinDialog
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.util.storage.CategoryLockCrypto
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// SY -->
object SettingsCategoryLockScreen : SearchableSettings {

    /**
     * Provides the localized title for the category lock settings screen.
     *
     * @return The string resource ID for the category lock settings title.
     */
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = SYMR.strings.category_lock_settings

    /**
     * Builds the settings UI for category locking, providing controls to set, change, and remove a master PIN
     * and to set or change per-category PINs.
     *
     * The returned preferences include a master PIN group (with options to set/change/remove the master PIN)
     * and a category group (one entry per non-system category to set or change that category's PIN). PIN entry
     * is performed via dialogs and user feedback is shown via toasts.
     *
     * @return A list of `Preference` groups representing the master PIN controls and per-category PIN settings.
     */
    @Composable
    override fun getPreferences(): List<Preference> {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val context = LocalContext.current

        val categories by getCategories.subscribe().collectAsState(initial = emptyList())

        val hasMasterPin by CategoryLockCrypto.hasMasterPinFlow()
            .collectAsState(initial = CategoryLockCrypto.hasMasterPin())

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(SYMR.strings.category_lock_master_pin),
                preferenceItems = listOf(
                    kotlin.run {
                        var showMasterPinDialog by remember { mutableStateOf(false) }
                        val errorSettingMasterPin = stringResource(SYMR.strings.category_lock_error_setting_master_pin)
                        if (showMasterPinDialog) {
                            CategoryPinDialog(
                                categoryName = stringResource(SYMR.strings.category_lock_master_pin),
                                onDismiss = { showMasterPinDialog = false },
                                onPinEntered = { pin ->
                                    try {
                                        CategoryLockCrypto.setMasterPin(pin)
                                        context.toast(SYMR.strings.category_lock_master_pin_set)
                                        showMasterPinDialog = false
                                        true
                                    } catch (e: Exception) {
                                        context.toast(e.message ?: errorSettingMasterPin)
                                        false
                                    }
                                },
                                isSettingPin = true,
                            )
                        }
                        Preference.PreferenceItem.TextPreference(
                            title = stringResource(SYMR.strings.category_lock_master_pin),
                            subtitle = if (hasMasterPin) {
                                stringResource(SYMR.strings.category_lock_master_pin_change)
                            } else {
                                stringResource(SYMR.strings.category_lock_master_pin_set)
                            },
                            onClick = {
                                showMasterPinDialog = true
                            },
                        )
                    },
                ).let {
                    if (hasMasterPin) {
                        it + Preference.PreferenceItem.TextPreference(
                            title = stringResource(SYMR.strings.category_lock_master_pin_remove),
                            onClick = {
                                CategoryLockCrypto.removeMasterPin()
                                context.toast(SYMR.strings.category_lock_master_pin_removed)
                            },
                        )
                    } else {
                        it
                    }
                }.toImmutableList(),
            ),
            Preference.PreferenceGroup(
                title = stringResource(SYMR.strings.category_lock_settings),
                preferenceItems = categories
                    .filter { !it.isSystemCategory }
                    .map { category ->
                        kotlin.run {
                            val isLocked = CategoryLockCrypto.hasLock(category.id)
                            var showPinDialog by remember { mutableStateOf(false) }
                            val errorSettingPin = stringResource(SYMR.strings.category_lock_error_setting_pin)
                            if (showPinDialog) {
                                CategoryPinDialog(
                                    categoryName = category.name,
                                    onDismiss = { showPinDialog = false },
                                    onPinEntered = { pin ->
                                        try {
                                            CategoryLockCrypto.setPinForCategory(category.id, pin)
                                            context.toast(SYMR.strings.category_lock_pin_set)
                                            showPinDialog = false
                                            true
                                        } catch (e: Exception) {
                                            context.toast(e.message ?: errorSettingPin)
                                            false
                                        }
                                    },
                                    isSettingPin = true,
                                )
                            }
                            Preference.PreferenceItem.TextPreference(
                                title = category.name,
                                subtitle = if (isLocked) {
                                    stringResource(SYMR.strings.category_lock_change_pin)
                                } else {
                                    stringResource(SYMR.strings.category_lock_set_pin, category.name)
                                },
                                onClick = {
                                    showPinDialog = true
                                },
                            )
                        }
                    }
                    .toImmutableList(),
            ),
        )
    }
}
// SY <--
