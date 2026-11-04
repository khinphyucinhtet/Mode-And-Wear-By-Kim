package com.example.os_app.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextDirectionHeuristics
import com.example.os_app.R
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.OutputStream
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun isSenzo5810Name(name: String?): Boolean {
    val normalized = name?.uppercase(Locale.ROOT)?.filter(Char::isLetterOrDigit).orEmpty()
    return "5810" in normalized || "SENZO" in normalized
}

private fun isPrinterAlias(name: String?): Boolean {
    val normalized = name?.uppercase(Locale.ROOT)?.filter(Char::isLetterOrDigit).orEmpty()
    return "PRINTER" in normalized || "THERMAL" in normalized || "RECEIPT" in normalized
}

internal data class ReceiptPrintLine(
    val name: String,
    val quantity: Int,
    val unitPrice: Long
) {
    val lineTotal: Long get() = quantity.toLong() * unitPrice
}

internal data class ReceiptPrintData(
    val customer: String,
    val phone: String,
    val date: String,
    val address: String,
    val items: List<ReceiptPrintLine>,
    val discount: Long,
    val delivery: Long
) {
    val subtotal: Long get() = items.sumOf(ReceiptPrintLine::lineTotal)
    val total: Long get() = (subtotal - discount + delivery).coerceAtLeast(0L)
}

internal object ReceiptPrinter {
    private const val PRINT_WIDTH_DOTS = 384
    private const val ROWS_PER_COMMAND = 128
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val connectionLock = Any()
    @Volatile private var activeSocket: BluetoothSocket? = null
    @Volatile private var activeAddress: String? = null

    fun isConnected(): Boolean = activeSocket?.isConnected == true

    suspend fun connect(context: Context): String = withContext(Dispatchers.IO) {
        ensurePermission(context)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IOException("This device does not have Bluetooth.")
        if (!adapter.isEnabled) throw IOException("Turn on Bluetooth and try again.")
        val printer = findPairedPrinter(adapter)
            ?: throw IOException(
                "Could not identify a paired receipt printer. Pair the SE-5810 and use a Bluetooth name containing SENZO, 5810, or PRINTER."
            )
        openConnection(printer)
        @Suppress("MissingPermission")
        printer.name ?: "SE-5810"
    }

