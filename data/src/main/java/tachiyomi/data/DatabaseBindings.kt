package tachiyomi.data

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConcurrencyModel.MultipleReadersSingleWriter
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfigurableDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConnectionFactory
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import mihon.data.database.DatabaseRepositoryImpl
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver

@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseBindings {

    // SY -->
    @Provides
    @SingleIn(AppScope::class)
    fun providesSqlDriver(
        context: Context,
        securityPreferences: SecurityPreferences,
        cbzCrypto: CbzCrypto,
        databaseRepository: DatabaseRepositoryImpl,
    ): SqlDriver {
        val isLowRam = context.getSystemService<ActivityManager>()?.isLowRamDevice == true
        val configuration = AndroidxSqliteConfiguration(
            isForeignKeyConstraintsEnabled = true,
            concurrencyModel = MultipleReadersSingleWriter(isWal = true, walCount = if (isLowRam) 1 else 4),
        )
        // Runs before the driver compares the schema version, and onOpen once the schema is ready
        val onConfigure: suspend AndroidxSqliteConfigurableDriver.() -> Unit = {
            val version = executePragmaQuery("user_version", { QueryResult.Value(it.apply { next() }.getLong(0)) })
            databaseRepository.isMigrating.value = version in 1..<Database.Schema.version
        }
        val onOpen: suspend SqlDriver.() -> Unit = {
            databaseRepository.isMigrating.value = false
        }
        if (securityPreferences.encryptDatabase.get()) {
            System.loadLibrary("sqlcipher")

            return AndroidxSqliteDriver(
                connectionFactory = busyTimeoutConnectionFactory(
                    SQLCipherDriver(
                        cbzCrypto.getDecryptedPasswordSql(),
                        null,
                        null,
                    ),
                ),
                databaseType = AndroidxSqliteDatabaseType.FileProvider(context, CbzCrypto.DATABASE_NAME),
                schema = Database.Schema,
                configuration = configuration,
                onConfigure = onConfigure,
                onOpen = onOpen,
            )
        }
        return AndroidxSqliteDriver(
            connectionFactory = busyTimeoutConnectionFactory(BundledSQLiteDriver()),
            databaseType = AndroidxSqliteDatabaseType.FileProvider(context, "tachiyomi.db"),
            schema = Database.Schema,
            configuration = configuration,
            onConfigure = onConfigure,
            onOpen = onOpen,
        )
    }

    private fun busyTimeoutConnectionFactory(sqliteDriver: SQLiteDriver) = object : AndroidxSqliteConnectionFactory {
        override val driver: SQLiteDriver = sqliteDriver

        override fun createConnection(name: String): SQLiteConnection {
            return driver.open(name).apply {
                execSQL("PRAGMA busy_timeout = 3000")
            }
        }
    }
    // SY <--

    @Provides
    @SingleIn(AppScope::class)
    fun providesDatabase(driver: SqlDriver): Database {
        return Database(
            driver = driver,
            historyAdapter = History.Adapter(
                read_atAdapter = InstantColumnAdapter,
            ),
            mangaAdapter = Manga.Adapter(
                remote_genreAdapter = StringListColumnAdapter,
                remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_favorite_atAdapter = InstantColumnAdapter,
                state_chapter_last_updateAdapter = InstantColumnAdapter,
                state_chapter_next_updateAdapter = InstantColumnAdapter,
                state_cover_last_modifiedAdapter = InstantColumnAdapter,
            ),
            chapterAdapter = Chapter.Adapter(
                remote_date_uploadAdapter = InstantColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                state_date_fetchAdapter = InstantColumnAdapter,
            ),
            manga_trackAdapter = Manga_track.Adapter(
                start_dateAdapter = InstantColumnAdapter,
                finish_dateAdapter = InstantColumnAdapter,
            ),
        )
    }
}
