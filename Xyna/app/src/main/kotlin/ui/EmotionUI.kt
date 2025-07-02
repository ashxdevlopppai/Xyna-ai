package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun EmotionUI(
    emotion: String,
    intensity: Float,
    happiness: Float,
    energy: Float,
    modifier: Modifier = Modifier
) {
    var animatedIntensity by remember { mutableFloatStateOf(0f) }
    var animatedHappiness by remember { mutableFloatStateOf(0f) }
    var animatedEnergy by remember { mutableFloatStateOf(0f) }

    // Animate emotional parameters
    LaunchedEffect(intensity, happiness, energy) {
        animate(initialValue = animatedIntensity, targetValue = intensity) { value, _ ->
            animatedIntensity = value
        }
        animate(initialValue = animatedHappiness, targetValue = happiness) { value, _ ->
            animatedHappiness = value
        }
        animate(initialValue = animatedEnergy, targetValue = energy) { value, _ ->
            animatedEnergy = value
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Emotion Visualization
        EmotionVisualization(
            emotion = emotion,
            intensity = animatedIntensity,
            happiness = animatedHappiness,
            energy = animatedEnergy,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(16.dp)
        )

        // Emotion Stats
        EmotionStats(
            emotion = emotion,
            intensity = intensity,
            happiness = happiness,
            energy = energy,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun EmotionVisualization(
    emotion: String,
    intensity: Float,
    happiness: Float,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val emotionColor = getEmotionColor(emotion, happiness)
    val infiniteTransition = rememberInfiniteTransition(label = "emotion")
    
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (size.minDimension / 2) * intensity * pulseSize

        // Draw emotion aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    emotionColor.copy(alpha = 0.7f),
                    emotionColor.copy(alpha = 0.0f)
                ),
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )

        // Draw energy waves
        for (i in 0..5) {
            val waveRadius = radius * (1f + (i * 0.1f))
            drawCircle(
                color = emotionColor.copy(alpha = 0.1f * (1f - (i / 5f)) * energy),
                center = center,
                radius = waveRadius,
                style = Stroke(width = 2f)
            )
        }
    }
}

@Composable
private fun EmotionStats(
    emotion: String,
    intensity: Float,
    happiness: Float,
    energy: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        EmotionStatBar(
            label = "Intensity",
            value = intensity,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        EmotionStatBar(
            label = "Happiness",
            value = happiness,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        EmotionStatBar(
            label = "Energy",
            value = energy,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun EmotionStatBar(
    label: String,
    value: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LinearProgressIndicator(
            progress = value,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = color
        )
    }
}

private fun getEmotionColor(emotion: String, happiness: Float): Color {
    return when (emotion.lowercase()) {
        "joy" -> Color(0xFF64DD17)
        "sadness" -> Color(0xFF0D47A1)
        "anger" -> Color(0xFFD50000)
        "fear" -> Color(0xFF6200EA)
        "surprise" -> Color(0xFFFFD600)
        "love" -> Color(0xFFE91E63)
        else -> {
            // Blend between happy and sad colors based on happiness value
            val happyColor = Color(0xFF64DD17)
            val sadColor = Color(0xFF0D47A1)
            lerp(sadColor, happyColor, happiness)
        }
    }
}