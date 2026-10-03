package app.workouttracker.settings

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Weights are stored as plain numbers in whichever unit is chosen here. */
enum class WeightUnit(val label: String, val step: Double) {
    LB("lb", 5.0),
    KG("kg", 2.5),
}

/** The chosen weight unit, kept in SharedPreferences. Defaults to lb. */
class WeightUnitSetting(context: Context) {
    private val prefs = context.getSharedPreferences("units", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(
        runCatching { WeightUnit.valueOf(prefs.getString("weight", null) ?: "") }.getOrDefault(WeightUnit.LB)
    )
    val unit: StateFlow<WeightUnit> = state

    fun set(unit: WeightUnit) {
        prefs.edit().putString("weight", unit.name).apply()
        state.value = unit
    }
}

val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.LB }
