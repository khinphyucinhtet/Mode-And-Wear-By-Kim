package com.example.os_app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val PromptBlue = Color(0xFF1769D2)

@Composable
internal fun AppPromptDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(26.dp)
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .widthIn(max = 410.dp)
                .clip(shape)
                .background(Color.White)
                .border(1.5.dp, Color.Black, shape)
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Text(title, color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Text(message, color = Color(0xFF202124), fontSize = 16.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (dismissLabel != null && onDismiss != null) {
                    TextButton(onClick = onDismiss) {
                        Text(dismissLabel, color = PromptBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel, color = PromptBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
