package app.workouttracker.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Each statement must match Room's generated schema exactly; CI checks this after every build.

/** v2 adds SessionExercise, the exercises in a workout session. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `SessionExercise` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `targetSets` INTEGER, `targetReps` INTEGER, `targetWeight` REAL, FOREIGN KEY(`sessionId`) REFERENCES `WorkoutSession`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `Exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_SessionExercise_sessionId` ON `SessionExercise` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_SessionExercise_exerciseId` ON `SessionExercise` (`exerciseId`)")
    }
}
