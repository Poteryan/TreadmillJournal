package ru.treadmill.journal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("treadmill_journal", MODE_PRIVATE)
        setContent { JournalApp(prefs) }
    }
}

data class Workout(
    val id: Long,
    val time: Long,
    val speed: Double,
    val minutes: Int,
    val distance: Double,
    val calories: Int
)

@Composable
fun JournalApp(prefs: android.content.SharedPreferences) {
    var weight by remember { mutableStateOf(prefs.getString("weight", "") ?: "") }
    var height by remember { mutableStateOf(prefs.getString("height", "") ?: "") }
    var age by remember { mutableStateOf(prefs.getString("age", "") ?: "") }
    var sex by remember { mutableStateOf(prefs.getString("sex", "Мужской") ?: "Мужской") }
    var workouts by remember { mutableStateOf(loadWorkouts(prefs)) }
    var speed by remember { mutableStateOf("5.5") }
    var minutes by remember { mutableStateOf("20") }
    var showProfile by remember { mutableStateOf(false) }
    var showPast by remember { mutableStateOf(false) }
    var editWorkout by remember { mutableStateOf<Workout?>(null) }
    var error by remember { mutableStateOf("") }

    val bodyWeight = weight.toDoubleOrNull() ?: 0.0
    val totalCalories = workouts.sumOf { it.calories }
    val totalMinutes = workouts.sumOf { it.minutes }
    val totalDistance = workouts.sumOf { it.distance }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF8BD5B2),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF073B2A),
            background = androidx.compose.ui.graphics.Color(0xFF101A1D),
            surface = androidx.compose.ui.graphics.Color(0xFF182629),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF26383C),
            onSurface = androidx.compose.ui.graphics.Color(0xFFE8F0EE),
            onBackground = androidx.compose.ui.graphics.Color(0xFFE8F0EE)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { showPast = true })
                        }
                    ) {
                        Text("МОЯ ДОРОЖКА", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Журнал ходьбы", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showProfile = true }) {
                        Icon(Icons.Default.Person, contentDescription = "Профиль", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("ТВОЯ АКТИВНОСТЬ", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("$totalCalories", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text("ккал за всё время", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("Тренировки", workouts.size.toString(), Modifier.weight(1f))
                            StatTile("Время", formatDuration(totalMinutes), Modifier.weight(1f))
                            StatTile("Расстояние", String.format(Locale.US, "%.1f км", totalDistance), Modifier.weight(1f))
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Новая тренировка", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Введи среднюю скорость и продолжительность. Дата и время сохранятся автоматически.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NumberField("Скорость", speed, { speed = it }, "км/ч", Modifier.weight(1f))
                            NumberField("Время", minutes, { minutes = it }, "мин", Modifier.weight(1f), integer = true)
                        }
                        if (bodyWeight > 0 && (speed.toDoubleOrNull() ?: 0.0) > 0 && (minutes.toIntOrNull() ?: 0) > 0) {
                            val estimate = estimateCalories(bodyWeight, speed.toDouble(), minutes.toInt())
                            Text("Предварительно: около $estimate ккал · ${String.format(Locale.US, "%.2f", speed.toDouble() * minutes.toInt() / 60.0)} км", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Укажи вес в профиле, чтобы рассчитать калории.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), fontSize = 13.sp)
                        }
                        Button(
                            onClick = {
                                val s = speed.toDoubleOrNull()
                                val m = minutes.toIntOrNull()
                                if (bodyWeight <= 0 || s == null || m == null || s <= 0 || s > 20 || m <= 0 || m > 600) {
                                    error = "Проверь вес в профиле, скорость и продолжительность."
                                } else {
                                    val w = makeWorkout(System.currentTimeMillis(), s, m, bodyWeight)
                                    workouts = (workouts + w).sortedByDescending { it.time }
                                    saveWorkouts(prefs, workouts)
                                    error = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ЗАПИСАТЬ ТРЕНИРОВКУ", fontWeight = FontWeight.Bold)
                        }
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("История", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${workouts.size} записей", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), fontSize = 12.sp)
                }
                if (workouts.isEmpty()) {
                    Text("Здесь появятся твои тренировки. Первая запись займёт пару секунд.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                } else {
                    workouts.take(30).forEach { workout ->
                        WorkoutRow(workout, onClick = { editWorkout = workout })
                    }
                    if (workouts.size > 30) Text("Показаны последние 30 тренировок.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), fontSize = 12.sp)
                }
                Text("Калории рассчитываются приблизительно по скорости, длительности и весу.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f), fontSize = 11.sp)
            }
        }

        if (showProfile) {
            AlertDialog(
                onDismissRequest = { showProfile = false },
                title = { Text("Профиль") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Вес", weight, { weight = it }, "кг")
                        NumberField("Рост", height, { height = it }, "см", integer = true)
                        NumberField("Возраст", age, { age = it }, "лет", integer = true)
                        Text("Пол")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = sex == "Мужской", onClick = { sex = "Мужской" }, label = { Text("Мужской") })
                            FilterChip(selected = sex == "Женский", onClick = { sex = "Женский" }, label = { Text("Женский") })
                        }
                        Text("Вес используется в расчёте калорий. Рост, возраст и пол сохраняются в профиле.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit().putString("weight", weight).putString("height", height).putString("age", age).putString("sex", sex).apply()
                        showProfile = false
                    }) { Text("Сохранить") }
                },
                dismissButton = { TextButton(onClick = { showProfile = false }) { Text("Отмена") } }
            )
        }

        if (showPast) {
            PastWorkoutDialog(
                currentWeight = bodyWeight,
                onDismiss = { showPast = false },
                onSave = { dateTime, s, m ->
                    if (bodyWeight > 0) {
                        workouts = (workouts + makeWorkout(dateTime, s, m, bodyWeight)).sortedByDescending { it.time }
                        saveWorkouts(prefs, workouts)
                        showPast = false
                    }
                }
            )
        }

        editWorkout?.let { selected ->
            EditWorkoutDialog(
                workout = selected,
                currentWeight = bodyWeight,
                onDismiss = { editWorkout = null },
                onDelete = {
                    workouts = workouts.filterNot { it.id == selected.id }
                    saveWorkouts(prefs, workouts)
                    editWorkout = null
                },
                onSave = { updated ->
                    workouts = workouts.map { if (it.id == selected.id) updated else it }.sortedByDescending { it.time }
                    saveWorkouts(prefs, workouts)
                    editWorkout = null
                }
            )
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(13.dp)).padding(10.dp)) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
    }
}

@Composable
fun NumberField(label: String, value: String, onValue: (String) -> Unit, suffix: String, modifier: Modifier = Modifier, integer: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValue, modifier = modifier,
        label = { Text(label) }, suffix = { Text(suffix) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal)
    )
}

