package sk.receipttracker.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import sk.receipttracker.data.model.Expense
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    /**
     * Exports expenses to CSV format with UTF-8 BOM for Microsoft Excel / Sheets compatibility.
     */
    fun exportToCsv(context: Context, expenses: List<Expense>): Uri {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val csvFile = File(exportDir, "Vydavky_$timeStamp.csv")

        FileOutputStream(csvFile).use { fos ->
            // Write UTF-8 BOM
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

            val header = "ID;Dátum;Názov;Kategória;Suma (€);GPS Šírka;GPS Dĺžka;Poznámka;Má bloček\n"
            fos.write(header.toByteArray(Charsets.UTF_8))

            expenses.forEach { expense ->
                val hasPhoto = if (!expense.photoPath.isNullOrBlank()) "Áno" else "Nie"
                val lat = expense.latitude?.toString() ?: ""
                val lon = expense.longitude?.toString() ?: ""
                val safeNote = expense.note.replace(";", ",")
                val safeTitle = expense.title.replace(";", ",")

                val line = "${expense.id};${expense.formattedDate()};$safeTitle;${expense.category};${String.format(Locale.US, "%.2f", expense.amount)};$lat;$lon;$safeNote;$hasPhoto\n"
                fos.write(line.toByteArray(Charsets.UTF_8))
            }
        }

        return FileProvider.getUriForFile(
            context,
            "sk.receipttracker.fileprovider",
            csvFile
        )
    }

    /**
     * Exports expenses to a multi-page PDF document with table summary and receipt photos.
     */
    fun exportToPdf(context: Context, expenses: List<Expense>): Uri {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val pdfFile = File(exportDir, "Vydavky_Report_$timeStamp.pdf")

        val document = PdfDocument()
        val pageWidth = 595 // Standard A4 width in points
        val pageHeight = 842 // Standard A4 height in points

        // Paints
        val titlePaint = Paint().apply {
            color = Color.rgb(0, 109, 68) // Emerald
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerBgPaint = Paint().apply {
            color = Color.rgb(230, 240, 235)
            style = Paint.Style.FILL
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // Header
        canvas.drawText("Výkaz výdavkov — ReceiptTracker", 40f, 50f, titlePaint)
        val exportDateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val totalSpent = expenses.sumOf { it.amount }
        canvas.drawText("Vygenerované: $exportDateStr | Počet záznamov: ${expenses.size} | Celková suma: ${String.format(Locale.US, "%.2f €", totalSpent)}", 40f, 68f, subtitlePaint)

        var yPos = 100f
        val startX = 40f
        val colWidths = floatArrayOf(80f, 160f, 90f, 75f, 50f, 60f) // Dátum, Názov, Kategória, Suma, GPS, Bloček

        // Table Header
        canvas.drawRect(startX, yPos - 14f, pageWidth - 40f, yPos + 8f, headerBgPaint)
        canvas.drawText("Dátum", startX + 4f, yPos, headerPaint)
        canvas.drawText("Názov / Obchod", startX + colWidths[0], yPos, headerPaint)
        canvas.drawText("Kategória", startX + colWidths[0] + colWidths[1], yPos, headerPaint)
        canvas.drawText("Suma", startX + colWidths[0] + colWidths[1] + colWidths[2], yPos, headerPaint)
        canvas.drawText("GPS", startX + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3], yPos, headerPaint)
        canvas.drawText("Bloček", startX + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + colWidths[4], yPos, headerPaint)
        yPos += 22f

        for (expense in expenses) {
            // Check for page overflow
            if (yPos > pageHeight - 60f) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                yPos = 50f

                // Re-draw table header
                canvas.drawRect(startX, yPos - 14f, pageWidth - 40f, yPos + 8f, headerBgPaint)
                canvas.drawText("Dátum", startX + 4f, yPos, headerPaint)
                canvas.drawText("Názov / Obchod", startX + colWidths[0], yPos, headerPaint)
                canvas.drawText("Kategória", startX + colWidths[0] + colWidths[1], yPos, headerPaint)
                canvas.drawText("Suma", startX + colWidths[0] + colWidths[1] + colWidths[2], yPos, headerPaint)
                canvas.drawText("GPS", startX + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3], yPos, headerPaint)
                canvas.drawText("Bloček", startX + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + colWidths[4], yPos, headerPaint)
                yPos += 22f
            }

            val dateStr = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(expense.date))
            val titleStr = if (expense.title.length > 22) expense.title.take(20) + "..." else expense.title
            val hasGps = if (expense.latitude != null) "Áno" else "-"
            val hasPhoto = if (!expense.photoPath.isNullOrBlank()) "Áno" else "-"

            canvas.drawText(dateStr, startX + 4f, yPos, textPaint)
            canvas.drawText(titleStr, startX + colWidths[0], yPos, textPaint)
            canvas.drawText(expense.category, startX + colWidths[0] + colWidths[1], yPos, textPaint)
            canvas.drawText(expense.formattedAmount(), startX + colWidths[0] + colWidths[1] + colWidths[2], yPos, headerPaint)
            canvas.drawText(hasGps, startX + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3], yPos, textPaint)
            canvas.drawText(hasPhoto, startX + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + colWidths[4], yPos, textPaint)

            canvas.drawLine(startX, yPos + 4f, pageWidth - 40f, yPos + 4f, linePaint)
            yPos += 20f
        }

        document.finishPage(page)

        // Additional Page: Visual Receipts Gallery for expenses that have photos
        val expensesWithPhotos = expenses.filter { !it.photoPath.isNullOrBlank() && File(it.photoPath).exists() }
        if (expensesWithPhotos.isNotEmpty()) {
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas

            canvas.drawText("Príloha — Fotografie pokladničných blokov", 40f, 50f, titlePaint)
            var photoY = 80f

            for (expense in expensesWithPhotos.take(6)) { // Embed up to 6 receipts in photo gallery
                if (photoY > pageHeight - 160f) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    photoY = 50f
                }

                try {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 } // Scale down to avoid OOM
                    val bitmap = BitmapFactory.decodeFile(expense.photoPath, options)
                    if (bitmap != null) {
                        val thumbHeight = 110f
                        val thumbWidth = (bitmap.width.toFloat() / bitmap.height.toFloat()) * thumbHeight
                        val destRect = Rect(40, photoY.toInt(), (40 + thumbWidth).toInt(), (photoY + thumbHeight).toInt())
                        canvas.drawBitmap(bitmap, null, destRect, null)

                        canvas.drawText("${expense.title} — ${expense.formattedAmount()}", 40 + thumbWidth + 15f, photoY + 25f, headerPaint)
                        canvas.drawText("Dátum: ${expense.formattedDate()} | Kategória: ${expense.category}", 40 + thumbWidth + 15f, photoY + 45f, subtitlePaint)
                        if (expense.latitude != null) {
                            canvas.drawText("GPS: ${String.format("%.4f, %.4f", expense.latitude, expense.longitude)}", 40 + thumbWidth + 15f, photoY + 65f, subtitlePaint)
                        }

                        photoY += thumbHeight + 20f
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            document.finishPage(page)
        }

        FileOutputStream(pdfFile).use { fos ->
            document.writeTo(fos)
        }
        document.close()

        return FileProvider.getUriForFile(
            context,
            "sk.receipttracker.fileprovider",
            pdfFile
        )
    }

    /**
     * Launches Android ShareSheet for a given file Uri.
     */
    fun shareFile(context: Context, fileUri: Uri, mimeType: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Zdieľať $title"))
    }
}
