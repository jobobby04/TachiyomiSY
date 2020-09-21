package eu.kanade.tachiyomi.ui.setting

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupCreateService
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.backup.BackupRestoreService
import eu.kanade.tachiyomi.data.backup.BackupRestoreValidator
import eu.kanade.tachiyomi.data.backup.full.FullBackupRestoreService
import eu.kanade.tachiyomi.data.backup.full.FullBackupRestoreValidator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.requestPermissionsSafe
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.getFilePicker
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsBackupController : SettingsController() {

    /**
     * Flags containing information of what to backup.
     */
    private var backupFlags = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermissionsSafe(arrayOf(WRITE_EXTERNAL_STORAGE), 500)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.backup

        preferenceCategory {
            titleRes = R.string.backup

            preference {
                key = "pref_create_backup"
                titleRes = R.string.pref_create_backup
                summaryRes = R.string.pref_create_backup_summ

                onClick {
                    if (!BackupCreateService.isRunning(context)) {
                        val ctrl = CreateBackupDialog(TYPE_LITE)
                        ctrl.targetController = this@SettingsBackupController
                        ctrl.showDialog(router)
                    } else {
                        context.toast(R.string.backup_in_progress)
                    }
                }
            }
            preference {
                key = "pref_restore_backup"
                titleRes = R.string.pref_restore_backup
                summaryRes = R.string.pref_restore_backup_summ

                onClick {
                    if (!BackupRestoreService.isRunning(context)) {
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                        intent.addCategory(Intent.CATEGORY_OPENABLE)
                        intent.type = "application/*"
                        val title = resources?.getString(R.string.file_select_backup)
                        val chooser = Intent.createChooser(intent, title)
                        startActivityForResult(chooser, CODE_BACKUP_RESTORE)
                    } else {
                        context.toast(R.string.restore_in_progress)
                    }
                }
            }
        }
        preferenceCategory {
            titleRes = R.string.backup_full

            preference {
                key = "pref_create_full_backup"
                titleRes = R.string.pref_create_full_backup
                summaryRes = R.string.pref_create_full_backup_summ

                onClick {
                    if (!BackupCreateService.isRunning(context)) {
                        val ctrl = CreateBackupDialog(TYPE_FULL)
                        ctrl.targetController = this@SettingsBackupController
                        ctrl.showDialog(router)
                    } else {
                        context.toast(R.string.backup_in_progress)
                    }
                }
            }
            preference {
                key = "pref_restore_full_backup"
                titleRes = R.string.pref_restore_full_backup
                summaryRes = R.string.pref_restore_full_backup_summ

                onClick {
                    if (!BackupRestoreService.isRunning(context)) {
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                        intent.addCategory(Intent.CATEGORY_OPENABLE)
                        intent.type = "application/*"
                        val title = resources?.getString(R.string.file_select_backup)
                        val chooser = Intent.createChooser(intent, title)
                        startActivityForResult(chooser, CODE_FULL_BACKUP_RESTORE)
                    } else {
                        context.toast(R.string.restore_in_progress)
                    }
                }
            }
        }
        preferenceCategory {
            titleRes = R.string.pref_backup_service_category

            intListPreference {
                key = Keys.backupInterval
                titleRes = R.string.pref_backup_interval
                entriesRes = arrayOf(
                    R.string.update_never,
                    R.string.update_6hour,
                    R.string.update_12hour,
                    R.string.update_24hour,
                    R.string.update_48hour,
                    R.string.update_weekly
                )
                entryValues = arrayOf("0", "6", "12", "24", "48", "168")
                defaultValue = "0"
                summary = "%s"

                onChange { newValue ->
                    val interval = (newValue as String).toInt()
                    BackupCreatorJob.setupTask(context, interval)
                    true
                }
            }
            preference {
                key = Keys.backupDirectory
                titleRes = R.string.pref_backup_directory

                onClick {
                    val currentDir = preferences.backupsDirectory().get()
                    try {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        startActivityForResult(intent, CODE_BACKUP_DIR)
                    } catch (e: ActivityNotFoundException) {
                        // Fall back to custom picker on error
                        startActivityForResult(preferences.context.getFilePicker(currentDir), CODE_BACKUP_DIR)
                    }
                }

                preferences.backupInterval().asImmediateFlow { isVisible = it > 0 }
                    .launchIn(scope)

                preferences.backupsDirectory().asFlow()
                    .onEach { path ->
                        val dir = UniFile.fromUri(context, path.toUri())
                        summary = dir.filePath + "/automatic"
                    }
                    .launchIn(scope)
            }
            intListPreference {
                key = Keys.numberOfBackups
                titleRes = R.string.pref_backup_slots
                entries = arrayOf("1", "2", "3", "4", "5")
                entryValues = entries
                defaultValue = "1"
                summary = "%s"

                preferences.backupInterval().asImmediateFlow { isVisible = it > 0 }
                    .launchIn(scope)
            }
            switchPreference {
                key = Keys.isFullBackup
                titleRes = R.string.pref_backup_type
                summaryRes = R.string.pref_backup_type_summary
                defaultValue = false

                preferences.backupInterval().asImmediateFlow { isVisible = it > 0 }
                    .launchIn(scope)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            CODE_BACKUP_DIR -> if (data != null && resultCode == Activity.RESULT_OK) {
                val activity = activity ?: return
                // Get uri of backup folder.
                val uri = data.data

                // Get UriPermission so it's possible to write files
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                if (uri != null) {
                    activity.contentResolver.takePersistableUriPermission(uri, flags)
                }

                // Set backup Uri
                preferences.backupsDirectory().set(uri.toString())
            }
            CODE_BACKUP_CREATE -> if (data != null && resultCode == Activity.RESULT_OK) {
                val activity = activity ?: return

                val uri = data.data
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                if (uri != null) {
                    activity.contentResolver.takePersistableUriPermission(uri, flags)
                }

                val file = UniFile.fromUri(activity, uri)

                activity.toast(R.string.creating_backup)

                BackupCreateService.start(activity, file.uri, backupFlags, BackupCreateService.BACKUP_TYPE_ONLINE)
            }
            CODE_BACKUP_RESTORE -> if (data != null && resultCode == Activity.RESULT_OK) {
                val uri = data.data
                if (uri != null) {
                    RestoreBackupDialog(uri /* SY --> */, TYPE_LITE /* SY <-- */, MODE_ONLINE).showDialog(router)
                }
            }
            CODE_FULL_BACKUP_CREATE -> if (data != null && resultCode == Activity.RESULT_OK) {
                val activity = activity ?: return

                val uri = data.data
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                if (uri != null) {
                    activity.contentResolver.takePersistableUriPermission(uri, flags)
                }

                val file = UniFile.fromUri(activity, uri)

                activity.toast(R.string.creating_backup)

                BackupCreateService.start(activity, file.uri, backupFlags, BackupCreateService.BACKUP_TYPE_OFFLINE)
            }
            CODE_FULL_BACKUP_RESTORE -> if (data != null && resultCode == Activity.RESULT_OK) {
                val uri = data.data
                if (uri != null) {
                    val options = arrayOf(
                        R.string.full_restore_online,
                        R.string.full_restore_offline
                    )
                        .map { activity!!.getString(it) }
                    MaterialDialog(activity!!)
                        .title(R.string.full_restore_mode)
                        .listItemsSingleChoice(
                            items = options,
                            initialSelection = 0
                        ) { _, index, _ ->
                            RestoreBackupDialog(
                                uri,
                                TYPE_FULL,
                                if (index != 0) MODE_OFFLINE else MODE_ONLINE
                            ).showDialog(router)
                        }
                        .positiveButton(R.string.action_restore)
                        .show()
                }
            }
        }
    }

    fun createBackup(flags: Int, type: Int) {
        backupFlags = flags

        // Get dirs
        val currentDir = preferences.backupsDirectory().get()

        try {
            // Use Android's built-in file creator
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/*")
                .putExtra(Intent.EXTRA_TITLE, Backup.getDefaultFilename(type == TYPE_FULL))

            startActivityForResult(intent, if (type == TYPE_LITE) CODE_BACKUP_CREATE else CODE_FULL_BACKUP_CREATE)
        } catch (e: ActivityNotFoundException) {
            // Handle errors where the android ROM doesn't support the built in picker
            startActivityForResult(preferences.context.getFilePicker(currentDir), if (type == TYPE_LITE) CODE_BACKUP_CREATE else CODE_FULL_BACKUP_CREATE)
        }
    }

    class CreateBackupDialog(bundle: Bundle? = null) : DialogController(bundle) {
        constructor(type: Int) : this(
            Bundle().apply {
                putInt(KEY_TYPE, type)
            }
        )

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val type = args.getInt(KEY_TYPE)
            val activity = activity!!
            val options = arrayOf(
                R.string.manga,
                R.string.categories,
                R.string.chapters,
                R.string.track,
                R.string.history
            )
                .map { activity.getString(it) }

            return MaterialDialog(activity)
                .title(R.string.pref_create_backup)
                .message(R.string.backup_choice)
                .listItemsMultiChoice(
                    items = options,
                    disabledIndices = intArrayOf(0),
                    initialSelection = intArrayOf(0, 1, 2, 3, 4)
                ) { _, positions, _ ->
                    var flags = 0
                    for (i in 1 until positions.size) {
                        when (positions[i]) {
                            1 -> flags = flags or BackupCreateService.BACKUP_CATEGORY
                            2 -> flags = flags or BackupCreateService.BACKUP_CHAPTER
                            3 -> flags = flags or BackupCreateService.BACKUP_TRACK
                            4 -> flags = flags or BackupCreateService.BACKUP_HISTORY
                        }
                    }

                    (targetController as? SettingsBackupController)?.createBackup(flags, type)
                }
                .positiveButton(R.string.action_create)
                .negativeButton(android.R.string.cancel)
        }

        private companion object {
            const val KEY_TYPE = "CreateBackupDialog.type"
        }
    }

    class RestoreBackupDialog(bundle: Bundle? = null) : DialogController(bundle) {
        constructor(uri: Uri, type: Int, mode: Boolean) : this(
            Bundle().apply {
                putParcelable(KEY_URI, uri)
                putInt(KEY_TYPE, type)
                putBoolean(KEY_MODE, mode)
            }
        )

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val activity = activity!!
            val uri: Uri = args.getParcelable(KEY_URI)!!
            val type: Int = args.getInt(KEY_TYPE)
            val mode: Boolean = args.getBoolean(KEY_MODE)

            return try {
                var message = activity.getString(R.string.backup_restore_content)

                val results = if (type == TYPE_LITE) BackupRestoreValidator.validate(activity, uri) else FullBackupRestoreValidator.validate(activity, uri)
                if (results.missingSources.isNotEmpty()) {
                    message += "\n\n${activity.getString(R.string.backup_restore_missing_sources)}\n${results.missingSources.joinToString("\n") { "- $it" }}"
                }
                if (results.missingTrackers.isNotEmpty()) {
                    message += "\n\n${activity.getString(R.string.backup_restore_missing_trackers)}\n${results.missingTrackers.joinToString("\n") { "- $it" }}"
                }

                MaterialDialog(activity)
                    .title(R.string.pref_restore_backup)
                    .message(text = message)
                    .positiveButton(R.string.action_restore) {
                        if (type == TYPE_LITE) {
                            BackupRestoreService.start(activity, uri)
                        } else if (type == TYPE_FULL) {
                            FullBackupRestoreService.start(activity, uri, mode)
                        }
                    }
            } catch (e: Exception) {
                MaterialDialog(activity)
                    .title(R.string.invalid_backup_file)
                    .message(text = e.message)
                    .positiveButton(android.R.string.cancel)
            }
        }

        private companion object {
            const val KEY_URI = "RestoreBackupDialog.uri"
            const val KEY_TYPE = "RestoreBackupDialog.type"
            const val KEY_MODE = "RestoreBackupDialog.mode"
        }
    }

    private companion object {
        const val CODE_BACKUP_CREATE = 501
        const val CODE_BACKUP_RESTORE = 502
        const val CODE_BACKUP_DIR = 503
        const val CODE_FULL_BACKUP_CREATE = 504
        const val CODE_FULL_BACKUP_RESTORE = 505
        const val TYPE_LITE = 1
        const val TYPE_FULL = 2
        const val MODE_ONLINE = true
        const val MODE_OFFLINE = false
    }
}
