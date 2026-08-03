package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.example.data.MealLog
import com.example.viewmodel.WorkoutViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealsScreen(viewModel: WorkoutViewModel) {
    val mealLogs by viewModel.mealLogs.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingSuggestions.collectAsStateWithLifecycle()
    val suggestedText by viewModel.suggestedMeals.collectAsStateWithLifecycle()
    val suggestionError by viewModel.suggestionError.collectAsStateWithLifecycle()
    
    val profileGoal by viewModel.profileGoal.collectAsStateWithLifecycle()
    
    // Observed targets from database/shared preferences
    val targetCalories by viewModel.calorieGoal.collectAsStateWithLifecycle()
    val targetProtein by viewModel.proteinGoal.collectAsStateWithLifecycle()
    val targetCarbs by viewModel.carbsGoal.collectAsStateWithLifecycle()
    val targetFats by viewModel.fatsGoal.collectAsStateWithLifecycle()

    // Observed Llama settings
    val currentProvider by viewModel.llmProvider.collectAsStateWithLifecycle()
    val currentBaseUrl by viewModel.llmBaseUrl.collectAsStateWithLifecycle()
    val currentModel by viewModel.llmModel.collectAsStateWithLifecycle()
    val currentApiKey by viewModel.llmApiKey.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    
    // UI Local state for meal dialog fields
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var mealName by rememberSaveable { mutableStateOf("") }
    var caloriesStr by rememberSaveable { mutableStateOf("") }
    var proteinStr by rememberSaveable { mutableStateOf("") }
    var carbsStr by rememberSaveable { mutableStateOf("") }
    var fatsStr by rememberSaveable { mutableStateOf("") }
    var selectedMealType by rememberSaveable { mutableStateOf("Breakfast") }

    // UI Local state for calorie & macro target settings
    var showEditTargetsDialog by rememberSaveable { mutableStateOf(false) }
    var editCalorieGoalStr by rememberSaveable { mutableStateOf("") }
    var editProteinGoalStr by rememberSaveable { mutableStateOf("") }
    var editCarbsGoalStr by rememberSaveable { mutableStateOf("") }
    var editFatsGoalStr by rememberSaveable { mutableStateOf("") }
    
    // Goal-Based Pre-built Meal Plans state
    val defaultGoalTab = remember(profileGoal) {
        when {
            profileGoal.contains("Gain", ignoreCase = true) -> "Muscle Gain"
            profileGoal.contains("Loss", ignoreCase = true) || profileGoal.contains("Fat", ignoreCase = true) -> "Fat Loss"
            else -> "Balanced Nutrition"
        }
    }
    var selectedGoalTab by remember(defaultGoalTab) { mutableStateOf(defaultGoalTab) }
    var selectedRegion by rememberSaveable { mutableStateOf("Global") }

    var lastLoggedMealName by remember { mutableStateOf<String?>(null) }
    var lastLoggedMealType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lastLoggedMealName) {
        if (lastLoggedMealName != null) {
            delay(3000)
            lastLoggedMealName = null
            lastLoggedMealType = null
        }
    }

    // Filter today's logged meals
    val todayStartMillis = remember {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }
    
    val todayMeals = remember(mealLogs) {
        mealLogs.filter { it.timestamp >= todayStartMillis }
    }
    
    // Nutrition summary metrics
    val totalCalories = todayMeals.sumOf { it.calories }
    val totalProtein = todayMeals.sumOf { it.protein }
    val totalCarbs = todayMeals.sumOf { it.carbs }
    val totalFats = todayMeals.sumOf { it.fats }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("meals_screen_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. HERO HEADER - Dynamic Daily Progress Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("meals_progress_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Today's Intake",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val formatter = remember { SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()) }
                            Text(
                                text = formatter.format(Date()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Day tracker",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Daily Tracker",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Main Calories Number Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "$totalCalories",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            
                            // Editable daily calorie target display
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        editCalorieGoalStr = targetCalories.toString()
                                        editProteinGoalStr = targetProtein.toInt().toString()
                                        editCarbsGoalStr = targetCarbs.toInt().toString()
                                        editFatsGoalStr = targetFats.toInt().toString()
                                        showEditTargetsDialog = true
                                    }
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("edit_calorie_target_button")
                            ) {
                                Text(
                                    text = "/ $targetCalories kcal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Adjust target",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        val completionPercentage = (totalCalories.toDouble() / targetCalories.toDouble()).coerceIn(0.0, 1.0)
                        CircularProgressIndicator(
                            progress = { completionPercentage.toFloat() },
                            modifier = Modifier.size(72.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 8.dp,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                        )
                    }

                    // Macro Breakdown Linear Indicators
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                        
                        // Row of macronutrient labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MacroBarProgress(
                                label = "Protein",
                                current = totalProtein,
                                target = targetProtein,
                                unit = "g",
                                barColor = Color(0xFFE57373),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            MacroBarProgress(
                                label = "Carbs",
                                current = totalCarbs,
                                target = targetCarbs,
                                unit = "g",
                                barColor = Color(0xFFFFB74D),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            MacroBarProgress(
                                label = "Fats",
                                current = totalFats,
                                target = targetFats,
                                unit = "g",
                                barColor = Color(0xFF4FC3F7),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 2. MEAL LOGGER CONTROLS (Add Meal Button Card)
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_meal_interactive_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Track Every Day Meals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Log portions to balance healthy nutrients",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_meal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Meal",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Log Meal")
                    }
                }
            }
        }

        // 3. MEALS LIST SECTION
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Logged Meals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "${todayMeals.size} Today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (todayMeals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_meals_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fastfood,
                            contentDescription = "Empty state icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No Meals Logged Today",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Maintain consistency by logging breakfast, lunch, or dinner macros.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        } else {
            items(todayMeals) { meal ->
                MealLogItem(
                    meal = meal,
                    onDeleteClick = { viewModel.deleteMealLog(meal.id) }
                )
            }
        }

        // 4. PRE-BUILT NUTRITION PLANNER
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Pre-Built Nutrition Plans",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pre_built_plans_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restaurant,
                                contentDescription = "Meal Plans",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(
                                text = "Personalized Diet & Recipes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Instantly loaded based on your registration goal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Alert / Info banner about user's goal
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                            .padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Active Goal",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Your Profile Goal: ${profileGoal.ifBlank { "General Fitness" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "We have customized your default planner to target ${
                                        if (profileGoal.contains("Gain", ignoreCase = true)) "high protein lean bulk foods"
                                        else if (profileGoal.contains("Loss", ignoreCase = true) || profileGoal.contains("Fat", ignoreCase = true)) "caloric deficit & high satiety items"
                                        else "balanced macros and sustained daily energy"
                                    }.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Goal selector tab chips
                    Text(
                        text = "Filter by Goal Plan",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Muscle Gain", "Fat Loss", "Balanced Nutrition").forEach { goalOpt ->
                            val isSelected = selectedGoalTab == goalOpt
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedGoalTab = goalOpt },
                                label = { Text(goalOpt) },
                                modifier = Modifier.weight(1f).testTag("goal_tab_${goalOpt.replace(" ", "_").lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Region selector tab chips
                    Text(
                        text = "Filter by Localization / Country",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Global", "Indian", "Japanese", "Mexican").forEach { regionOpt ->
                            val isSelected = selectedRegion == regionOpt
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedRegion = regionOpt },
                                label = { Text(regionOpt) },
                                modifier = Modifier.weight(1f).testTag("region_tab_${regionOpt.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }

                    // Quick Logged success animation card
                    AnimatedVisibility(
                        visible = lastLoggedMealName != null,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Logged",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Successfully logged \"$lastLoggedMealName\" to $lastLoggedMealType!",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Meal List
                    val filteredMeals = preBuiltMeals.filter {
                        it.goal.equals(selectedGoalTab, ignoreCase = true) &&
                        it.region.equals(selectedRegion, ignoreCase = true)
                    }

                    if (filteredMeals.isEmpty()) {
                        Text(
                            text = "No customized plans found for this combination.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            filteredMeals.forEach { meal ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pre_built_meal_item_${meal.name.replace(" ", "_").lowercase()}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = meal.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = meal.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Macro pills row
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            // Cal
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "🔥 ${meal.calories} kcal",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            // Protein
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "💪 ${meal.protein.toInt()}g P",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            // Carbs
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "🍞 ${meal.carbs.toInt()}g C",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                            // Fats
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.outlineVariant)
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "🥑 ${meal.fats.toInt()}g F",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Quick Log interactive triggers
                                        Text(
                                            text = "Quick Log to Diary:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf("Breakfast", "Lunch", "Dinner", "Snack").forEach { type ->
                                                TextButton(
                                                    onClick = {
                                                        viewModel.addMealLog(
                                                            name = meal.name,
                                                            calories = meal.calories,
                                                            protein = meal.protein,
                                                            carbs = meal.carbs,
                                                            fats = meal.fats,
                                                            mealType = type
                                                        )
                                                        lastLoggedMealName = meal.name
                                                        lastLoggedMealType = type
                                                    },
                                                    modifier = Modifier.weight(1f).testTag("quick_log_${type.lowercase()}_${meal.name.replace(" ", "_").lowercase()}"),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    colors = ButtonDefaults.textButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = "+ $type",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 5. DIALOG - ADJUST DAILY NUTRITION TARGETS
    if (showEditTargetsDialog) {
        AlertDialog(
            onDismissRequest = { showEditTargetsDialog = false },
            title = {
                Text(
                    text = "Adjust Daily Targets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Customize your dynamic daily calorie intake and nutrient targets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            val suggested = viewModel.calculateSuggestedCalories()
                            editCalorieGoalStr = suggested.calories.toString()
                            editProteinGoalStr = suggested.protein.toInt().toString()
                            editCarbsGoalStr = suggested.carbs.toInt().toString()
                            editFatsGoalStr = suggested.fats.toInt().toString()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("calculate_targets_from_profile"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Auto Suggest",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Suggest from My Profile")
                    }
                    
                    OutlinedTextField(
                        value = editCalorieGoalStr,
                        onValueChange = { editCalorieGoalStr = it },
                        label = { Text("Daily Calories Target (kcal)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_target_calories"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        leadingIcon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = "Calories") }
                    )

                    OutlinedTextField(
                        value = editProteinGoalStr,
                        onValueChange = { editProteinGoalStr = it },
                        label = { Text("Target Protein (g)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_target_protein"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        )
                    )

                    OutlinedTextField(
                        value = editCarbsGoalStr,
                        onValueChange = { editCarbsGoalStr = it },
                        label = { Text("Target Carbs (g)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_target_carbs"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        )
                    )

                    OutlinedTextField(
                        value = editFatsGoalStr,
                        onValueChange = { editFatsGoalStr = it },
                        label = { Text("Target Fats (g)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_target_fats"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val calories = editCalorieGoalStr.toIntOrNull() ?: 2200
                        val protein = editProteinGoalStr.toDoubleOrNull() ?: 140.0
                        val carbs = editCarbsGoalStr.toDoubleOrNull() ?: 250.0
                        val fats = editFatsGoalStr.toDoubleOrNull() ?: 75.0
                        viewModel.updateNutritionGoals(calories, protein, carbs, fats)
                        showEditTargetsDialog = false
                    },
                    modifier = Modifier.testTag("save_target_goals"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Goals")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTargetsDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 6. DIALOG - FORM TO RECORD A NEW MEAL LOG
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "Record Daily Meal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = mealName,
                        onValueChange = { mealName = it },
                        label = { Text("Meal Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_meal_name"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        leadingIcon = { Icon(Icons.Default.Restaurant, contentDescription = "Meal") }
                    )

                    // Meal Type selector (Row of Choicechips)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Meal Type",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Breakfast", "Lunch", "Dinner", "Snack").forEach { type ->
                                val selected = selectedMealType == type
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { selectedMealType = type }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = type,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Numeric Fields
                    OutlinedTextField(
                        value = caloriesStr,
                        onValueChange = { caloriesStr = it },
                        label = { Text("Estimated Calories (kcal)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_meal_calories"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        leadingIcon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = "Calories") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = proteinStr,
                            onValueChange = { proteinStr = it },
                            label = { Text("Protein (g)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_meal_protein"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )

                        OutlinedTextField(
                            value = carbsStr,
                            onValueChange = { carbsStr = it },
                            label = { Text("Carbs (g)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_meal_carbs"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )

                        OutlinedTextField(
                            value = fatsStr,
                            onValueChange = { fatsStr = it },
                            label = { Text("Fats (g)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_meal_fats"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val calories = caloriesStr.toIntOrNull() ?: 0
                        val protein = proteinStr.toDoubleOrNull() ?: 0.0
                        val carbs = carbsStr.toDoubleOrNull() ?: 0.0
                        val fats = fatsStr.toDoubleOrNull() ?: 0.0
                        if (mealName.isNotBlank() && calories > 0) {
                            viewModel.addMealLog(
                                name = mealName,
                                calories = calories,
                                protein = protein,
                                carbs = carbs,
                                fats = fats,
                                mealType = selectedMealType
                            )
                            // Clear inputs
                            mealName = ""
                            caloriesStr = ""
                            proteinStr = ""
                            carbsStr = ""
                            fatsStr = ""
                            selectedMealType = "Breakfast"
                            showAddDialog = false
                        }
                    },
                    enabled = mealName.isNotBlank() && (caloriesStr.toIntOrNull() ?: 0) > 0,
                    modifier = Modifier.testTag("submit_meal_log"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Register Meal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun MacroBarProgress(
    label: String,
    current: Double,
    target: Double,
    unit: String,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = "${current.toInt()}g",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        val progressPercent = (current / target).coerceIn(0.0, 1.0)
        LinearProgressIndicator(
            progress = { progressPercent.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
        )
    }
}

@Composable
fun MealLogItem(
    meal: MealLog,
    onDeleteClick: () -> Unit
) {
    val containerColor = when (meal.mealType) {
        "Breakfast" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        "Lunch" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        "Dinner" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    
    val badgeColor = when (meal.mealType) {
        "Breakfast" -> Color(0xFF1E88E5)
        "Lunch" -> Color(0xFF43A047)
        "Dinner" -> Color(0xFFE53935)
        else -> Color(0xFF757575)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("meal_log_item_${meal.id}"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = meal.mealType,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = meal.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Macros tags
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Calories",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${meal.calories} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• P: ${meal.protein.toInt()}g | C: ${meal.carbs.toInt()}g | F: ${meal.fats.toInt()}g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onDeleteClick,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete meal log",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

data class LocalMealPlan(
    val name: String,
    val description: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    val goal: String, // "Muscle Gain", "Fat Loss", "Balanced Nutrition"
    val region: String // "Global", "Indian", "Japanese", "Mexican"
)

val preBuiltMeals = listOf(
    // === FAT LOSS ===
    // Global
    LocalMealPlan(
        name = "Lemon Garlic Grilled Whitefish",
        description = "Tender whitefish fillet grilled with garlic and olive oil, served with roasted asparagus.",
        calories = 340, protein = 34.0, carbs = 12.0, fats = 9.0,
        goal = "Fat Loss", region = "Global"
    ),
    LocalMealPlan(
        name = "Mediterranean Chickpea & Avocado Salad",
        description = "A fiber-rich salad combining boiled chickpeas, crisp cucumbers, red onions, and diced avocado.",
        calories = 380, protein = 12.0, carbs = 34.0, fats = 14.0,
        goal = "Fat Loss", region = "Global"
    ),
    LocalMealPlan(
        name = "Egg White Omelet with Spinach",
        description = "Fluffy egg whites cooked with baby spinach and mushrooms, served with one slice of whole wheat toast.",
        calories = 320, protein = 26.0, carbs = 22.0, fats = 6.0,
        goal = "Fat Loss", region = "Global"
    ),
    // Indian
    LocalMealPlan(
        name = "Egg White Masala Bhurji & Roti",
        description = "Scrambled egg whites with tomatoes, onions, chilies, and coriander, served with 1 medium whole wheat roti.",
        calories = 310, protein = 22.0, carbs = 24.0, fats = 8.0,
        goal = "Fat Loss", region = "Indian"
    ),
    LocalMealPlan(
        name = "Grilled Tofu / Paneer Tikka Salad",
        description = "Marinated paneer or firm tofu grilled with bell peppers and onions, tossed in a leafy green salad.",
        calories = 340, protein = 18.0, carbs = 14.0, fats = 16.0,
        goal = "Fat Loss", region = "Indian"
    ),
    LocalMealPlan(
        name = "Sprouted Moong & Pomegranate Salad",
        description = "Steamed sprouts tossed with cucumbers, tomatoes, pomegranate seeds, lemon juice, and chat masala.",
        calories = 280, protein = 14.0, carbs = 38.0, fats = 4.0,
        goal = "Fat Loss", region = "Indian"
    ),
    // Japanese
    LocalMealPlan(
        name = "Steamed Cod with Soy-Ginger Glaze",
        description = "Fresh white cod fillet steamed with ginger and green onions, served with steamed broccoli.",
        calories = 290, protein = 32.0, carbs = 10.0, fats = 4.0,
        goal = "Fat Loss", region = "Japanese"
    ),
    LocalMealPlan(
        name = "Chilled Tofu (Hiyayakko) & Edamame",
        description = "Silken tofu topped with grated ginger and scallions, served with a cup of boiled edamame pods.",
        calories = 310, protein = 22.0, carbs = 18.0, fats = 12.0,
        goal = "Fat Loss", region = "Japanese"
    ),
    LocalMealPlan(
        name = "Grilled Chicken Yakitori",
        description = "Skewers of lean chicken breast grilled with salt and green onions, served with shirataki (konjac) noodles.",
        calories = 340, protein = 35.0, carbs = 8.0, fats = 6.0,
        goal = "Fat Loss", region = "Japanese"
    ),
    // Mexican
    LocalMealPlan(
        name = "Grilled Chicken Tacos",
        description = "Shredded skinless grilled chicken in two corn tortillas, topped with pico de gallo and fresh salsa verde.",
        calories = 330, protein = 28.0, carbs = 22.0, fats = 6.0,
        goal = "Fat Loss", region = "Mexican"
    ),
    LocalMealPlan(
        name = "Nopal (Cactus) Salad with Panela",
        description = "Sliced tender boiled nopales with tomatoes, cilantro, lemon, and small cubes of light panela cheese.",
        calories = 210, protein = 12.0, carbs = 14.0, fats = 8.0,
        goal = "Fat Loss", region = "Mexican"
    ),
    LocalMealPlan(
        name = "Ceviche de Pescado (Fish Ceviche)",
        description = "Fresh white fish cured in lime juice, mixed with diced tomatoes, onions, cilantro, and cucumber.",
        calories = 290, protein = 24.0, carbs = 18.0, fats = 4.0,
        goal = "Fat Loss", region = "Mexican"
    ),

    // === MUSCLE GAIN ===
    // Global
    LocalMealPlan(
        name = "High-Protein Chicken Quinoa Bowl",
        description = "Grilled chicken breast slices over quinoa, roasted sweet potatoes, and steamed broccoli.",
        calories = 660, protein = 48.0, carbs = 65.0, fats = 16.0,
        goal = "Muscle Gain", region = "Global"
    ),
    LocalMealPlan(
        name = "Baked Salmon with Sweet Potato",
        description = "Omega-3 rich salmon fillet baked with herbs, served with a large baked sweet potato and asparagus.",
        calories = 610, protein = 40.0, carbs = 52.0, fats = 20.0,
        goal = "Muscle Gain", region = "Global"
    ),
    LocalMealPlan(
        name = "Lean Beef & Brown Rice Stir-Fry",
        description = "Lean beef strips seared with bell peppers, sugar snap peas, and steamed brown rice.",
        calories = 640, protein = 42.0, carbs = 68.0, fats = 15.0,
        goal = "Muscle Gain", region = "Global"
    ),
    // Indian
    LocalMealPlan(
        name = "Tandoori Chicken Tikka with Quinoa",
        description = "Chicken breast marinated in spiced yogurt, grilled, and served with fluffy high-protein quinoa.",
        calories = 620, protein = 42.0, carbs = 55.0, fats = 16.0,
        goal = "Muscle Gain", region = "Indian"
    ),
    LocalMealPlan(
        name = "High-Protein Paneer Scramble",
        description = "Fresh paneer crumbled and sautéed with green peas, onions, tomatoes, and cumin, with two whole wheat toasts.",
        calories = 540, protein = 24.0, carbs = 48.0, fats = 18.0,
        goal = "Muscle Gain", region = "Indian"
    ),
    LocalMealPlan(
        name = "Soya Chunks Masala with Brown Rice",
        description = "High-protein soya chunks simmered in a spiced tomato gravy, served with steamed brown basmati rice.",
        calories = 580, protein = 36.0, carbs = 65.0, fats = 10.0,
        goal = "Muscle Gain", region = "Indian"
    ),
    // Japanese
    LocalMealPlan(
        name = "Salmon Teriyaki & Brown Rice",
        description = "Salmon fillet grilled with a light teriyaki glaze, paired with a generous bowl of brown rice.",
        calories = 650, protein = 42.0, carbs = 60.0, fats = 18.0,
        goal = "Muscle Gain", region = "Japanese"
    ),
    LocalMealPlan(
        name = "Oven-Baked Chicken Katsu & Rice",
        description = "Crispy panko-breaded chicken breast baked to reduce fat, served with white rice and shredded cabbage.",
        calories = 590, protein = 44.0, carbs = 58.0, fats = 12.0,
        goal = "Muscle Gain", region = "Japanese"
    ),
    LocalMealPlan(
        name = "Beef & Tofu Gyudon Bowl",
        description = "Thinly sliced lean beef and firm tofu simmered in sweet dashi broth over a warm bed of rice.",
        calories = 680, protein = 38.0, carbs = 72.0, fats = 18.0,
        goal = "Muscle Gain", region = "Japanese"
    ),
    // Mexican
    LocalMealPlan(
        name = "Beef Fajitas with Black Beans",
        description = "Lean beef strips seared with bell peppers, served with brown rice and seasoned black beans.",
        calories = 640, protein = 42.0, carbs = 58.0, fats = 16.0,
        goal = "Muscle Gain", region = "Mexican"
    ),
    LocalMealPlan(
        name = "Chipotle Grilled Chicken Bowl",
        description = "Spicy chipotle chicken breast served over quinoa with avocado slices and sweet corn.",
        calories = 590, protein = 45.0, carbs = 48.0, fats = 14.0,
        goal = "Muscle Gain", region = "Mexican"
    ),
    LocalMealPlan(
        name = "Egg White Huevos Rancheros",
        description = "Egg whites over baked corn tortillas, layered with spicy ranchero salsa, black beans, and cotija cheese.",
        calories = 440, protein = 34.0, carbs = 38.0, fats = 8.0,
        goal = "Muscle Gain", region = "Mexican"
    ),

    // === BALANCED NUTRITION ===
    // Global
    LocalMealPlan(
        name = "Classic Turkey & Swiss Wheat Wrap",
        description = "Lean deli turkey breast, Swiss cheese, crisp lettuce, and tomatoes in a whole wheat wrap.",
        calories = 450, protein = 32.0, carbs = 38.0, fats = 12.0,
        goal = "Balanced Nutrition", region = "Global"
    ),
    LocalMealPlan(
        name = "Seared Salmon & Quinoa Medley",
        description = "Grilled salmon fillet with lemon pepper over quinoa mixed with baby spinach and cherry tomatoes.",
        calories = 540, protein = 38.0, carbs = 45.0, fats = 18.0,
        goal = "Balanced Nutrition", region = "Global"
    ),
    LocalMealPlan(
        name = "Greek Yogurt Power Berry Bowl",
        description = "Thick Greek yogurt topped with fresh blueberries, strawberries, chia seeds, and honey.",
        calories = 340, protein = 24.0, carbs = 38.0, fats = 8.0,
        goal = "Balanced Nutrition", region = "Global"
    ),
    // Indian
    LocalMealPlan(
        name = "Mixed Vegetable Khichdi with Curd",
        description = "Nutritious, easily digestible one-pot rice and yellow lentils cooked with mixed veggies, served with fresh yogurt.",
        calories = 420, protein = 16.0, carbs = 62.0, fats = 10.0,
        goal = "Balanced Nutrition", region = "Indian"
    ),
    LocalMealPlan(
        name = "Chana Masala with 2 Rotis",
        description = "Protein-rich chickpeas cooked in a flavorful tomato-onion gravy, paired with two fresh whole wheat rotis.",
        calories = 490, protein = 18.0, carbs = 68.0, fats = 12.0,
        goal = "Balanced Nutrition", region = "Indian"
    ),
    LocalMealPlan(
        name = "Paneer Veggie Mint Wrap",
        description = "Sautéed paneer strips, bell peppers, and cabbage rolled inside a whole wheat tortilla with mint chutney.",
        calories = 460, protein = 20.0, carbs = 45.0, fats = 14.0,
        goal = "Balanced Nutrition", region = "Indian"
    ),
    // Japanese
    LocalMealPlan(
        name = "Salt-Grilled Mackerel (Saba)",
        description = "Crispy, omega-3 rich grilled mackerel served with a bowl of steamed rice and pickled radish.",
        calories = 510, protein = 28.0, carbs = 42.0, fats = 18.0,
        goal = "Balanced Nutrition", region = "Japanese"
    ),
    LocalMealPlan(
        name = "Soba Noodles with Soft Egg",
        description = "Buckwheat soba noodles served in a warm savory dashi broth with green onions and a soft boiled egg.",
        calories = 430, protein = 18.0, carbs = 58.0, fats = 8.0,
        goal = "Balanced Nutrition", region = "Japanese"
    ),
    // Mexican
    LocalMealPlan(
        name = "Enchiladas Verdes with Chicken",
        description = "Chicken breast rolled in corn tortillas, baked in green tomatillo sauce and light cheese.",
        calories = 480, protein = 34.0, carbs = 45.0, fats = 10.0,
        goal = "Balanced Nutrition", region = "Mexican"
    ),
    LocalMealPlan(
        name = "Sopa de Tortilla with Avocado",
        description = "A warm, spiced tomato and chili broth topped with crispy tortilla strips, shredded chicken, and fresh avocado.",
        calories = 390, protein = 16.0, carbs = 35.0, fats = 14.0,
        goal = "Balanced Nutrition", region = "Mexican"
    )
)
