package app.workouttracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import app.workouttracker.MainActivity
import app.workouttracker.WorkoutApp
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess
import org.json.JSONObject

/**
 * A backup is a zip holding a copy of the database plus the settings that change how its
 * numbers read (weight unit, rest timer). Restoring replaces everything on this phone.
 */
object Backup {
    private const val DB_ENTRY = "workouts.db"
    private const val SETTINGS_ENTRY = "settings.json"
    private const val DB_VERSION = 2 // Keep in step with @Database(version) in WorkoutDatabase.

    class InvalidBackup(message: String) : IOException(message)

    /** Writes a backup of all data to [uri]. Call off the main thread. */
    fun export(context: Context, uri: Uri) {
        val app = context.applicationContext as WorkoutApp
        // Fold the write-ahead log into the main file so the copy is complete.
        app.database.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).close()
        val dbFile = context.getDatabasePath(WorkoutDatabase.NAME)
        val settings = JSONObject()
            .put("weightUnit", app.weightUnit.unit.value.name)
            .put("restSeconds", context.getSharedPreferences("logging", Context.MODE_PRIVATE).getInt("rest_seconds", 90))

        val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Couldn't open the file")
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(DB_ENTRY))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
            zip.write(settings.toString().toByteArray())
            zip.closeEntry()
        }
    }

    /**
     * Checks the backup at [uri], then replaces this phone's data with it and restarts the app.
     * Throws [InvalidBackup] without changing anything if the file isn't a usable backup.
     * Call off the main thread.
     */
    fun restore(context: Context, uri: Uri) {
        val staged = File(context.cacheDir, "restore.db")
        var settings: JSONObject? = null
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Couldn't open the file")
        try {
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    when (entry.name) {
                        DB_ENTRY -> staged.outputStream().use { zip.copyTo(it) }
                        SETTINGS_ENTRY -> settings = JSONObject(zip.readBytes().decodeToString())
                    }
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw InvalidBackup("That file isn't a Workout Tracker backup.")
        }
        checkDatabase(staged)

        val app = context.applicationContext as WorkoutApp
        app.database.close()
        val dbFile = context.getDatabasePath(WorkoutDatabase.NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        staged.copyTo(dbFile, overwrite = true)
        staged.delete()

        settings?.let { s ->
            s.optString("weightUnit").takeIf { it.isNotEmpty() }?.let { name ->
                context.getSharedPreferences("units", Context.MODE_PRIVATE).edit().putString("weight", name).commit()
            }
            if (s.has("restSeconds")) {
                context.getSharedPreferences("logging", Context.MODE_PRIVATE).edit()
                    .putInt("rest_seconds", s.getInt("restSeconds")).commit()
            }
        }

        // Start fresh so the restored database is opened (and upgraded, if it's older) cleanly.
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        exitProcess(0)
    }

    /** The file must be an SQLite database from this app, no newer than this version of it. */
    private fun checkDatabase(file: File) {
        if (!file.exists() || file.length() < 100) {
            file.delete()
            throw InvalidBackup("That file isn't a Workout Tracker backup.")
        }
        val header = ByteArray(100)
        file.inputStream().use { it.read(header) }
        if (!header.copyOfRange(0, 16).decodeToString().startsWith("SQLite format 3")) {
            file.delete()
            throw InvalidBackup("That file isn't a Workout Tracker backup.")
        }
        // SQLite keeps user_version, which Room uses for the schema version, at byte 60.
        val version = ((header[60].toInt() and 0xff) shl 24) or ((header[61].toInt() and 0xff) shl 16) or
            ((header[62].toInt() and 0xff) shl 8) or (header[63].toInt() and 0xff)
        if (version > DB_VERSION) {
            file.delete()
            throw InvalidBackup("This backup came from a newer version of the app. Update the app on this phone first.")
        }
    }
}
