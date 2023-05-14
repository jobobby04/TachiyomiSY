package eu.kanade.tachiyomi.util

import android.content.Context
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import net.zetetic.database.sqlcipher.SQLiteDatabase
import tachiyomi.data.Database

private const val IMPLEMENTED_SCHEMA_VERSION = 28

// SY -->
object SqlCipherHelper {
    fun migrateDatabase(context: Context) {
        val databaseFile = context.getDatabasePath(CbzCrypto.DATABASE_NAME)
        if (databaseFile.exists()) {
            val database = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                CbzCrypto.getDecryptedPasswordSql(),
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null,
                null,
            )

            database.use { db ->
                val oldVersion = db.version
                val newVersion = Database.Schema.version
                if (Database.Schema.version != db.version) {
                    if (newVersion > IMPLEMENTED_SCHEMA_VERSION) {
                        throw IllegalStateException("Sql Cipher has not been configured for Database Schema version: $newVersion yet")
                    }

                    db.version = Database.Schema.version

                    if (oldVersion <= 25 && newVersion > 25) {
                        db.rawExecSQL("ALTER TABLE mangas ADD COLUMN calculate_interval INTEGER DEFAULT 0 NOT NULL")
                    }

                    if (oldVersion <= 26 && newVersion > 26) {
                        db.rawExecSQL("ALTER TABLE eh_favorites RENAME TO eh_favorites_temp")
                        db.rawExecSQL(
                            """
                            |CREATE TABLE eh_favorites (
                            |    gid TEXT NOT NULL,
                            |    token TEXT NOT NULL,
                            |    title TEXT NOT NULL,
                            |    category INTEGER NOT NULL,
                            |    PRIMARY KEY (gid, token)
                            |)
                            """.trimMargin(),
                        )
                        db.rawExecSQL(
                            """
                            |INSERT INTO eh_favorites
                            |SELECT gid, token, title, category
                            |FROM eh_favorites_temp
                            """.trimMargin(),
                        )
                        db.rawExecSQL(
                            "DROP TABLE IF EXISTS eh_favorites_temp",
                        )
                        db.rawExecSQL(
                            """
                            |CREATE TABLE eh_favorites_alternatives (
                            |    id INTEGER PRIMARY KEY AUTOINCREMENT,
                            |    gid TEXT NOT NULL,
                            |    token TEXT NOT NULL,
                            |    otherGid TEXT NOT NULL,
                            |    otherToken TEXT NOT NULL,
                            |    FOREIGN KEY (gid, token) REFERENCES eh_favorites(gid, token)
                            |)
                            """.trimMargin(),
                        )
                        db.rawExecSQL("CREATE INDEX eh_favorites_alternatives_gid_token_index ON eh_favorites_alternatives(gid, token)")
                        db.rawExecSQL("CREATE INDEX eh_favorites_alternatives_other_gid_token_index ON eh_favorites_alternatives(otherGid, otherToken)")
                    }

                    if (oldVersion <= 27 && newVersion > 27) {
                        db.rawExecSQL("DROP INDEX IF EXISTS eh_favorites_alternatives_gid_token_index")
                        db.rawExecSQL("DROP INDEX IF EXISTS eh_favorites_alternatives_other_gid_token_index")
                        db.rawExecSQL("DROP TABLE IF EXISTS eh_favorites_alternatives")
                        db.rawExecSQL(
                            """
                            |CREATE TABLE eh_favorites_alternatives (
                            |    id INTEGER PRIMARY KEY AUTOINCREMENT,
                            |    gid TEXT NOT NULL,
                            |    token TEXT NOT NULL,
                            |    otherGid TEXT NOT NULL,
                            |    otherToken TEXT NOT NULL,
                            |    UNIQUE (gid, token, otherGid, otherToken)
                            |)
                            """.trimMargin(),
                        )
                        db.rawExecSQL("CREATE INDEX eh_favorites_alternatives_gid_token_index ON eh_favorites_alternatives(gid, token)")
                        db.rawExecSQL("CREATE INDEX eh_favorites_alternatives_other_gid_token_index ON eh_favorites_alternatives(otherGid, otherToken)")
                    }


                }
            }
        }
    }
}
// SY <--
