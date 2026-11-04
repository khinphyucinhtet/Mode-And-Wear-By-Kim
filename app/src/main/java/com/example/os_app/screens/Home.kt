package com.example.os_app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    onBluetoothClick: () -> Unit,
    onReceiptsClick: () -> Unit
) {
    val blue = Color(0xFF1976D2)
    val lightBlue = Color(0xFFE3F2FD)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val buttonSize = minOf(maxWidth * 0.38f, maxHeight * 0.30f, 176.dp)

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeButton("Bluetooth", true, Modifier.size(buttonSize), lightBlue, blue, onBluetoothClick)
            HomeButton("Receipts", false, Modifier.size(buttonSize), blue, Color.White, onReceiptsClick)
        }
    }
}

@Composable
private fun HomeButton(
    label: String,
    isBluetooth: Boolean,
    modifier: Modifier,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HomeButtonIcon(isBluetooth = isBluetooth, color = contentColor)
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomeButtonIcon(isBluetooth: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(30.dp)) {
        val stroke = Stroke(width = 2.4.dp.toPx())
        if (isBluetooth) {
            val cx = size.width / 2
            val cy = size.height / 2
            val path = Path().apply {
                moveTo(cx, size.height * 0.08f)
                lineTo(cx + size.width * 0.30f, size.height * 0.32f)
                lineTo(cx, cy)
                lineTo(cx + size.width * 0.30f, size.height * 0.68f)
                lineTo(cx, size.height * 0.92f)
                lineTo(cx, size.height * 0.08f)
                moveTo(cx - size.width * 0.30f, size.height * 0.28f)
                lineTo(cx + size.width * 0.30f, size.height * 0.72f)
                moveTo(cx - size.width * 0.30f, size.height * 0.72f)
                lineTo(cx + size.width * 0.30f, size.height * 0.28f)
            }
            drawPath(path, color = color, style = stroke)
        } else {
            val left = size.width * 0.22f
            val right = size.width * 0.78f
            val top = size.height * 0.10f
            val bottom = size.height * 0.90f
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                style = stroke
            )
            repeat(3) { index ->
                val y = size.height * (0.36f + index * 0.18f)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.36f, y), androidx.compose.ui.geometry.Offset(size.width * 0.66f, y), stroke.width)
            }
        }
    }
}
