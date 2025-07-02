package ui

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import core.XynaCoreBrain
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRecording by remember { mutableStateOf(false) }
    var studyTimer by remember { mutableStateOf(0L) }
    var recognizedText by remember { mutableStateOf("") }
    var studyInsights by remember { mutableStateOf<List<String>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val xynaBrain = remember { XynaCoreBrain.getInstance(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Assistant") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isRecording = !isRecording }
                    ) {
                        Icon(
                            if (isRecording) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            "Toggle Recording"
                        )
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
            // Study Timer
            StudyTimer(
                elapsed = studyTimer,
                isRecording = isRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            // Camera Preview for OCR
            CameraPreview(
                onTextRecognized = { text ->
                    recognizedText = text
                    // Process recognized text through XynaCoreBrain
                    coroutineScope.launch {
                        xynaBrain.executeCommand(
                            "analyze_study_content $text",
                            onSuccess = { insights ->
                                studyInsights = insights.split("\n")
                            },
                            onError = { /* Handle error */ }
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Study Insights
            StudyInsights(
                recognizedText = recognizedText,
                insights = studyInsights,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StudyTimer(
    elapsed: Long,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatTime(elapsed),
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = if (isRecording) "Studying" else "Paused",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CameraPreview(
    onTextRecognized: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = modifier,
        update = { previewView ->
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            // Set up camera and OCR here
            // This is a placeholder for actual camera implementation
        }
    )
}

@Composable
private fun StudyInsights(
    recognizedText: String,
    insights: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        LazyColumn(
            modifier = Modifier.padding(16.dp)
        ) {
            item {
                Text(
                    text = "Recognized Text:",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = recognizedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "AI Insights:",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(insights) { insight ->
                InsightItem(insight)
            }
        }
    }
}

@Composable
private fun InsightItem(insight: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Lightbulb,
                contentDescription = "Insight",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(insight)
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = milliseconds / (1000 * 60 * 60)
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}