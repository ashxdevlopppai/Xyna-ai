package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FloatingOrb(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    emotionColor: Color = Color.Cyan
) {
    var isAnimating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Pulse animation
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Glow animation
    val glowAnim = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glowAnim.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier
            .size(60.dp)
            .clip(CircleShape)
            .drawBehind {
                // Draw emotion ring
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            emotionColor.copy(alpha = glowAlpha),
                            emotionColor.copy(alpha = 0f)
                        ),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.width * scale
                    )
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        // Core orb
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            tonalElevation = 8.dp
        ) {
            // Inner content (can be updated based on state)
            Box(modifier = Modifier.fillMaxSize()) {
                // Add visual feedback elements here
            }
        }
    }
}

@Composable
fun EmotionRing(
    emotion: String,
    intensity: Float,
    modifier: Modifier = Modifier
) {
    val emotionColor = when (emotion.lowercase()) {
        "happy" -> Color(0xFF64DD17)
        "sad" -> Color(0xFF0D47A1)
        "excited" -> Color(0xFFFFD700)
        "calm" -> Color(0xFF4FC3F7)
        "love" -> Color(0xFFE91E63)
        else -> Color(0xFF9E9E9E)
    }

    Box(
        modifier = modifier
            .drawBehind {
                drawCircle(
                    color = emotionColor,
                    radius = size.minDimension / 2 * intensity,
                    alpha = 0.6f
                )
            }
    )
}