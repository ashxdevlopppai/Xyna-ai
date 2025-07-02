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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import data.models.Log
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperMode(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    var logs by remember { mutableStateOf<List<Log>>(emptyList()) }
    var memoryUsage by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var activeModules by remember { mutableStateOf<List<String>>(emptyList()) }
    var brainState by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Console") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Refresh data */ }) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Selection
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                listOf("Logs", "Memory", "Modules", "Brain State").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> LogViewer(logs)
                1 -> MemoryMonitor(memoryUsage)
                2 -> ModuleStatus(activeModules)
                3 -> BrainStateViewer(brainState)
            }
        }
    }
}

@Composable
private fun LogViewer(logs: List<Log>) {
    var filterType by remember { mutableStateOf("All") }
    val logTypes = listOf("All", "Error", "Info", "Debug")

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Log Type Filter
        ScrollableTabRow(
            selectedTabIndex = logTypes.indexOf(filterType),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            logTypes.forEach { type ->
                Tab(
                    selected = filterType == type,
                    onClick = { filterType = type },
                    text = { Text(type) }
                )
            }
        }

        // Log List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            items(logs.filter { log ->
                filterType == "All" || log.type.equals(filterType, ignoreCase = true)
            }) { log ->
                LogEntry(log)
                Divider()
            }
        }
    }
}

@Composable
private fun LogEntry(log: Log) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(Date(log.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = log.type,
                style = MaterialTheme.typography.labelSmall,
                color = when (log.type.lowercase()) {
                    "error" -> MaterialTheme.colorScheme.error
                    "warning" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        }
        Text(
            text = log.content,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun MemoryMonitor(memoryUsage: Map<String, Float>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Memory Usage",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        memoryUsage.forEach { (module, usage) ->
            MemoryUsageBar(
                module = module,
                usage = usage,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MemoryUsageBar(
    module: String,
    usage: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(module)
            Text("${(usage * 100).toInt()}%")
        }
        LinearProgressIndicator(
            progress = usage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = when {
                usage > 0.9f -> MaterialTheme.colorScheme.error
                usage > 0.7f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
private fun ModuleStatus(activeModules: List<String>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(activeModules) { module ->
            ModuleStatusCard(module)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ModuleStatusCard(module: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = module,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Active",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BrainStateViewer(brainState: Map<String, Any>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(brainState.toList()) { (key, value) ->
            BrainStateEntry(key, value)
            Divider()
        }
    }
}

@Composable
private fun BrainStateEntry(
    key: String,
    value: Any
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(2f)
                .padding(start = 16.dp),
            overflow = TextOverflow.Ellipsis,
            maxLines = 2
        )
    }
}