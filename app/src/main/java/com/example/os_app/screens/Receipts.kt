package com.example.os_app.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Calendar

private val ReceiptBlue = Color(0xFF1476D4)
private val ReceiptLightBlue = Color(0xFFDCEFFC)
private val ReceiptBorder = Color(0xFFD9DDE2)
private val ReceiptField = Color(0xFFF8F9FA)
private val ReceiptMuted = Color(0xFF66717C)
internal const val DEFAULT_DISCOUNT_DIGITS = "0"
private val ReceiptDateFormatter = DateTimeFormatter.ofPattern("d.M.yyyy", Locale.US)

internal fun formatReceiptDate(date: LocalDate = LocalDate.now()): String =
    date.format(ReceiptDateFormatter)

private class ReceiptLine(
    name: String,
    quantity: String,
    unitPrice: String
) {
    var name by mutableStateOf(name)
    var quantity by mutableStateOf(quantity)
    var unitPrice by mutableStateOf(unitPrice)
}

@Composable
fun ReceiptsScreen(onBackClick: () -> Unit) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current

    var customerName by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var receiptDate by rememberSaveable { mutableStateOf(formatReceiptDate()) }
    var address by rememberSaveable { mutableStateOf("") }
    var discountDigits by rememberSaveable { mutableStateOf(DEFAULT_DISCOUNT_DIGITS) }
    var deliveryDigits by rememberSaveable { mutableStateOf("") }
    val items = remember {
        mutableStateListOf(
            ReceiptLine("", "", ""),
            ReceiptLine("", "", "")
        )
    }
    var showPreview by remember { mutableStateOf(false) }
    var showPrintConfirmation by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }
    var printResult by remember { mutableStateOf<String?>(null) }
    var pendingReceipt by remember { mutableStateOf<ReceiptPrintData?>(null) }
    var showBluetoothPermissionPrompt by remember { mutableStateOf(false) }
    var printerConnected by remember { mutableStateOf(ReceiptPrinter.isConnected()) }
    val printScope = rememberCoroutineScope()
    val activity = context as? ComponentActivity
    val lifecycle = activity?.lifecycle

    fun openDatePicker() {
        val calendar = Calendar.getInstance()
        val selectedDate = receiptDate.split('.').mapNotNull { it.toIntOrNull() }
        if (selectedDate.size == 3) {
            calendar.set(selectedDate[2], (selectedDate[1] - 1).coerceIn(0, 11), selectedDate[0])
        }
        DatePickerDialog(
            context,
            { _, year, month, day -> receiptDate = "$day.${month + 1}.$year" },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val subtotal = items.sumOf { line ->
        line.quantity.toLongOrNull().orZero() * line.unitPrice.toLongOrNull().orZero()
    }
    val discount = discountDigits.toLongOrNull().orZero()
    val delivery = deliveryDigits.toLongOrNull().orZero()
    val total = (subtotal - discount + delivery).coerceAtLeast(0L)

    fun collectReceipt(): ReceiptPrintData? {
        if (customerName.isBlank()) {
            printResult = "Enter the customer name before printing."
            return null
        }
        val namedLines = items.filter { it.name.isNotBlank() }
        val printLines = namedLines.mapNotNull { line ->
            val quantity = line.quantity.toIntOrNull()
            val unitPrice = line.unitPrice.toLongOrNull()
            if (quantity == null || quantity <= 0 || unitPrice == null || unitPrice < 0) null
            else ReceiptPrintLine(line.name.trim(), quantity, unitPrice)
        }
        if (printLines.isEmpty()) {
            printResult = "Add at least one item with a valid quantity and price."
            return null
        }
        if (printLines.size != namedLines.size) {
            printResult = "Check each item’s quantity and price before printing."
            return null
        }
        return ReceiptPrintData(
            customer = customerName.trim(),
            phone = phoneNumber.trim(),
            date = receiptDate.trim(),
            address = address.trim(),
            items = printLines,
            discount = discount,
            delivery = delivery
        )
    }

    fun startPrint(receipt: ReceiptPrintData) {
        isPrinting = true
        printResult = null
        printScope.launch {
            try {
                ReceiptPrinter.print(context.applicationContext, receipt)
                printerConnected = ReceiptPrinter.isConnected()
                printResult = "Print job sent to SE-5810. Check that the receipt came out correctly."
            } catch (error: SecurityException) {
                printerConnected = ReceiptPrinter.isConnected()
                printResult = "Bluetooth permission is required. Allow Nearby devices access and try again."
            } catch (error: Exception) {
                printerConnected = ReceiptPrinter.isConnected()
                printResult = error.message ?: "Printing failed. Check the printer and try again."
            } finally {
                isPrinting = false
            }
        }
    }

    val printPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val receipt = pendingReceipt
        pendingReceipt = null
        if (granted && receipt != null) {
            startPrint(receipt)
        } else {
            isPrinting = false
            printResult = "Bluetooth permission is required. Allow Nearby devices access to print."
        }
    }

    fun confirmAndPrint() {
        val receipt = collectReceipt() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingReceipt = receipt
            printPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            startPrint(receipt)
        }
    }

    fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    suspend fun tryConnectPrinter(openSettingsIfNeeded: Boolean) {
        try {
            ReceiptPrinter.connect(context.applicationContext)
            printerConnected = ReceiptPrinter.isConnected()
            printResult = null
        } catch (error: Exception) {
            printerConnected = ReceiptPrinter.isConnected()
            val message = error.message.orEmpty()
            val needsSettings = message.contains("not paired", ignoreCase = true) ||
                message.contains("Could not identify a paired receipt printer", ignoreCase = true) ||
                message.contains("turn on Bluetooth", ignoreCase = true)
            if (openSettingsIfNeeded && needsSettings) {
                runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            } else if (!needsSettings) {
                printResult = message.ifBlank { "Could not connect to SE-5810. Check Bluetooth and try again." }
            }
        }
    }

    val connectPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            printScope.launch { tryConnectPrinter(openSettingsIfNeeded = true) }
        } else {
            printResult = "Allow Nearby devices access to connect to SE-5810."
        }
    }

    fun onPrinterChipClick() {
        if (hasConnectPermission()) {
            printScope.launch { tryConnectPrinter(openSettingsIfNeeded = true) }
        } else {
            showBluetoothPermissionPrompt = true
        }
    }

    LaunchedEffect(Unit) {
        printerConnected = ReceiptPrinter.isConnected()
        if (!printerConnected && hasConnectPermission()) {
            tryConnectPrinter(openSettingsIfNeeded = false)
        }
    }

    DisposableEffect(lifecycle) {
        if (lifecycle == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                printerConnected = ReceiptPrinter.isConnected()
                if (!printerConnected && hasConnectPermission()) {
                    printScope.launch { tryConnectPrinter(openSettingsIfNeeded = false) }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F5F6))
            .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White)
                    .border(1.dp, ReceiptBorder, RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "←",
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onBackClick)
                            .semantics { contentDescription = "Back to home" },
                        color = Color(0xFF263746),
                        fontSize = 22.sp,
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text = "New receipt",
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF343A40),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (printerConnected) Color(0xFFE5F5E9) else Color.White)
                            .border(
                                1.dp,
                                if (printerConnected) Color(0xFF238344) else ReceiptBorder,
                                RoundedCornerShape(50.dp)
                            )
                            .clickable(onClick = ::onPrinterChipClick)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(
                                    if (printerConnected) Color(0xFF238344) else ReceiptMuted,
                                    RoundedCornerShape(50.dp)
                                )
                        )
                        Text(
                            "SE-5810",
                            color = if (printerConnected) Color(0xFF176D34) else ReceiptMuted,
                            fontSize = 10.sp,
                            fontWeight = if (printerConnected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                SmallLabel("Customer")
                Spacer(Modifier.height(4.dp))
                ReceiptInput(
                    value = customerName,
                    onValueChange = { customerName = it },
                    placeholder = "Customer name",
                    semanticLabel = "Customer name"
                )

                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        SmallLabel("Phone Number")
                        Spacer(Modifier.height(4.dp))
                        ReceiptInput(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            keyboardType = KeyboardType.Phone,
                            placeholder = "Phone Number"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        SmallLabel("Date")
                        Spacer(Modifier.height(4.dp))
                        Box {
                            ReceiptInput(
                                value = receiptDate,
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth().clickable { openDatePicker() },
                                readOnly = true,
                                placeholder = "Select date",
                                semanticLabel = "Receipt date"
                            )
                            Text(
                                text = "▦",
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 10.dp)
                                    .clickable { openDatePicker() },
                                color = ReceiptMuted,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(7.dp))
                SmallLabel("Address")
                Spacer(Modifier.height(4.dp))
                ReceiptInput(
                    value = address,
                    onValueChange = { address = it },
                    height = 70.dp,
                    singleLine = false,
                    placeholder = "Address"
                )

                Spacer(Modifier.height(12.dp))
                SmallLabel("Items")
                Spacer(Modifier.height(4.dp))

                items.forEach { line ->
                    ItemCard(
                        line = line,
                        lineTotal = line.quantity.toLongOrNull().orZero() *
                            line.unitPrice.toLongOrNull().orZero(),
                        onDelete = { items.remove(line) }
                    )
                    Spacer(Modifier.height(6.dp))
                }

                ReceiptActionButton(
                    text = "＋  Add item",
                    background = ReceiptLightBlue,
                    foreground = Color(0xFF174C7B),
                    modifier = Modifier.fillMaxWidth().height(35.dp),
                    onClick = { items.add(ReceiptLine("", "", "")) }
                )

                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        SmallLabel("Discount")
                        Spacer(Modifier.height(3.dp))
                        AmountInput(
                            value = discountDigits,
                            onValueChange = { discountDigits = it },
                            semanticLabel = "Discount amount"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        SmallLabel("Delivery fee")
                        Spacer(Modifier.height(3.dp))
                        AmountInput(value = deliveryDigits, onValueChange = { deliveryDigits = it })
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subtotal", color = Color(0xFF40464C), fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatAmount(subtotal), color = Color(0xFF4E555C), fontSize = 10.sp)
                }
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total", color = Color(0xFF30363B), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text(formatAmount(total), color = Color(0xFF20252A), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReceiptActionButton(
                        text = "Preview",
                        background = ReceiptLightBlue,
                        foreground = Color(0xFF174C7B),
                        modifier = Modifier.weight(0.8f).height(38.dp),
                        onClick = { showPreview = true }
                    )
                    ReceiptActionButton(
                        text = "▣  Print receipt",
                        background = ReceiptBlue,
                        foreground = Color.White,
                        modifier = Modifier.weight(1.2f).height(38.dp),
                        onClick = { showPrintConfirmation = true }
                    )
                }
            }
        }
    }

    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("Receipt preview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(customerName.ifBlank { "Customer" })
                    Text(phoneNumber)
                    Text(receiptDate)
                    if (address.isNotBlank()) Text(address)
                    items.forEach { line ->
                        Text("${line.name}  ${line.quantity} × ${formatAmount(line.unitPrice.toLongOrNull().orZero())}")
                    }
                    Text("Subtotal: ${formatAmount(subtotal)}")
                    if (discount > 0) Text("Discount: -${formatAmount(discount)}")
                    if (delivery > 0) Text("Delivery fee: ${formatAmount(delivery)}")
                    Text("Total: ${formatAmount(total)}", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreview = false }) { Text("Close") }
            }
        )
    }

    if (showPrintConfirmation) {
        AppPromptDialog(
            title = "Confirm Printing",
            message = "Are you sure you want to print this receipt?\n\nPlease double-check the customer details and total amount before printing.",
            confirmLabel = "Yes, Print",
            onConfirm = {
                showPrintConfirmation = false
                confirmAndPrint()
            },
            dismissLabel = "No, Go Back",
            onDismiss = { showPrintConfirmation = false },
            onDismissRequest = { showPrintConfirmation = false },
        )
    }

    if (showBluetoothPermissionPrompt) {
        AppPromptDialog(
            title = "Allow printer access?",
            message = "print2moody needs permission to connect to the SE-5810 over Bluetooth. Android will ask you to allow Nearby devices access.",
            confirmLabel = "Allow",
            onConfirm = {
                showBluetoothPermissionPrompt = false
                connectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            },
            dismissLabel = "Not now",
            onDismiss = { showBluetoothPermissionPrompt = false },
            onDismissRequest = { showBluetoothPermissionPrompt = false }
        )
    }

    if (isPrinting || printResult != null) {
        AppPromptDialog(
            title = if (isPrinting) "Printing receipt" else "Print result",
            message = printResult ?: "Connecting to SE-5810…",
            confirmLabel = if (isPrinting) "Please wait" else "OK",
            onConfirm = { if (!isPrinting) printResult = null },
            onDismissRequest = { if (!isPrinting) printResult = null },
        )
    }
}

