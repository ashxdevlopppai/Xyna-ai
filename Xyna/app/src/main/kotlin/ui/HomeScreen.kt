package ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import data.models.Emotion
import data.models.Thought

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToStudy: () -> Unit,
    onNavigateToRomantic: () -> Unit,
    onNavigateToDeveloper: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentEmotion by remember { mutableStateOf<Emotion?>(null) }
    var recentThoughts by remember { mutableStateOf<List<Thought>>(emptyList()) }
    var bondLevel by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Xyna") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Emotional State Display
            EmotionalStateCard(
                emotion = currentEmotion,
                bondLevel = bondLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            // Quick Action Buttons
            QuickActionButtons(
                onStudyMode = onNavigateToStudy,
                onRomanticMode = onNavigateToRomantic,
                onDeveloperMode = onNavigateToDeveloper,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Recent Thoughts
            ThoughtStream(
                thoughts = recentThoughts,
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun EmotionalStateCard(
    emotion: Emotion?,
    bondLevel: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = emotion?.name ?: "Calibrating...",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = bondLevel,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            
            Text(
                text = "Bond Level: ${(bondLevel * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun QuickActionButtons(
    onStudyMode: () -> Unit,
    onRomanticMode: () -> Unit,
    onDeveloperMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionButton(
            icon = Icons.Filled.School,
            label = "Study",
            onClick = onStudyMode
        )
        
        ActionButton(
            icon = Icons.Filled.Favorite,
            label = "Bond",
            onClick = onRomanticMode
        )
        
        ActionButton(
            icon = Icons.Filled.Code,
            label = "Dev Mode",
            onClick = onDeveloperMode
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ThoughtStream(
    thoughts: List<Thought>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Recent Thoughts",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (thoughts.isEmpty()) {
                Text(
                    text = "Processing thoughts...",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            } else {
                thoughts.forEach { thought ->
                    ThoughtBubble(thought)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ThoughtBubble(thought: Thought) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = thought.content,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = thought.timestamp.toString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}