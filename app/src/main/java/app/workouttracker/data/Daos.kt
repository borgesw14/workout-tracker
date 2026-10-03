package app.workouttracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM Exercise ORDER BY category, name")
    fun observeAll(): Flow<List<Exercise>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)
}

@Dao
interface TemplateDao {
    @Query(
        """
        SELECT t.id, t.name, t.notes,
            (SELECT COUNT(*) FROM TemplateExercise te WHERE te.templateId = t.id) AS exerciseCount
        FROM WorkoutTemplate t ORDER BY t.name COLLATE NOCASE
        """
    )
    fun observeSummaries(): Flow<List<TemplateSummary>>

    @Query("SELECT * FROM WorkoutTemplate WHERE id = :id")
    suspend fun get(id: Long): WorkoutTemplate?

    @Query(
        """
        SELECT te.exerciseId, e.name AS exerciseName, te.targetSets, te.targetReps, te.targetWeight
        FROM TemplateExercise te JOIN Exercise e ON e.id = te.exerciseId
        WHERE te.templateId = :templateId ORDER BY te.position
        """
    )
    suspend fun exercisesFor(templateId: Long): List<TemplateExerciseDetail>

    @Insert
    suspend fun insert(template: WorkoutTemplate): Long

    @Update
    suspend fun update(template: WorkoutTemplate)

    @Insert
    suspend fun insertExercises(items: List<TemplateExercise>)

    @Query("DELETE FROM TemplateExercise WHERE templateId = :templateId")
    suspend fun clearExercises(templateId: Long)

    @Query("DELETE FROM WorkoutTemplate WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ScheduleDao {
    @Query(
        """
        SELECT s.id, s.templateId, s.epochDay, s.status, t.name AS templateName
        FROM ScheduledWorkout s JOIN WorkoutTemplate t ON t.id = s.templateId
        WHERE s.epochDay BETWEEN :fromDay AND :toDay
        ORDER BY s.epochDay, t.name COLLATE NOCASE
        """
    )
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<ScheduledItem>>

    @Query(
        """
        SELECT s.id, s.templateId, s.epochDay, s.status, t.name AS templateName
        FROM ScheduledWorkout s JOIN WorkoutTemplate t ON t.id = s.templateId
        WHERE s.epochDay = :day AND s.status = 'PLANNED'
        ORDER BY t.name COLLATE NOCASE
        """
    )
    suspend fun plannedOn(day: Long): List<ScheduledItem>

    @Insert
    suspend fun insertAll(items: List<ScheduledWorkout>)

    @Query("UPDATE ScheduledWorkout SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE ScheduledWorkout SET status = 'PLANNED' WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM ScheduledWorkout WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM WorkoutSession WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutSession?>

    @Query("SELECT * FROM WorkoutSession WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutSession?>

    @Query("SELECT * FROM WorkoutSession WHERE id = :id")
    suspend fun get(id: Long): WorkoutSession?

    @Query(
        """
        SELECT s.id, s.name, s.startedAt, s.finishedAt,
            COUNT(e.id) AS setCount, COALESCE(SUM(e.weight * e.reps), 0) AS volume
        FROM WorkoutSession s LEFT JOIN SetEntry e ON e.sessionId = s.id
        WHERE s.finishedAt IS NOT NULL
        GROUP BY s.id ORDER BY s.startedAt DESC LIMIT 100
        """
    )
    fun observeHistory(): Flow<List<SessionSummary>>

    @Query(
        """
        SELECT se.id, se.exerciseId, e.name AS exerciseName, se.position,
            se.targetSets, se.targetReps, se.targetWeight
        FROM SessionExercise se JOIN Exercise e ON e.id = se.exerciseId
        WHERE se.sessionId = :sessionId ORDER BY se.position
        """
    )
    fun observeExercises(sessionId: Long): Flow<List<SessionExerciseDetail>>

    @Query("SELECT * FROM SetEntry WHERE sessionId = :sessionId ORDER BY setNumber")
    fun observeSets(sessionId: Long): Flow<List<SetEntry>>

    /** The sets from the most recent other session that included this exercise. */
    @Query(
        """
        SELECT * FROM SetEntry WHERE exerciseId = :exerciseId AND sessionId = (
            SELECT sessionId FROM SetEntry
            WHERE exerciseId = :exerciseId AND sessionId != :excludeSessionId
            ORDER BY loggedAt DESC LIMIT 1
        ) ORDER BY setNumber
        """
    )
    suspend fun lastTimeSets(exerciseId: Long, excludeSessionId: Long): List<SetEntry>

    @Query("SELECT COALESCE(MAX(position), -1) FROM SessionExercise WHERE sessionId = :sessionId")
    suspend fun maxPosition(sessionId: Long): Int

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession)

    @Query("DELETE FROM WorkoutSession WHERE id = :id")
    suspend fun delete(id: Long)

    /** Deletes every logged workout; their sets and exercises go with them. */
    @Query("DELETE FROM WorkoutSession")
    suspend fun deleteAll()

    @Insert
    suspend fun insertExercises(items: List<SessionExercise>)

    @Query("DELETE FROM SessionExercise WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Insert
    suspend fun insertSet(set: SetEntry): Long

    @Query("DELETE FROM SetEntry WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("UPDATE SetEntry SET setNumber = :setNumber WHERE id = :id")
    suspend fun renumberSet(id: Long, setNumber: Int)

    @Query("SELECT * FROM SetEntry WHERE sessionId = :sessionId AND exerciseId = :exerciseId ORDER BY setNumber")
    suspend fun setsFor(sessionId: Long, exerciseId: Long): List<SetEntry>
}