@Composable
private fun SmallLabel(text: String) {
    Text(text = text, color = Color(0xFF626A72), fontSize = 9.sp, lineHeight = 11.sp)
}

@Composable
private fun ReceiptInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: androidx.compose.ui.unit.Dp = 40.dp,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    readOnly: Boolean = false,
    semanticLabel: String? = null
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(5.dp))
            .background(ReceiptField)
            .border(1.dp, ReceiptBorder, RoundedCornerShape(5.dp))
            .padding(horizontal = 9.dp, vertical = if (singleLine) 7.dp else 8.dp)
            .semantics {
                if (semanticLabel != null) contentDescription = semanticLabel
            },
        singleLine = singleLine,
        readOnly = readOnly,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(color = Color(0xFF30363B), fontSize = 13.sp, lineHeight = 18.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(ReceiptBlue),
        decorationBox = { innerTextField ->
            Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        color = Color(0xFF9AA1A8),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun AmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    semanticLabel: String? = null
) {
    ReceiptInput(
        value = formatEditableAmount(value),
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        keyboardType = KeyboardType.Number,
        placeholder = "Amount",
        semanticLabel = semanticLabel
    )
}

@Composable
private fun ItemCard(
    line: ReceiptLine,
    lineTotal: Long,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFFBFBFC))
            .border(1.dp, ReceiptBorder, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ReceiptInput(
                value = line.name,
                onValueChange = { line.name = it },
                modifier = Modifier.weight(1f),
                height = 34.dp,
                placeholder = "Item name"
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onDelete)
                    .semantics { contentDescription = "Delete item" },
                contentAlignment = Alignment.Center
            ) {
                TrashIcon()
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ReceiptInput(
                value = line.quantity,
                onValueChange = { line.quantity = it.filter(Char::isDigit) },
                modifier = Modifier.width(78.dp),
                height = 34.dp,
                keyboardType = KeyboardType.Number,
                placeholder = "Qty"
            )
            ReceiptInput(
                value = formatEditableAmount(line.unitPrice),
                onValueChange = { line.unitPrice = it.filter(Char::isDigit) },
                modifier = Modifier.weight(1f),
                height = 34.dp,
                keyboardType = KeyboardType.Number,
                placeholder = "Price"
            )
            Text(
                text = formatAmount(lineTotal),
                modifier = Modifier.widthIn(min = 54.dp),
                color = Color(0xFF51585F),
                fontSize = 10.sp,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun TrashIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeWidth = 1.2.dp.toPx()
        drawLine(Color(0xFFCE4B52), Offset(size.width * 0.28f, size.height * 0.30f), Offset(size.width * 0.72f, size.height * 0.30f), strokeWidth)
        drawLine(Color(0xFFCE4B52), Offset(size.width * 0.40f, size.height * 0.20f), Offset(size.width * 0.60f, size.height * 0.20f), strokeWidth)
        drawRoundRect(
            color = Color(0xFFCE4B52),
            topLeft = Offset(size.width * 0.34f, size.height * 0.34f),
            size = Size(size.width * 0.32f, size.height * 0.52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )
        drawLine(Color(0xFFCE4B52), Offset(size.width * 0.46f, size.height * 0.43f), Offset(size.width * 0.46f, size.height * 0.77f), strokeWidth)
        drawLine(Color(0xFFCE4B52), Offset(size.width * 0.54f, size.height * 0.43f), Offset(size.width * 0.54f, size.height * 0.77f), strokeWidth)
    }
}

@Composable
private fun ReceiptActionButton(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(5.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = foreground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1)
    }
}

private fun Long?.orZero(): Long = this ?: 0L

private fun formatAmount(amount: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(amount.coerceAtLeast(0L))

private fun formatEditableAmount(digits: String): String =
    digits.toLongOrNull()?.let(::formatAmount).orEmpty()
