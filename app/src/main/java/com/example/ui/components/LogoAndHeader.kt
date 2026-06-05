package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun AnimatedLogo(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "logoBorder")
    
    // Smooth angle rotation for a subtle gradient border glow
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Vibrant gradient colors matching the cobalt blue to emerald teal theme
    val borderColors = listOf(
        Color(0xFF2B7FFF), // Primary Blue
        Color(0xFF10B981), // Emerald Green
        Color(0xFF3B82F6), // Secondary Blue
        Color(0xFF0F766E), // Teal
        Color(0xFF2B7FFF)  // Loop back
    )

    val borderBrush = Brush.sweepGradient(borderColors)

    Box(
        modifier = modifier
            .size(54.dp) // Optimized neat size alongside "Ali Tools"
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        // Draw the rotating gradient border canvas for a premium glowing style
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(angle)
        ) {
            drawRoundRect(
                brush = borderBrush,
                style = Stroke(width = 4.dp.toPx())
            )
        }

        // Inner image containing the actual custom premium logo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(RoundedCornerShape(11.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_app_icon),
                contentDescription = "Ali Tools App Icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