@Composable
fun WorkoutRow(workout: Workout, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatDate(workout.time), fontWeight = FontWeight.SemiBold)
                Text("${workout.speed} км/ч · ${workout.minutes} мин · ${String.format(Locale.US, "%.2f", workout.distance)} км", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${workout.calories} ккал", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(formatTime(workout.time), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            }
        }
    }
}

@Composable
fun PastWorkoutDialog(currentWeight: Double, onDismiss: () -> Unit, onSave: (Long, Double, Int) -> Unit) {
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var time by remember { mutableStateOf("22:30") }
    var speed by remember { mutableStateOf("5.5") }
    var minutes by remember { mutableStateOf("20") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить тренировку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Дата и время", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(date, { date = it }, label = { Text("Дата (ГГГГ-ММ-ДД)") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("Время (ЧЧ:ММ)") }, singleLine = true)
                NumberField("Средняя скорость", speed, { speed = it }, "км/ч")
                NumberField("Продолжительность", minutes, { minutes = it }, "мин", integer = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                if (currentWeight <= 0) Text("Сначала укажи вес в профиле.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
                    val timestamp = parser.parse("$date $time")?.time
                    val s = speed.toDoubleOrNull()
                    val m = minutes.toIntOrNull()
                    if (timestamp == null || s == null || m == null || s <= 0 || s > 20 || m <= 0 || m > 600 || currentWeight <= 0) {
                        error = "Проверь дату, время и параметры тренировки."
                    } else onSave(timestamp, s, m)
                } catch (_: Exception) { error = "Неверный формат даты или времени." }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun EditWorkoutDialog(workout: Workout, currentWeight: Double, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Workout) -> Unit) {
    var speed by remember { mutableStateOf(workout.speed.toString()) }
    var minutes by remember { mutableStateOf(workout.minutes.toString()) }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(workout.time))) }
    var time by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.US).format(Date(workout.time))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Тренировка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(date, { date = it }, label = { Text("Дата (ГГГГ-ММ-ДД)") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("Время (ЧЧ:ММ)") }, singleLine = true)
                NumberField("Скорость", speed, { speed = it }, "км/ч")
                NumberField("Продолжительность", minutes, { minutes = it }, "мин", integer = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }.parse("$date $time")?.time
                    val s = speed.toDoubleOrNull()
                    val m = minutes.toIntOrNull()
                    if (timestamp != null && s != null && m != null && s > 0 && s <= 20 && m > 0 && m <= 600 && currentWeight > 0) {
                        onSave(makeWorkout(timestamp, s, m, currentWeight, workout.id))
                    }
                } catch (_: Exception) {}
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}

fun makeWorkout(time: Long, speed: Double, minutes: Int, weight: Double, id: Long = System.nanoTime()): Workout {
    val distance = speed * minutes / 60.0
    return Workout(id, time, speed, minutes, distance, estimateCalories(weight, speed, minutes))
}

// Approximate MET bands for level walking / very brisk walking. Not a medical measurement.
fun estimateCalories(weight: Double, speed: Double, minutes: Int): Int {
    val met = when {
        speed < 3.2 -> 2.0
        speed < 4.0 -> 2.8
        speed < 4.8 -> 3.5
        speed < 5.6 -> 4.3
        speed < 6.4 -> 5.0
        speed < 7.2 -> 6.3
        speed < 8.0 -> 7.5
        else -> 8.3
    }
    return ((met * 3.5 * weight / 200.0) * minutes).roundToInt()
}

fun saveWorkouts(prefs: android.content.SharedPreferences, workouts: List<Workout>) {
    val array = JSONArray()
    workouts.forEach {
        array.put(JSONObject().put("id", it.id).put("time", it.time).put("speed", it.speed)
            .put("minutes", it.minutes).put("distance", it.distance).put("calories", it.calories))
    }
    prefs.edit().putString("workouts", array.toString()).apply()
}

fun loadWorkouts(prefs: android.content.SharedPreferences): List<Workout> {
    return try {
        val array = JSONArray(prefs.getString("workouts", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Workout(o.getLong("id"), o.getLong("time"), o.getDouble("speed"), o.getInt("minutes"), o.getDouble("distance"), o.getInt("calories"))
        }.sortedByDescending { it.time }
    } catch (_: Exception) { emptyList() }
}

fun formatDate(time: Long) = SimpleDateFormat("d MMM yyyy", Locale("ru")).format(Date(time))
fun formatTime(time: Long) = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(time))
fun formatDuration(total: Int): String = if (total < 60) "$total мин" else "${total / 60} ч ${total % 60} мин"
