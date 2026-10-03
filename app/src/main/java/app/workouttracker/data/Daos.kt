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

    @Query("DELETE FROM ScheduledWorkout WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM WorkoutSession ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM SetEntry WHERE sessionId = :sessionId ORDER BY loggedAt")
    fun observeSets(sessionId: Long): Flow<List<SetEntry>>

    @Query("SELECT * FROM SetEntry WHERE exerciseId = :exerciseId ORDER BY loggedAt")
    fun observeSetsForExercise(exerciseId: Long): Flow<List<SetEntry>>

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession)

    @Insert
    suspend fun insertSet(set: SetEntry): Long

    @Delete
    suspend fun deleteSet(set: SetEntry)
}
