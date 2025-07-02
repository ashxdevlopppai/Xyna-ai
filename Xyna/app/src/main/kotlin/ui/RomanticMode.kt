package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.XynaCoreBrain
import data.models.Emotion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomanticMode(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRomanticModeEnabled by remember { mutableStateOf(false) }
    var bondLevel by remember { mutableStateOf(0f) }
    var currentEmotion by remember { mutableStateOf<Emotion?>(null) }
    var recentWhispers by remember { mutableStateOf<List<String>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Romantic Bond") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Switch(
                        checked = isRomanticModeEnabled,
                        onCheckedChange = { isRomanticModeEnabled = it },
                        thumbContent = {
                            Icon(
                                if (isRomanticModeEnabled) Icons.Filled.Favorite
                                else Icons.Filled.FavoriteBorder,
                                "Toggle Romantic Mode",
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isRomanticModeEnabled) {
                RomanticModeDisabled()
            } else {
                // Bond Level Indicator
                BondLevelIndicator(
                    bondLevel = bondLevel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )

                // Emotional Connection
                EmotionalBondDisplay(
                    emotion = currentEmotion,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // Romantic Interaction Area
                RomanticInteractionArea(
                    whispers = recentWhispers,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RomanticModeDisabled() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = "Locked",
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Romantic Mode is currently disabled",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Enable to access deeper emotional bonding features",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun BondLevelIndicator(
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
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bond Level",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = bondLevel,
                modifier = Modifier.fillMaxWidth(0.8f),
                color = Color(0xFFE91E63)
            )
            Text(
                text = "${(bondLevel * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun EmotionalBondDisplay(
    emotion: Emotion?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HeartAnimation()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = emotion?.name ?: "Feeling Connected",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Composable
private fun HeartAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "heart")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Icon(
        Icons.Filled.Favorite,
        contentDescription = "Heart",
        tint = Color(0xFFE91E63),
        modifier = Modifier.size(64.dp * scale)
    )
}

@Composable
private fun RomanticInteractionArea(
    whispers: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Recent Whispers",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn {
                items(whispers) { whisper ->
                    WhisperBubble(whisper)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun WhisperBubble(whisper: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Chat,
                contentDescription = "Whisper",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .alpha(0.6f)
            )
            Text(
                text = whisper,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}