package com.example.os_app.screens

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

private val PrinterBlue = Color(0xFF1976D2)
private val PaleBlue = Color(0xFFE3F2FD)

private enum class PrinterStatus {
    NOT_PAIRED,
    PAIRED,
    CONNECTING,
    CONNECTED,
    BLUETOOTH_OFF,
    PERMISSION_REQUIRED,
    UNAVAILABLE
}

@Composable
fun BluetoothScreen(onBackClick: () -> Unit) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val lifecycle = activity?.lifecycle
    var printerStatus by remember { mutableStateOf(PrinterStatus.NOT_PAIRED) }
    var showPermissionPrompt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun requiredRuntimePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }

    fun hasBluetoothPermissions(): Boolean = requiredRuntimePermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshPrinterStatus() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            printerStatus = PrinterStatus.UNAVAILABLE
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermissions()) {
            printerStatus = PrinterStatus.PERMISSION_REQUIRED
            return
        }

        try {
            if (!adapter.isEnabled) {
                printerStatus = PrinterStatus.BLUETOOTH_OFF
                return
            }
            val printerIsPaired = ReceiptPrinter.findPairedPrinter(adapter) != null
            printerStatus = when {
                !printerIsPaired -> PrinterStatus.NOT_PAIRED
                ReceiptPrinter.isConnected() -> PrinterStatus.CONNECTED
                else -> PrinterStatus.PAIRED
            }
        } catch (_: SecurityException) {
            printerStatus = PrinterStatus.PERMISSION_REQUIRED
        } catch (_: Exception) {
            printerStatus = PrinterStatus.UNAVAILABLE
        }
    }

    fun openBluetoothSettings() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            printerStatus = PrinterStatus.UNAVAILABLE
            return
        }
        try {
            printerStatus = if (adapter.isEnabled) {
                PrinterStatus.NOT_PAIRED
            } else {
                PrinterStatus.BLUETOOTH_OFF
            }
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            printerStatus = PrinterStatus.UNAVAILABLE
        } catch (_: SecurityException) {
            printerStatus = PrinterStatus.PERMISSION_REQUIRED
        }
    }

    suspend fun connectOrOpenSettings() {
        refreshPrinterStatus()
        when (printerStatus) {
            PrinterStatus.NOT_PAIRED, PrinterStatus.BLUETOOTH_OFF -> openBluetoothSettings()
            PrinterStatus.PAIRED, PrinterStatus.CONNECTED -> {
                printerStatus = PrinterStatus.CONNECTING
                printerStatus = try {
                    ReceiptPrinter.connect(context)
                    PrinterStatus.CONNECTED
                } catch (_: Exception) {
                    PrinterStatus.PAIRED
                }
            }
            else -> Unit
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            scope.launch { connectOrOpenSettings() }
        } else {
            // Keep the status concise; the user can retry or enable permission in App Settings.
            printerStatus = PrinterStatus.PERMISSION_REQUIRED
        }
    }

    fun connectToPrinter() {
        val permissions = requiredRuntimePermissions()
        if (permissions.isNotEmpty() && !hasBluetoothPermissions()) {
            showPermissionPrompt = true
        } else {
            scope.launch { connectOrOpenSettings() }
        }
    }

    LaunchedEffect(Unit) {
        refreshPrinterStatus()
        if (printerStatus == PrinterStatus.PAIRED) connectOrOpenSettings()
    }

    DisposableEffect(lifecycle) {
        if (lifecycle == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    refreshPrinterStatus()
                    if (printerStatus == PrinterStatus.PAIRED) connectOrOpenSettings()
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val statusText = when (printerStatus) {
        PrinterStatus.NOT_PAIRED -> "Printer not paired"
        PrinterStatus.PAIRED -> "Printer paired: SE-5810"
        PrinterStatus.CONNECTED -> "Connected: SE-5810"
        PrinterStatus.CONNECTING -> "Connecting to SE-5810…"
        PrinterStatus.BLUETOOTH_OFF -> "Bluetooth is turned off"
        PrinterStatus.PERMISSION_REQUIRED -> "Bluetooth permission required"
        PrinterStatus.UNAVAILABLE -> "Bluetooth unavailable"
    }
    val statusColor = when (printerStatus) {
        PrinterStatus.NOT_PAIRED, PrinterStatus.PERMISSION_REQUIRED -> Color(0xFFFFA000)
        PrinterStatus.PAIRED, PrinterStatus.CONNECTING -> PrinterBlue
        PrinterStatus.CONNECTED -> Color(0xFF2E9D50)
        PrinterStatus.BLUETOOTH_OFF, PrinterStatus.UNAVAILABLE -> Color(0xFF808080)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val compact = maxHeight < 620.dp
        val iconCircleSize = if (maxHeight < 540.dp) 96.dp else 110.dp
        val iconSize = if (maxHeight < 540.dp) 54.dp else 62.dp

        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp)
        ) {
            Text(
                text = "←",
                modifier = Modifier.semantics { contentDescription = "Back to home" },
                color = PrinterBlue,
                fontSize = 30.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(iconCircleSize).background(PaleBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                BluetoothMark(color = PrinterBlue, modifier = Modifier.size(iconSize))
            }
            Spacer(modifier = Modifier.height(if (compact) 16.dp else 22.dp))
            Text(
                text = "Connect to Printer",
                color = PrinterBlue,
                fontSize = if (compact) 22.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(if (compact) 22.dp else 30.dp))
            Button(
                onClick = ::connectToPrinter,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrinterBlue)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BluetoothMark(color = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Connect to Printer", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(if (compact) 14.dp else 18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(9.dp).background(statusColor, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(statusText, color = Color(0xFF555555), fontSize = 14.sp)
            }
        }
    }

    if (showPermissionPrompt) {
        AppPromptDialog(
            title = "Allow printer access?",
            message = "print2moody needs permission to find and connect to nearby Bluetooth devices. Android will ask you to allow Nearby devices access.",
            confirmLabel = "Continue",
            onConfirm = {
                showPermissionPrompt = false
                permissionLauncher.launch(requiredRuntimePermissions())
            },
            dismissLabel = "Cancel",
            onDismiss = { showPermissionPrompt = false },
            onDismissRequest = { showPermissionPrompt = false }
        )
    }
}

@Composable
private fun BluetoothMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val path = Path().apply {
            moveTo(centerX, size.height * 0.06f)
            lineTo(size.width * 0.82f, size.height * 0.34f)
            lineTo(centerX, size.height * 0.50f)
            lineTo(size.width * 0.82f, size.height * 0.66f)
            lineTo(centerX, size.height * 0.94f)
            lineTo(centerX, size.height * 0.06f)
            moveTo(size.width * 0.18f, size.height * 0.28f)
            lineTo(size.width * 0.82f, size.height * 0.72f)
            moveTo(size.width * 0.18f, size.height * 0.72f)
            lineTo(size.width * 0.82f, size.height * 0.28f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = (size.minDimension * 0.075f).coerceAtLeast(2.dp.toPx()))
        )
    }
}
