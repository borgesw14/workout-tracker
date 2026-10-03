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
    @Query("SELECT * FROM WorkoutTemplate ORDER BY name")
    fun observeAll(): Flow<List<WorkoutTemplate>>

    @Query("SELECT * FROM TemplateExercise WHERE templateId = :templateId ORDER BY position")
    fun observeExercises(templateId: Long): Flow<List<TemplateExercise>>

    @Insert
    suspend fun insert(template: WorkoutTemplate): Long

    @Insert
    suspend fun insertExercises(items: List<TemplateExercise>)

    @Query("DELETE FROM TemplateExercise WHERE templateId = :templateId")
    suspend fun clearExercises(templateId: Long)

    @Delete
    suspend fun delete(template: WorkoutTemplate)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM ScheduledWorkout WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<ScheduledWorkout>>

    @Insert
    suspend fun insert(item: ScheduledWorkout): Long

    @Query("UPDATE ScheduledWorkout SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Delete
    suspend fun delete(item: ScheduledWorkout)
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