    suspend fun print(context: Context, receipt: ReceiptPrintData) = withContext(Dispatchers.IO) {
        ensurePermission(context)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IOException("This device does not have Bluetooth.")
        if (!adapter.isEnabled) throw IOException("Turn on Bluetooth and try again.")

        val printer = findPairedPrinter(adapter)
            ?: throw IOException(
                "Could not identify a paired receipt printer. Pair the SE-5810 and use a Bluetooth name containing SENZO, 5810, or PRINTER."
            )
        val bitmap = buildReceiptBitmap(context, receipt)
        try {
            val socket = openConnection(printer)
            try {
                val output = socket.outputStream
                output.write(byteArrayOf(0x1B, 0x40)) // ESC @: initialize printer
                writeRasterImage(output, bitmap)
                output.write(byteArrayOf(0x1B, 0x64, 0x03)) // ESC d 3: feed paper
                output.flush()
            } catch (failure: Exception) {
                closeConnection()
                throw failure
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun ensurePermission(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Bluetooth permission is required to print.")
        }
    }

    @Suppress("MissingPermission")
    internal fun findPairedPrinter(adapter: BluetoothAdapter): BluetoothDevice? {
        val bondedDevices = adapter.bondedDevices.toList()
        val exactMatches = bondedDevices.filter { device -> isSenzo5810Name(device.name) }
        if (exactMatches.size == 1) return exactMatches.single()
        if (exactMatches.size > 1) return null

        return bondedDevices.filter { device -> isPrinterAlias(device.name) }.singleOrNull()
    }

    @Suppress("MissingPermission")
    private fun openConnection(device: BluetoothDevice): BluetoothSocket = synchronized(connectionLock) {
        val current = activeSocket
        if (current?.isConnected == true && activeAddress == device.address) return current
        closeConnection()

        val secureSocket = device.createRfcommSocketToServiceRecord(sppUuid)
        try {
            secureSocket.connect()
            activeSocket = secureSocket
            activeAddress = device.address
            secureSocket
        } catch (secureFailure: IOException) {
            runCatching { secureSocket.close() }
            val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(sppUuid)
            try {
                insecureSocket.connect()
                activeSocket = insecureSocket
                activeAddress = device.address
                insecureSocket
            } catch (insecureFailure: Exception) {
                runCatching { insecureSocket.close() }
                throw IOException(
                    "Could not connect to SE-5810. Check that it is on, paired, and supports Bluetooth Classic (SPP).",
                    insecureFailure
                )
            }
        } catch (failure: SecurityException) {
            runCatching { secureSocket.close() }
            throw failure
        }
    }

    private fun closeConnection() {
        synchronized(connectionLock) {
            runCatching { activeSocket?.close() }
            activeSocket = null
            activeAddress = null
        }
    }

    private fun writeRasterImage(output: OutputStream, bitmap: Bitmap) {
        val bytesPerRow = (bitmap.width + 7) / 8
        var top = 0
        while (top < bitmap.height) {
            val rows = minOf(ROWS_PER_COMMAND, bitmap.height - top)
            val pixels = IntArray(bitmap.width * rows)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, top, bitmap.width, rows)
            val raster = ByteArray(bytesPerRow * rows)

            for (row in 0 until rows) {
                for (column in 0 until bitmap.width) {
                    val pixel = pixels[row * bitmap.width + column]
                    val alpha = Color.alpha(pixel)
                    val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                    if (alpha > 127 && luminance < 180) {
                        val index = row * bytesPerRow + column / 8
                        raster[index] = (raster[index].toInt() or (0x80 shr (column % 8))).toByte()
                    }
                }
            }

            output.write(
                byteArrayOf(
                    0x1D, 0x76, 0x30, 0x00,
                    (bytesPerRow and 0xFF).toByte(),
                    ((bytesPerRow shr 8) and 0xFF).toByte(),
                    (rows and 0xFF).toByte(),
                    ((rows shr 8) and 0xFF).toByte()
                )
            )
            output.write(raster)
            output.flush()
            top += rows
        }
    }

    private fun buildReceiptBitmap(context: Context, receipt: ReceiptPrintData): Bitmap {
        val width = PRINT_WIDTH_DOTS
        val padding = 12
        val contentWidth = width - padding * 2
        val regular = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        val bold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        val totalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        val commands = mutableListOf<(Canvas) -> Unit>()
        var y = 12

        fun layout(text: String, paint: TextPaint, alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, maxWidth: Int = contentWidth): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                .setAlignment(alignment)
                .setIncludePad(true)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .build()

        fun addText(text: String, paint: TextPaint = regular, alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, gap: Int = 0) {
            if (text.isEmpty()) return
            val textLayout = layout(text, paint, alignment)
            val top = y
            commands += { canvas ->
                canvas.save()
                canvas.translate(padding.toFloat(), top.toFloat())
                textLayout.draw(canvas)
                canvas.restore()
            }
            y += textLayout.height + gap
        }

        fun addRule() {
            val ruleY = y + 3f
            commands += { canvas ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 2f }
                canvas.drawLine(padding.toFloat(), ruleY, (width - padding).toFloat(), ruleY, paint)
            }
            y += 15
        }

        fun addAmountRow(label: String, amount: String, paint: TextPaint = regular) {
            val amountWidth = paint.measureText(amount).toInt()
            val leftWidth = (contentWidth - amountWidth - 8).coerceAtLeast(100)
            val leftLayout = layout(label, paint, maxWidth = leftWidth)
            val top = y
            commands += { canvas ->
                canvas.save()
                canvas.translate(padding.toFloat(), top.toFloat())
                leftLayout.draw(canvas)
                canvas.restore()
                canvas.drawText(amount, (width - padding).toFloat() - paint.measureText(amount), (top + leftLayout.getLineBaseline(0)).toFloat(), paint)
            }
            y += maxOf(leftLayout.height, paint.fontSpacing.toInt()) + 3
        }

        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_art)
        if (logo != null) {
            val targetWidth = minOf(270, contentWidth)
            val targetHeight = (logo.height * targetWidth.toFloat() / logo.width).toInt()
            val destination = RectF(
                (width - targetWidth) / 2f,
                y.toFloat(),
                (width + targetWidth) / 2f,
                (y + targetHeight).toFloat()
            )
            commands += { canvas ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                }
                canvas.drawBitmap(logo, null, destination, paint)
            }
            y += targetHeight + 8
        }

        addText("09775916207", alignment = Layout.Alignment.ALIGN_CENTER, gap = 8)
        addRule()
        addText("Customer: ${receipt.customer}")
        if (receipt.phone.isNotBlank()) addText("Phone: ${receipt.phone}")
        if (receipt.date.isNotBlank()) addText("Date: ${receipt.date}")
        if (receipt.address.isNotBlank()) addText("Address: ${receipt.address}", gap = 3)
        addRule()

        receipt.items.forEach { item ->
            addText(item.name, gap = 1)
            addAmountRow("  ${item.quantity} x ${format(item.unitPrice)}", format(item.lineTotal))
        }

        addRule()
        addAmountRow("Discount", "-${format(receipt.discount)}")
        addAmountRow("Delivery Fees", format(receipt.delivery))
        addAmountRow("TOTAL", format(receipt.total), totalPaint)
        addRule()
        addText("Thank you!", bold, Layout.Alignment.ALIGN_CENTER, gap = 16)

        val bitmap = Bitmap.createBitmap(width, y.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        commands.forEach { draw -> draw(canvas) }
        logo?.recycle()
        return bitmap
    }

    private fun format(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)
}
