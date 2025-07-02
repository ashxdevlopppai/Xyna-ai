package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import data.models.Log
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(
    studyTime: Long,
    appUsage: Map<String, Long>,
    currentMood: String,
    recentLogs: List<Log>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Study", "Apps", "Mood", "Logs")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier.padding(padding)
        ) {
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> OverviewTab(studyTime, appUsage, currentMood)
                1 -> StudyTab(studyTime)
                2 -> AppsTab(appUsage)
                3 -> MoodTab(currentMood)
                4 -> LogsTab(recentLogs)
            }
        }
    }
}

@Composable
private fun OverviewTab(
    studyTime: Long,
    appUsage: Map<String, Long>,
    currentMood: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        MetricCard(
            title = "Today's Study Time",
            value = formatDuration(studyTime),
            icon = Icons.Filled.School
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        MetricCard(
            title = "Most Used App",
            value = appUsage.maxByOrNull { it.value }?.key ?: "None",
            icon = Icons.Filled.Apps
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        MetricCard(
            title = "Current Mood",
            value = currentMood,
            icon = Icons.Filled.Mood
        )
    }
}

@Composable
private fun StudyTab(studyTime: Long) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Study Statistics",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        StudyMetrics(studyTime)
    }
}

@Composable
private fun AppsTab(appUsage: Map<String, Long>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(appUsage.toList()) { (app, time) ->
            AppUsageItem(app, time)
            Divider()
        }
    }
}

@Composable
private fun MoodTab(currentMood: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmotionUI(
            emotion = currentMood,
            intensity = 0.8f,
            happiness = 0.7f,
            energy = 0.9f
        )
    }
}

@Composable
private fun LogsTab(logs: List<Log>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(logs) { log ->
            LogItem(log)
            Divider()
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Icon(icon, contentDescription = title)
        }
    }
}

@Composable
private fun StudyMetrics(studyTime: Long) {
    // Add study-specific metrics and charts here
    Text("Total Study Time: ${formatDuration(studyTime)}")
}

@Composable
private fun AppUsageItem(app: String, time: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(app)
        Text(formatDuration(time))
    }
}

@Composable
private fun LogItem(log: Log) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(log.timestamp)),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = log.content,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val hours = milliseconds / (1000 * 60 * 60)
    val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)
    return String.format("%02d:%02d", hours, minutes)
}