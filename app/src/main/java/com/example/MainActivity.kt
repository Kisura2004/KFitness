package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.WorkoutViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: WorkoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                FitnessApp(viewModel = viewModel)
            }
        }
    }
}

enum class Screen(val title: String, val activeIcon: ImageVector, val inactiveIcon: ImageVector) {
    Dashboard("Planner", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    Calendar("Calendar", Icons.Filled.DateRange, Icons.Outlined.DateRange),
    Records("PRs", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
    Profile("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun FitnessApp(viewModel: WorkoutViewModel) {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.Dashboard) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar"),
                tonalElevation = 8.dp
            ) {
                Screen.values().forEach { screen ->
                    val selected = currentScreen == screen
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.activeIcon else screen.inactiveIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.testTag("nav_item_${screen.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    Screen.Dashboard -> DashboardScreen(viewModel = viewModel)
                    Screen.Calendar -> CalendarScreen(viewModel = viewModel)
                    Screen.Records -> PersonalRecordsScreen(viewModel = viewModel)
                    Screen.Profile -> ProfileScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// METRIC DATA MODELS & AUXILIARIES
// -------------------------------------------------------------
data class WeekDayInfo(
    val dayName: String,     // e.g. "Monday"
    val shortName: String,   // e.g. "Mon"
    val dateLabel: String,   // e.g. "Jun 15"
    val timestamp: Long,     // calendar mid-day mills to anchor logs
    val isToday: Boolean
)

private fun getCurrentWeekDays(): List<WeekDayInfo> {
    val days = mutableListOf<WeekDayInfo>()
    val sdfDay = SimpleDateFormat("EEEE", Locale.US)
    val sdfShort = SimpleDateFormat("E", Locale.US)
    val sdfDate = SimpleDateFormat("MMM d", Locale.US)
    
    val todayCal = Calendar.getInstance()
    val todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)
    val todayYear = todayCal.get(Calendar.YEAR)
    
    val cal = Calendar.getInstance()
    cal.firstDayOfWeek = Calendar.MONDAY
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    cal.set(Calendar.HOUR_OF_DAY, 12)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    
    for (i in 0..6) {
        val isToday = cal.get(Calendar.DAY_OF_YEAR) == todayDayOfYear && cal.get(Calendar.YEAR) == todayYear
        days.add(
            WeekDayInfo(
                dayName = sdfDay.format(cal.time),
                shortName = sdfShort.format(cal.time),
                dateLabel = sdfDate.format(cal.time),
                timestamp = cal.timeInMillis,
                isToday = isToday
            )
        )
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return days
}

private fun isLogOnDay(logTimestamp: Long, dayTimestamp: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = logTimestamp }
    val cal2 = Calendar.getInstance().apply { timeInMillis = dayTimestamp }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

// -------------------------------------------------------------
// SCREEN 1: DASHBOARD (Vertical Planner & Day Toggles)
// -------------------------------------------------------------
@Composable
fun DashboardScreen(viewModel: WorkoutViewModel) {
    val logs by viewModel.workoutLogs.collectAsStateWithLifecycle()
    val streak by viewModel.streakFlow.collectAsStateWithLifecycle()
    val latestWeightEntry by viewModel.latestWeight.collectAsStateWithLifecycle()
    
    // Customization selections from settings
    val setTrackerEnabled by viewModel.setTrackerEnabled.collectAsStateWithLifecycle()
    val repTrackerEnabled by viewModel.repTrackerEnabled.collectAsStateWithLifecycle()
    val timeTrackerEnabled by viewModel.timeTrackerEnabled.collectAsStateWithLifecycle()
    
    var expandedDayTimestamp by remember { mutableStateOf<Long?>(null) }
    
    val weekDays = remember { getCurrentWeekDays() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Track your physical progress",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Stats Row (Streak and Weight)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Streak Card
                Card(
                    modifier = Modifier
                        .weight(1.0f)
                        .testTag("dashboard_streak_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Workout Streak",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (streak == 1) "$streak Day" else "$streak Days",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Weight Card
                Card(
                    modifier = Modifier
                        .weight(1.0f)
                        .testTag("dashboard_weight_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Scale,
                                contentDescription = "Weight",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Current Weight",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val weightText = latestWeightEntry?.let { "${it.weight} kg" } ?: "Not set"
                        Text(
                            text = weightText,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Weekly Planner Column (Vertical Monday to Sunday)
        item {
            Text(
                text = "Weekly Workout Planner",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Tap a day below to record workouts completed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 7 Weekday rows rendered vertically
        items(weekDays) { day ->
            val dayLogs = logs.filter { isLogOnDay(it.timestamp, day.timestamp) }
            val isExpanded = expandedDayTimestamp == day.timestamp
            
            // Unique completed categories
            val completedCategories = dayLogs.map { it.category }.filter { it.isNotEmpty() }.distinct()

            var detailedExerciseName by rememberSaveable { mutableStateOf("") }
            var detailedCategory by rememberSaveable { mutableStateOf("Chest") }
            var detailedWeightText by rememberSaveable { mutableStateOf("") }
            var detailedSetsCount by rememberSaveable { mutableStateOf(3) }
            var detailedRepsCount by rememberSaveable { mutableStateOf(10) }
            var detailedNotesText by rememberSaveable { mutableStateOf("") }
            
            // Stopwatch/Timer local state
            var stopwatchSeconds by remember { mutableStateOf(0) }
            var stopwatchIsRunning by remember { mutableStateOf(false) }
            var detailedTimeText by rememberSaveable { mutableStateOf("") }

            LaunchedEffect(stopwatchIsRunning) {
                if (stopwatchIsRunning) {
                    while (true) {
                        kotlinx.coroutines.delay(1000)
                        stopwatchSeconds++
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        expandedDayTimestamp = if (isExpanded) null else day.timestamp 
                    }
                    .testTag("planner_day_card_${day.shortName.lowercase()}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (day.isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                     else MaterialTheme.colorScheme.surface
                ),
                border = if (day.isToday) CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                ) else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = day.dayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                if (day.isToday) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "TODAY",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = day.dateLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Completed labels or Rest Day
                        if (completedCategories.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = completedCategories.joinToString(", "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                text = "Rest Day",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand day tracker options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Content expander to toggle splits directly
                    if (isExpanded) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )

                        Text(
                            text = "Toggle Completed Muscle Groups / Splits:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )

                        val row1 = listOf("Chest", "Back", "Shoulders")
                        val row2 = listOf("Arms", "Biceps", "Triceps", "Forearms")
                        val row3 = listOf("Legs", "Core", "Cardio")

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row1.forEach { category ->
                                    val log = dayLogs.find { it.category.equals(category, ignoreCase = true) }
                                    val isSelected = log != null
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            )
                                            .clickable {
                                                if (isSelected && log != null) {
                                                    viewModel.deleteWorkoutLog(log.id)
                                                } else {
                                                    viewModel.addWorkoutLog(
                                                        exerciseName = "",
                                                        weight = 0.0,
                                                        reps = 0,
                                                        sets = 0,
                                                        notes = "",
                                                        category = category,
                                                        timestamp = day.timestamp
                                                    )
                                                }
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Logged",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = category,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row2.forEach { category ->
                                    val log = dayLogs.find { it.category.equals(category, ignoreCase = true) }
                                    val isSelected = log != null
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            )
                                            .clickable {
                                                if (isSelected && log != null) {
                                                    viewModel.deleteWorkoutLog(log.id)
                                                } else {
                                                    viewModel.addWorkoutLog(
                                                        exerciseName = "",
                                                        weight = 0.0,
                                                        reps = 0,
                                                        sets = 0,
                                                        notes = "",
                                                        category = category,
                                                        timestamp = day.timestamp
                                                    )
                                                }
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Logged",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = category,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row3.forEach { category ->
                                    val log = dayLogs.find { it.category.equals(category, ignoreCase = true) }
                                    val isSelected = log != null
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            )
                                            .clickable {
                                                if (isSelected && log != null) {
                                                    viewModel.deleteWorkoutLog(log.id)
                                                } else {
                                                    viewModel.addWorkoutLog(
                                                        exerciseName = "",
                                                        weight = 0.0,
                                                        reps = 0,
                                                        sets = 0,
                                                        notes = "",
                                                        category = category,
                                                        timestamp = day.timestamp
                                                    )
                                                }
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Logged",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = category,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // DETAILED RECORDING SECTION
                        val anyTrackerEnabled = setTrackerEnabled || repTrackerEnabled || timeTrackerEnabled
                        if (anyTrackerEnabled) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = "Detailed Sets Logs",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Recorded Exercises & Sets",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Filter dayLogs for detailed ones (exerciseName is not empty)
                            val detailedLogs = dayLogs.filter { it.exerciseName.isNotEmpty() }

                            if (detailedLogs.isEmpty()) {
                                Text(
                                    text = "No detailed sets logged for this day yet. Customize and log your sets below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    detailedLogs.forEach { dLog ->
                                        val (cleanNotes, parsedTime) = parseNotesAndTime(dLog.notes)
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = dLog.exerciseName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (dLog.category.isNotEmpty()) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = dLog.category.uppercase(),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                            }
                                                        }
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    
                                                    // Display logged metrics based on toggle permissions
                                                    val metricsList = mutableListOf<String>()
                                                    if (setTrackerEnabled && dLog.sets > 0) {
                                                        metricsList.add("${dLog.sets} Sets")
                                                    }
                                                    if (repTrackerEnabled && dLog.reps > 0) {
                                                        metricsList.add("${dLog.reps} Reps")
                                                    }
                                                    if (dLog.weight > 0.0) {
                                                        metricsList.add("${dLog.weight} kg")
                                                    }
                                                    if (timeTrackerEnabled && !parsedTime.isNullOrEmpty()) {
                                                        metricsList.add("⏱️ $parsedTime")
                                                    }
                                                    
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = if (metricsList.isNotEmpty()) metricsList.joinToString(" • ") else "Completed Split",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    
                                                    if (cleanNotes.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "\"$cleanNotes\"",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                        )
                                                    }
                                                }
                                                
                                                IconButton(
                                                    onClick = { viewModel.deleteWorkoutLog(dLog.id) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete exercise log",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FitnessCenter,
                                            contentDescription = "Log New Exercise Sets",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Record Set Details",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Exercise Name Input
                                    OutlinedTextField(
                                        value = detailedExerciseName,
                                        onValueChange = { detailedExerciseName = it },
                                        modifier = Modifier.fillMaxWidth().testTag("detailed_exercise_name_input_${day.shortName.lowercase()}"),
                                        label = { Text("Exercise (e.g., Squat, Bench Press)", style = MaterialTheme.typography.bodyMedium) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            focusedContainerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )

                                    // Category / Muscle split picker (Chips)
                                    Text(
                                        text = "Select Muscle Group / Split:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    val categoriesList = listOf("Chest", "Back", "Arms", "Biceps", "Triceps", "Forearms", "Legs", "Shoulders", "Core", "Cardio")
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(categoriesList) { cat ->
                                            val isSelected = detailedCategory.equals(cat, ignoreCase = true)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                    .clickable { detailedCategory = cat }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = cat,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Always show weight
                                        OutlinedTextField(
                                            value = detailedWeightText,
                                            onValueChange = { detailedWeightText = it },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Weight (kg)", style = MaterialTheme.typography.bodySmall) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                focusedContainerColor = MaterialTheme.colorScheme.surface
                                            )
                                        )

                                        // Render Set Selector / Stepper if enabled
                                        if (setTrackerEnabled) {
                                            Column(
                                                modifier = Modifier.weight(1.2f),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "Sets",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { if (detailedSetsCount > 1) detailedSetsCount-- },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease Sets", modifier = Modifier.size(16.dp))
                                                    }
                                                    Text(
                                                        text = detailedSetsCount.toString(),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    IconButton(
                                                        onClick = { detailedSetsCount++ },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase Sets", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }

                                        // Render Rep Selector / Stepper if enabled
                                        if (repTrackerEnabled) {
                                            Column(
                                                modifier = Modifier.weight(1.2f),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "Reps",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { if (detailedRepsCount > 1) detailedRepsCount-- },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease Reps", modifier = Modifier.size(16.dp))
                                                    }
                                                    Text(
                                                        text = detailedRepsCount.toString(),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    IconButton(
                                                        onClick = { detailedRepsCount++ },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase Reps", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Render Time Tracker Setup if enabled
                                    if (timeTrackerEnabled) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Set Duration / Rest Timer:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = detailedTimeText,
                                                    onValueChange = { detailedTimeText = it },
                                                    modifier = Modifier.weight(1f),
                                                    placeholder = { Text("e.g. 1m 30s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                                    label = { Text("Logged Time", style = MaterialTheme.typography.bodySmall) },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                        focusedContainerColor = MaterialTheme.colorScheme.surface
                                                    )
                                                )

                                                // Mini stopwatch layout
                                                Card(
                                                    modifier = Modifier.weight(1.5f),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        val formattedStopwatch = String.format("%02d:%02d", stopwatchSeconds / 60, stopwatchSeconds % 60)
                                                        Text(
                                                            text = formattedStopwatch,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                            color = if (stopwatchIsRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )

                                                        IconButton(
                                                            onClick = { stopwatchIsRunning = !stopwatchIsRunning },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (stopwatchIsRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                                contentDescription = "Start/Pause timer",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }

                                                        IconButton(
                                                            onClick = {
                                                                stopwatchSeconds = 0
                                                                stopwatchIsRunning = false
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Refresh,
                                                                contentDescription = "Reset timer",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }

                                                        Button(
                                                            onClick = {
                                                                detailedTimeText = formattedStopwatch
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(26.dp),
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text("Fill", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Additional Notes Input
                                    OutlinedTextField(
                                        value = detailedNotesText,
                                        onValueChange = { detailedNotesText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Exercise Notes (e.g., Felt strong, partial reps)", style = MaterialTheme.typography.bodyMedium) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            focusedContainerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )

                                    Button(
                                        onClick = {
                                            if (detailedExerciseName.trim().isNotEmpty()) {
                                                val finalNotes = if (timeTrackerEnabled && detailedTimeText.isNotBlank()) {
                                                    "[Time: ${detailedTimeText.trim()}] ${detailedNotesText.trim()}"
                                                } else {
                                                    detailedNotesText.trim()
                                                }

                                                viewModel.addWorkoutLog(
                                                    exerciseName = detailedExerciseName.trim(),
                                                    weight = detailedWeightText.toDoubleOrNull() ?: 0.0,
                                                    reps = if (repTrackerEnabled) detailedRepsCount else 0,
                                                    sets = if (setTrackerEnabled) detailedSetsCount else 0,
                                                    notes = finalNotes,
                                                    category = detailedCategory,
                                                    timestamp = day.timestamp
                                                )

                                                // Clear form fields
                                                detailedExerciseName = ""
                                                detailedWeightText = ""
                                                detailedNotesText = ""
                                                detailedTimeText = ""
                                                stopwatchSeconds = 0
                                                stopwatchIsRunning = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("add_detailed_workout_log_${day.shortName.lowercase()}"),
                                        enabled = detailedExerciseName.trim().isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Log", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Detailed Set Record", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// -------------------------------------------------------------
// SCREEN 3: PERSONAL RECORDS (PRs)
// -------------------------------------------------------------
@Composable
fun PersonalRecordsScreen(viewModel: WorkoutViewModel) {
    val prs by viewModel.personalRecords.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var newExerciseName by remember { mutableStateOf("") }
    var newPRWeightText by remember { mutableStateOf("") }
    var newRepsCount by remember { mutableStateOf("1") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Personal Milestones",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Highlighting your strongest achievements",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Quick Input form for records
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pr_input_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Add / Import historical Record",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = newExerciseName,
                        onValueChange = { newExerciseName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pr_exercise_input"),
                        label = { Text("Exercise (e.g. Bench Press)", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = newPRWeightText,
                            onValueChange = { newPRWeightText = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pr_weight_input"),
                            label = { Text("Max Weight (kg)", style = MaterialTheme.typography.bodyMedium) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = newRepsCount,
                            onValueChange = { newRepsCount = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pr_reps_input"),
                            label = { Text("Reps Completed", style = MaterialTheme.typography.bodyMedium) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Button(
                        onClick = {
                            val weight = newPRWeightText.toDoubleOrNull()
                            val reps = newRepsCount.toIntOrNull() ?: 1
                            if (newExerciseName.trim().isNotEmpty() && weight != null && weight > 0) {
                                viewModel.addPersonalRecord(newExerciseName.trim(), weight, reps)
                                newExerciseName = ""
                                newPRWeightText = ""
                                newRepsCount = "1"
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("save_pr_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Log Milestone Record", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Personal Records",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (prs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "No milestones",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No PR milestones recorded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(prs) { record ->
                PersonalRecordItem(record = record, onDelete = { viewModel.deletePersonalRecord(record.exerciseName) })
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PersonalRecordItem(record: PersonalRecord, onDelete: () -> Unit) {
    val dateText = remember(record.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(record.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pr_item_${record.exerciseName.lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.0f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = record.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${record.maxWeight} kg",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Completed ${record.reps} rep(s) • $dateText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_pr_${record.exerciseName.lowercase()}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete record",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 2: TRAINING CALENDAR
// -------------------------------------------------------------
data class CalendarDayInfo(
    val dayOfMonth: Int,
    val isFiller: Boolean,
    val timestamp: Long,
    val isToday: Boolean
)

private fun generateMonthDays(selectedMonth: Calendar): List<CalendarDayInfo> {
    val days = mutableListOf<CalendarDayInfo>()
    
    val cal = (selectedMonth.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    
    val todayCal = Calendar.getInstance()
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
    val todayMonth = todayCal.get(Calendar.MONTH)
    val todayYear = todayCal.get(Calendar.YEAR)
    
    val currentMonth = cal.get(Calendar.MONTH)
    val currentYear = cal.get(Calendar.YEAR)
    
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val fillerCount = (firstDayOfWeek - Calendar.MONDAY + 7) % 7
    
    for (i in 0 until fillerCount) {
        days.add(CalendarDayInfo(dayOfMonth = 0, isFiller = true, timestamp = 0L, isToday = false))
    }
    
    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    for (day in 1..maxDays) {
        cal.set(Calendar.DAY_OF_MONTH, day)
        val isToday = day == todayDay && currentMonth == todayMonth && currentYear == todayYear
        days.add(
            CalendarDayInfo(
                dayOfMonth = day,
                isFiller = false,
                timestamp = cal.timeInMillis,
                isToday = isToday
            )
        )
    }
    
    return days
}

@Composable
fun CalendarScreen(viewModel: WorkoutViewModel) {
    val logs by viewModel.workoutLogs.collectAsStateWithLifecycle()
    val streak by viewModel.streakFlow.collectAsStateWithLifecycle()
    
    var selectedMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDayForDetail by remember { mutableStateOf<CalendarDayInfo?>(null) }
    
    val monthDays = remember(selectedMonth) { generateMonthDays(selectedMonth) }
    
    val monthFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
    val monthLabel = remember(selectedMonth) { monthFormatter.format(selectedMonth.time) }
    
    val monthLogs = remember(logs, selectedMonth) {
        val currentMonth = selectedMonth.get(Calendar.MONTH)
        val currentYear = selectedMonth.get(Calendar.YEAR)
        val cal = Calendar.getInstance()
        logs.filter {
            cal.timeInMillis = it.timestamp
            cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
        }
    }
    
    val activeDaysCount = remember(monthLogs) {
        monthLogs.map { 
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }.distinct().size
    }
    
    val daysInMonth = remember(selectedMonth) {
        selectedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    
    val activeRatio = if (daysInMonth > 0) (activeDaysCount.toFloat() / daysInMonth * 100).toInt() else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Training Calendar",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "View month activity or toggle muscle group target entries directly",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                selectedMonth = (selectedMonth.clone() as Calendar).apply {
                                    add(Calendar.MONTH, -1)
                                }
                            },
                            modifier = Modifier.testTag("calendar_prev_month")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Month"
                            )
                        }
                        
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        
                        IconButton(
                            onClick = {
                                selectedMonth = (selectedMonth.clone() as Calendar).apply {
                                    add(Calendar.MONTH, 1)
                                }
                            },
                            modifier = Modifier.testTag("calendar_next_month")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowRight,
                                contentDescription = "Next Month"
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Active Days",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$activeDaysCount Day${if (activeDaysCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Column {
                            Text(
                                text = "Consistency",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$activeRatio%",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Column {
                            Text(
                                text = "Active Streak",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$streak Day${if (streak == 1) "" else "s"}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    val weekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekdayLabels.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val rowsCount = (monthDays.size + 6) / 7
                    for (rowIdx in 0 until rowsCount) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (colIdx in 0..6) {
                                val dayIdx = rowIdx * 7 + colIdx
                                if (dayIdx < monthDays.size) {
                                    val dayInfo = monthDays[dayIdx]
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!dayInfo.isFiller) {
                                            val dayLogs = logs.filter { isLogOnDay(it.timestamp, dayInfo.timestamp) }
                                            val isSelected = selectedDayForDetail?.timestamp == dayInfo.timestamp
                                            val targetedSplits = dayLogs.map { it.category }.filter { it.isNotEmpty() }.distinct()
                                            val hasWorkout = targetedSplits.isNotEmpty()
                                            
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        when {
                                                            isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                                            hasWorkout -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                            else -> Color.Transparent
                                                        }
                                                    )
                                                    .border(
                                                        width = if (dayInfo.isToday) 2.dp else 0.dp,
                                                        color = if (dayInfo.isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable {
                                                        selectedDayForDetail = if (isSelected) null else dayInfo
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = dayInfo.dayOfMonth.toString(),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (hasWorkout) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (hasWorkout || dayInfo.isToday) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                    
                                                    if (hasWorkout) {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            targetedSplits.take(3).forEach { _ ->
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(4.dp)
                                                                        .clip(CircleShape)
                                                                        .background(MaterialTheme.colorScheme.primary)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
        
        selectedDayForDetail?.let { dayInfo ->
            item {
                val dayLogs = logs.filter { isLogOnDay(it.timestamp, dayInfo.timestamp) }
                val sdf = SimpleDateFormat("EEEE, MMMM dd", Locale.US)
                val dayLabel = sdf.format(Date(dayInfo.timestamp))
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calendar_detail_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Training Splits",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { selectedDayForDetail = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close detailed view",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Text(
                            text = "Toggle splits completed on this date:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        val row1 = listOf("Chest", "Back", "Shoulders")
                        val row2 = listOf("Arms", "Biceps", "Triceps", "Forearms")
                        val row3 = listOf("Legs", "Core", "Cardio")
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row1.forEach { category ->
                                    val log = dayLogs.find { it.category.equals(category, ignoreCase = true) }
                                    val isSelected = log != null
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                            .clickable {
                                                if (isSelected && log != null) {
                                                    viewModel.deleteWorkoutLog(log.id)
                                                } else {
                                                    viewModel.addWorkoutLog(
                                                        exerciseName = "",
                                                        weight = 0.0,
                                                        reps = 0,
                                                        sets = 0,
                                                        notes = "",
                                                        category = category,
                                                        timestamp = dayInfo.timestamp
                                                    )
                                                }
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row2.forEach { category ->
                                    val log = dayLogs.find { it.category.equals(category, ignoreCase = true) }
                                    val isSelected = log != null
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                            .clickable {
                                                if (isSelected && log != null) {
                                                    viewModel.deleteWorkoutLog(log.id)
                                                } else {
                                                    viewModel.addWorkoutLog(
                                                        exerciseName = "",
                                                        weight = 0.0,
                                                        reps = 0,
                                                        sets = 0,
                                                        notes = "",
                                                        category = category,
                                                        timestamp = dayInfo.timestamp
                                                    )
                                                }
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row3.forEach { category ->
                                    val log = dayLogs.find { it.category.equals(category, ignoreCase = true) }
                                    val isSelected = log != null
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                            .clickable {
                                                if (isSelected && log != null) {
                                                    viewModel.deleteWorkoutLog(log.id)
                                                } else {
                                                    viewModel.addWorkoutLog(
                                                        exerciseName = "",
                                                        weight = 0.0,
                                                        reps = 0,
                                                        sets = 0,
                                                        notes = "",
                                                        category = category,
                                                        timestamp = dayInfo.timestamp
                                                    )
                                                }
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// -------------------------------------------------------------
// SCREEN 4: USER PROFILE & SETTINGS
// -------------------------------------------------------------
@Composable
fun ProfileScreen(viewModel: WorkoutViewModel) {
    val weightsAsc by viewModel.weightEntriesAsc.collectAsStateWithLifecycle()
    val latestWeightEntry by viewModel.latestWeight.collectAsStateWithLifecycle()
    val logs by viewModel.workoutLogs.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Gather toggled customized tracking permissions from VM
    val setTrackerEnabled by viewModel.setTrackerEnabled.collectAsStateWithLifecycle()
    val repTrackerEnabled by viewModel.repTrackerEnabled.collectAsStateWithLifecycle()
    val timeTrackerEnabled by viewModel.timeTrackerEnabled.collectAsStateWithLifecycle()

    // Local profile preferences
    var profileName by rememberSaveable { mutableStateOf("Kisura Wijeweera") }
    var heightText by rememberSaveable { mutableStateOf("178") }
    var userGoal by rememberSaveable { mutableStateOf("Muscle Gain & Consistency") }
    
    // Setting preferences
    var weightTargetText by rememberSaveable { mutableStateOf("75.0") }
    var selectedUnit by rememberSaveable { mutableStateOf("Metric (kg)") }
    var remindersEnabled by rememberSaveable { mutableStateOf(true) }
    var healthBridgeEnabled by rememberSaveable { mutableStateOf(false) }
    var workoutsTargetPerWeek by rememberSaveable { mutableStateOf(4) }
    
    // Weight logging field
    var newWeightVal by remember { mutableStateOf("") }
    
    // Filter active workout days this week
    val weekDays = remember { getCurrentWeekDays() }
    val activeDaysThisWeek = remember(logs) {
        weekDays.count { day ->
            logs.any { isLogOnDay(it.timestamp, day.timestamp) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Profile & Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Manage your biometric targets and application preferences",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Sleek User Card holding Avatar & core text info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Profile initials avatar
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = if (profileName.trim().isNotEmpty()) {
                            val parts = profileName.split(" ")
                            if (parts.size >= 2) {
                                "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
                            } else {
                                profileName.take(2).uppercase()
                            }
                        } else {
                            "KW"
                        }
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = profileName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = userGoal,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Height: $heightText cm • Target: $weightTargetText kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Setting Inputs & Fields
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Edit Profile Info",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_name_input"),
                        label = { Text("Display Name", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = userGoal,
                        onValueChange = { userGoal = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_goal_input"),
                        label = { Text("Fitness Goal Description", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_height_input"),
                            label = { Text("Height (cm)", style = MaterialTheme.typography.bodyMedium) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = weightTargetText,
                            onValueChange = { weightTargetText = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_target_weight_input"),
                            label = { Text("Target Weight (kg)", style = MaterialTheme.typography.bodyMedium) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }

        // 3. Weekly Workout Target Tracker (Stepper + Custom Progress Bar)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Weekly Practice Target",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target workouts per week",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (workoutsTargetPerWeek > 1) workoutsTargetPerWeek-- }) {
                                Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease")
                            }
                            Text(
                                text = workoutsTargetPerWeek.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { if (workoutsTargetPerWeek < 7) workoutsTargetPerWeek++ }) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = "Weekly Training Success:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    val progressFraction = if (workoutsTargetPerWeek > 0) {
                        (activeDaysThisWeek.toFloat() / workoutsTargetPerWeek).coerceAtMost(1f)
                    } else 0f

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )

                    Text(
                        text = "$activeDaysThisWeek of $workoutsTargetPerWeek days successfully logged this week!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3.5 Detailed Tracker Settings (Set, Rep, and Time Toggles)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("workout_tracker_customization_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Customize Workout Logger",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Toggle options below to customize the metrics you want to record for each exercise set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 1. Set Tracker toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ListAlt,
                                contentDescription = "Set Tracker",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Set Tracker",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Log the number of sets completed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = setTrackerEnabled,
                            onCheckedChange = { viewModel.toggleSetTracker() },
                            modifier = Modifier.testTag("toggle_set_tracker")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // 2. Rep Tracker toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = "Rep Tracker",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Rep Tracker",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Log repetitions completed per set",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = repTrackerEnabled,
                            onCheckedChange = { viewModel.toggleRepTracker() },
                            modifier = Modifier.testTag("toggle_rep_tracker")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // 3. Time Tracker toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Time Tracker",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Time Tracker",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Record time duration / elapsed rest spent",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = timeTrackerEnabled,
                            onCheckedChange = { viewModel.toggleTimeTracker() },
                            modifier = Modifier.testTag("toggle_time_tracker")
                        )
                    }
                }
            }
        }

        // 4. App Preferences (KG/LBS unit selection, Toggles)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "App Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )

                    // Units Selector Row (KG vs LBS)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Measurement Units",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(2.dp)
                        ) {
                            listOf("KG (Metric)", "LBS (Imperial)").forEach { unit ->
                                val isSelected = if (unit.startsWith("KG")) selectedUnit.startsWith("Metric") else selectedUnit.startsWith("Imperial")
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { selectedUnit = if (unit.startsWith("KG")) "Metric (kg)" else "Imperial (lbs)" }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (unit.startsWith("KG")) "KG" else "LBS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Reminders toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Daily reminders",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Receive consistency notifications",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = remindersEnabled,
                            onCheckedChange = { remindersEnabled = it }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Sync integration toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Google Fit Integration",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Bridge workouts and biometrics",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = healthBridgeEnabled,
                            onCheckedChange = { healthBridgeEnabled = it }
                        )
                    }
                }
            }
        }

        // 5. BODY WEIGHT PROGRESS (Incorporates original tracker logic flawlessly!)
        item {
            Text(
                text = "Body Weight Check-Ins",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weight_log_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Log Current Weight",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newWeightVal,
                            onValueChange = { newWeightVal = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("weight_tracker_input"),
                            placeholder = { Text("e.g. 84.5", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                val weight = newWeightVal.toDoubleOrNull()
                                if (weight != null && weight > 0) {
                                    viewModel.addWeightEntry(weight)
                                    newWeightVal = ""
                                    focusManager.clearFocus()
                                }
                            },
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("save_weight_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Record")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Record", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Body weight over time (kg)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (weightsAsc.size < 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Log at least 2 weight entry logs to view progress trend.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        WeightTrendGraph(entries = weightsAsc)
                    }
                }
            }
        }

        item {
            Text(
                text = "biometric History Records",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        val sortedWeightsDesc = weightsAsc.sortedByDescending { it.timestamp }
        if (sortedWeightsDesc.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "No logs",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No weight history records found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(sortedWeightsDesc) { entry ->
                val dateStr = remember(entry.timestamp) {
                    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                    sdf.format(Date(entry.timestamp))
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weight_item_${entry.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${entry.weight} kg",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteWeightEntry(entry.id) },
                            modifier = Modifier.testTag("delete_weight_${entry.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove weight entry",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Fitness Tracker",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun WeightTrendGraph(entries: List<WeightEntry>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary

    val weights = entries.map { it.weight.toFloat() }
    val maxWeight = (weights.maxOrNull() ?: 100f) + 1.5f
    val minWeight = (weights.minOrNull() ?: 50f) - 1.5f
    val weightRange = maxWeight - minWeight

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .testTag("weight_trend_canvas")
    ) {
        val width = size.width
        val height = size.height

        val stepX = width / (entries.size - 1)
        val points = entries.mapIndexed { idx, entry ->
            val x = idx * stepX
            val yFactor = (entry.weight.toFloat() - minWeight) / weightRange
            // Flip the Y index since 0,0 is at the top left of the canvas
            val y = height - (yFactor * height)
            Offset(x, y)
        }

        // Draw helper horizontal lines for scale representation
        val gridLinesCount = 4
        for (i in 0..gridLinesCount) {
            val gridY = height * i / gridLinesCount
            drawLine(
                color = Color.LightGray.copy(alpha = 0.2f),
                start = Offset(0f, gridY),
                end = Offset(width, gridY),
                strokeWidth = 1f
            )
        }

        // Draw beautiful smooth gradient content behind the line path
        val fillPath = Path().apply {
            moveTo(0f, height)
            points.forEach { offset ->
                lineTo(offset.x, offset.y)
            }
            lineTo(width, height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        // Draw main trend path line
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }

        drawPath(
            path = linePath,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw interactive nodes / points on line graph vertices
        points.forEach { point ->
            drawCircle(
                color = accentColor,
                radius = 5.dp.toPx(),
                center = point
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = point
            )
        }
    }
}

// -------------------------------------------------------------
// HELPER METHODS
// -------------------------------------------------------------
/**
 * Computes calendar weekday workout consistency mapping.
 */
private fun getWorkoutDaysThisWeek(logs: List<WorkoutLog>): Map<String, Boolean> {
    val results = mutableMapOf(
        "Mon" to false,
        "Tue" to false,
        "Wed" to false,
        "Thu" to false,
        "Fri" to false,
        "Sat" to false,
        "Sun" to false
    )

    val sdf = SimpleDateFormat("E", Locale.US)
    sdf.timeZone = TimeZone.getDefault()

    val calendar = Calendar.getInstance()
    val currentWeekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)
    val currentYear = calendar.get(Calendar.YEAR)

    logs.forEach { log ->
        calendar.timeInMillis = log.timestamp
        val logWeek = calendar.get(Calendar.WEEK_OF_YEAR)
        val logYear = calendar.get(Calendar.YEAR)

        if (logWeek == currentWeekOfYear && logYear == currentYear) {
            val dayName = sdf.format(Date(log.timestamp)) // e.g. "Mon", "Tue"
            if (results.containsKey(dayName)) {
                results[dayName] = true
            }
        }
    }

    return results
}

/**
 * Parses embedded time duration and clean user notes from the notes field.
 */
fun parseNotesAndTime(notes: String): Pair<String, String?> {
    return if (notes.startsWith("[Time: ") && notes.contains("] ")) {
        val timeEndIndex = notes.indexOf("] ")
        if (timeEndIndex != -1) {
            val timePart = notes.substring(7, timeEndIndex)
            val restPart = notes.substring(timeEndIndex + 2)
            Pair(restPart, timePart)
        } else {
            Pair(notes, null)
        }
    } else if (notes.startsWith("[Time: ") && notes.endsWith("]")) {
        val timePart = notes.substring(7, notes.length - 1)
        Pair("", timePart)
    } else {
        Pair(notes, null)
    }
}
