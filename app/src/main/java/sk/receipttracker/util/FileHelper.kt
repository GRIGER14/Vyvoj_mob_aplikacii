package sk.receipttracker.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileHelper {

    /**
     * Creates a new temporary file in the app-specific cache directory
     * and returns its content Uri via FileProvider.
     */
    fun createTempImageUri(context: Context): Pair<Uri, File> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = File(context.filesDir, "receipt_images")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val imageFile = File.createTempFile(
            "RECEIPT_${timeStamp}_",
            ".jpg",
            storageDir
        )

        val uri = FileProvider.getUriForFile(
            context,
            "sk.receipttracker.fileprovider",
            imageFile
        )
        return Pair(uri, imageFile)
    }

    /**
     * Deletes a receipt photo from app storage if it exists.
     */
    fun deleteImageFile(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
