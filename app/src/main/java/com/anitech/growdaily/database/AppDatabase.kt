package com.anitech.growdaily.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.anitech.growdaily.data_class.ChecklistVersionEntity
import com.anitech.growdaily.data_class.ChecklistProgressItemEntity
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.ListTaskCrossRef
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import com.anitech.growdaily.data_class.TaskExtraDateEntity
import com.anitech.growdaily.data_class.TaskOrderChangeLog
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import com.anitech.growdaily.data_class.UntilCompleteChildEntity
import com.anitech.growdaily.database.dao.ChecklistProgressDao
import com.anitech.growdaily.database.dao.ChecklistVersionDao
import com.anitech.growdaily.database.dao.ListDao
import com.anitech.growdaily.database.dao.OrderLogDao
import com.anitech.growdaily.database.dao.TaskCompletionDao
import com.anitech.growdaily.database.dao.TaskDao
import com.anitech.growdaily.database.dao.TaskDaySnapshotDao
import com.anitech.growdaily.database.dao.TaskExtraDateDao
import com.anitech.growdaily.database.dao.TaskTrackingVersionDao
import com.anitech.growdaily.database.dao.UntilCompleteChildDao
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.FileInputStream

@Database(
    entities = [
        TaskEntity::class,
        ListEntity::class,
        TaskOrderChangeLog::class,
        ListTaskCrossRef::class,
        TaskCompletionEntity::class,
        ChecklistVersionEntity::class,
        ChecklistProgressItemEntity::class,
        TaskTrackingVersionEntity::class,
        TaskDaySnapshotEntity::class,
        TaskExtraDateEntity::class,
        UntilCompleteChildEntity::class
    ],
    version = 20,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyTaskDao(): TaskDao
    abstract fun listDao(): ListDao
    abstract fun orderLogDao(): OrderLogDao
    abstract fun taskCompletionDao():TaskCompletionDao
    abstract fun checklistVersionDao(): ChecklistVersionDao
    abstract fun checklistProgressDao(): ChecklistProgressDao
    abstract fun taskTrackingVersionDao(): TaskTrackingVersionDao
    abstract fun taskDaySnapshotDao(): TaskDaySnapshotDao
    abstract fun taskExtraDateDao(): TaskExtraDateDao
    abstract fun untilCompleteChildDao(): UntilCompleteChildDao


    companion object {
        private const val DATABASE_NAME = "task_database"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            // System.loadLibrary("sqlcipher")
            // val passphrase = SecurityUtils.getDatabasePassphrase(context)
            // val hexKey = bytesToHex(passphrase)
            // val databaseFile = context.getDatabasePath(DATABASE_NAME)

            // if (databaseFile.exists()) {
            //     if (isPlaintextSqliteDatabase(databaseFile)) {
            //         migratePlaintextDatabaseIfNeeded(context, passphrase)
            //     } else if (!isValidDatabase(databaseFile, hexKey)) {
            //         // Key mismatch or corruption - clear DB to recover
            //         deleteDatabaseWithSidecars(databaseFile)
            //     }
            // }

            // val factory = SupportOpenHelperFactory("x'$hexKey'".toByteArray(), null, false)
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                // .openHelperFactory(factory)
                .build()
        }

        private fun migratePlaintextDatabaseIfNeeded(context: Context, passphrase: ByteArray) {
            val databaseFile = context.getDatabasePath(DATABASE_NAME)
            if (!databaseFile.exists() || !isPlaintextSqliteDatabase(databaseFile)) return

            val parent = databaseFile.parentFile ?: return
            val encryptedFile = File(parent, "$DATABASE_NAME.encrypted")
            val backupFile = File(parent, "$DATABASE_NAME.plaintext.bak")
            encryptedFile.delete()

            var database: SQLiteDatabase? = null
            try {
                val hexKey = bytesToHex(passphrase)
                SQLiteDatabase.openOrCreateDatabase(
                    encryptedFile.absolutePath,
                    "x'$hexKey'".toByteArray(),
                    null,
                    null,
                    null
                ).close()

                database = SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    ByteArray(0),
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null,
                    null
                )

                val escapedPath = encryptedFile.absolutePath.replace("'", "''")
                database.execSQL("ATTACH DATABASE '$escapedPath' AS encrypted KEY \"x'$hexKey'\"")
                database.rawQuery("SELECT sqlcipher_export('encrypted')", emptyArray()).use { cursor ->
                    cursor.moveToFirst()
                }
                database.execSQL("DETACH DATABASE encrypted")
                database.close()
                database = null

                deleteDatabaseSidecars(databaseFile)
                backupFile.delete()
                check(databaseFile.renameTo(backupFile)) { "Could not back up plaintext database" }
                if (!encryptedFile.renameTo(databaseFile)) {
                    backupFile.renameTo(databaseFile)
                    error("Could not replace plaintext database with encrypted database")
                }
                backupFile.delete()
            } catch (error: Exception) {
                encryptedFile.delete()
                throw IllegalStateException("Failed to migrate plaintext database to SQLCipher", error)
            } finally {
                try {
                    database?.close()
                } catch (_: Exception) {
                }
            }
        }

        private fun isPlaintextSqliteDatabase(file: File): Boolean {
            if (!file.exists() || file.length() < SQLITE_HEADER.size) return false
            val header = ByteArray(SQLITE_HEADER.size)
            FileInputStream(file).use { input ->
                if (input.read(header) != SQLITE_HEADER.size) return false
            }
            return header.contentEquals(SQLITE_HEADER)
        }

        private fun isValidDatabase(file: File, hexKey: String): Boolean {
            var db: SQLiteDatabase? = null
            return try {
                db = SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    "x'$hexKey'".toByteArray(),
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                    null,
                    null
                )
                db != null
            } catch (_: Exception) {
                false
            } finally {
                db?.close()
            }
        }

        private fun deleteDatabaseWithSidecars(databaseFile: File) {
            databaseFile.delete()
            deleteDatabaseSidecars(databaseFile)
        }

        private fun deleteDatabaseSidecars(databaseFile: File) {
            val parent = databaseFile.parentFile ?: return
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                File(parent, databaseFile.name + suffix).delete()
            }
        }

        private fun bytesToHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        }
    }
}
