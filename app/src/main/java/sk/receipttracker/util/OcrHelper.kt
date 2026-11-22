package sk.receipttracker.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Locale

data class OcrResult(
    val detectedAmount: Double?,
    val detectedStore: String?,
    val detectedDate: String?,
    val rawText: String
)

object OcrHelper {

    private val KNOWN_STORES = listOf(
        "Lidl", "Billa", "Kaufland", "Tesco", "Coop Jednota", "DM Drogerie", "Teta",
        "Shell", "Slovnaft", "OMV", "Orlen", "Alza", "Nay", "Datart", "Ikea",
        "Jysk", "Action", "Pepco", "KFC", "McDonald's", "Burger King", "Lekáreň"
    )

    fun processReceipt(
        context: Context,
        photoPath: String,
        onSuccess: (OcrResult) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        try {
            val file = File(photoPath)
            if (!file.exists()) {
                onError(IllegalArgumentException("File does not exist: $photoPath"))
                return
            }

            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val result = parseVisionText(visionText.text)
                    onSuccess(result)
                }
                .addOnFailureListener { exception ->
                    onError(exception)
                }
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun parseVisionText(text: String): OcrResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // 1. Detect Store Name
        var detectedStore: String? = null
        for (known in KNOWN_STORES) {
            if (lines.any { it.contains(known, ignoreCase = true) }) {
                detectedStore = known
                break
            }
        }

        if (detectedStore == null && lines.isNotEmpty()) {
            // Pick first line that isn't metadata (DIČ, IČO, DOKLAD, etc.)
            val ignoredKeywords = listOf("doklad", "pokladnica", "ico", "ičo", "dic", "dič", "dph", "ekasa", "faktura")
            val candidate = lines.take(4).firstOrNull { line ->
                val lower = line.lowercase(Locale.getDefault())
                ignoredKeywords.none { lower.contains(it) } && line.length in 3..30
            }
            if (candidate != null) {
                detectedStore = candidate
            }
        }

        // 2. Detect Amount (SPOLU / CELKOM / K ÚHRADE / SUMA / TOTAL)
        var detectedAmount: Double? = null

        // Try targeted keyword matching first
        val totalKeywords = listOf("spolu", "celkom", "úhrada", "uhrada", "k úhrade", "suma", "total")
        for (line in lines) {
            val lower = line.lowercase(Locale.getDefault())
            if (totalKeywords.any { lower.contains(it) }) {
                // Extract price from this line or next numbers
                val priceMatch = Regex("""(\d+[.,]\d{2})""").find(line)
                if (priceMatch != null) {
                    val amountStr = priceMatch.groupValues[1].replace(',', '.')
                    val parsed = amountStr.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) {
                        detectedAmount = parsed
                        break
                    }
                }
            }
        }

        // Fallback: look for largest price on receipt or last price with euro sign
        if (detectedAmount == null) {
            val allPrices = mutableListOf<Double>()
            for (line in lines) {
                val matches = Regex("""(\d+[.,]\d{2})""").findAll(line)
                for (match in matches) {
                    val parsed = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (parsed != null && parsed in 0.5..10000.0) {
                        allPrices.add(parsed)
                    }
                }
            }
            if (allPrices.isNotEmpty()) {
                // Usually the total is the maximum number or last line total
                detectedAmount = allPrices.maxOrNull()
            }
        }

        // 3. Detect Date (dd.MM.yyyy or dd/MM/yyyy)
        var detectedDate: String? = null
        val dateRegex = Regex("""\b(\d{1,2}[./]\d{1,2}[./]\d{2,4})\b""")
        for (line in lines) {
            val dateMatch = dateRegex.find(line)
            if (dateMatch != null) {
                detectedDate = dateMatch.groupValues[1]
                break
            }
        }

        return OcrResult(
            detectedAmount = detectedAmount,
            detectedStore = detectedStore,
            detectedDate = detectedDate,
            rawText = text
        )
    }
}
