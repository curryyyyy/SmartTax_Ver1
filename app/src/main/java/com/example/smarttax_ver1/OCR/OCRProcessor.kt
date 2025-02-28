package com.example.smarttax_ver1.OCR

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// This class handles OCR processing for receipt images
class OCRProcessor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Process image from Uri (from gallery or camera)
    suspend fun processImageFromUri(context: Context, imageUri: Uri): ReceiptData {
        return try {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            val visionText = recognizeTextFromImage(inputImage)
            extractReceiptData(visionText)
        } catch (e: IOException) {
            Log.e("OCRProcessor", "Error processing image: ${e.message}")
            throw e
        }
    }

    // Process image from Bitmap (if you're preprocessing the image)
    suspend fun processImageFromBitmap(bitmap: Bitmap): ReceiptData {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizeTextFromImage(inputImage)
            extractReceiptData(visionText)
        } catch (e: Exception) {
            Log.e("OCRProcessor", "Error processing bitmap: ${e.message}")
            throw e
        }
    }

    // Use suspendCancellableCoroutine to make the ML Kit callback-based API work with coroutines
    private suspend fun recognizeTextFromImage(image: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }

    // Extract receipt data from recognized text
    private fun extractReceiptData(visionText: Text): ReceiptData {
        // Get the full text
        val fullText = visionText.text
        Log.d("OCRProcessor", "Recognized text: $fullText")

        // Extract merchant/vendor name (usually at the top of the receipt)
        val merchantName = extractMerchantName(visionText)

        // Extract date (common formats: DD/MM/YYYY, DD-MM-YYYY, etc.)
        val date = extractDate(visionText)

        // Extract total amount (usually preceded by words like "TOTAL", "AMOUNT", etc.)
        val totalAmount = extractTotalAmount(visionText)

        // Extract line items (products, services with their prices)
        val lineItems = extractLineItems(visionText)

        // Determine likely category based on merchant or items
        val category = determineCategory(merchantName, lineItems)

        return ReceiptData(
            merchantName = merchantName,
            date = date,
            totalAmount = totalAmount,
            lineItems = lineItems,
            category = category,
            rawText = fullText
        )
    }

    // Extract merchant name - typically at the top of the receipt, often in all caps
    private fun extractMerchantName(visionText: Text): String {
        val textBlocks = visionText.textBlocks

        // Often the merchant name is in the first few lines and in all caps
        if (textBlocks.isNotEmpty()) {
            val firstBlock = textBlocks[0]
            if (firstBlock.lines.isNotEmpty()) {
                // Get the first line which is often the merchant name
                return firstBlock.lines[0].text
            }
        }

        return "Unknown Merchant"
    }

    // Extract date using regex patterns
    private fun extractDate(visionText: Text): String {
        val dateRegex = listOf(
            Regex("\\d{2}[/.-]\\d{2}[/.-]\\d{4}"), // DD/MM/YYYY or DD-MM-YYYY
            Regex("\\d{2}[/.-]\\d{2}[/.-]\\d{2}"),  // DD/MM/YY or DD-MM-YY
            Regex("\\d{4}[/.-]\\d{2}[/.-]\\d{2}")   // YYYY/MM/DD or YYYY-MM-DD
        )

        val fullText = visionText.text

        // Try each regex pattern
        for (regex in dateRegex) {
            val match = regex.find(fullText)
            if (match != null) {
                return match.value
            }
        }

        // Check for date with month names
        val monthDateRegex = Regex("\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{4}",
            RegexOption.IGNORE_CASE)
        val monthMatch = monthDateRegex.find(fullText)
        if (monthMatch != null) {
            return monthMatch.value
        }

        return "Unknown Date"
    }

    // Extract total amount
    private fun extractTotalAmount(visionText: Text): Double {
        val fullText = visionText.text

        // Look for common patterns that precede the total amount
        val totalIndicators = listOf(
            "TOTAL", "Total", "total",
            "AMOUNT", "Amount", "amount",
            "GRAND TOTAL", "Grand Total",
            "SUBTOTAL", "Subtotal",
            "RM", "MYR"
        )

        // Regex for currency amount (handles Malaysian Ringgit format: RM 123.45 or just 123.45)
        val amountRegex = Regex("(?:RM|MYR)?\\s*\\d+[,.]\\d{2}")

        // Scan for lines containing total indicators
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text

                // Check if the line contains a total indicator
                val containsIndicator = totalIndicators.any { indicator ->
                    lineText.contains(indicator, ignoreCase = true)
                }

                if (containsIndicator) {
                    // Try to extract the amount from this line
                    val match = amountRegex.find(lineText)
                    if (match != null) {
                        // Clean up the amount string (remove RM, spaces, commas)
                        val amountStr = match.value
                            .replace("RM", "")
                            .replace("MYR", "")
                            .replace(" ", "")
                            .replace(",", ".")

                        return try {
                            amountStr.toDouble()
                        } catch (e: NumberFormatException) {
                            0.0
                        }
                    }
                }
            }
        }

        // Fallback: just look for any currency amount in the text
        val matches = amountRegex.findAll(fullText).toList()
        if (matches.isNotEmpty()) {
            // Get the last match, which is often the total
            val lastMatch = matches.last().value
                .replace("RM", "")
                .replace("MYR", "")
                .replace(" ", "")
                .replace(",", ".")

            return try {
                lastMatch.toDouble()
            } catch (e: NumberFormatException) {
                0.0
            }
        }

        return 0.0
    }

    // Extract line items (products/services with prices)
    private fun extractLineItems(visionText: Text): List<LineItem> {
        val items = mutableListOf<LineItem>()

        // We'll implement a simple approach: look for lines with a description followed by a price
        val priceRegex = Regex("\\d+[,.]\\d{2}")

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text

                // Ignore lines that are likely headers or footers
                val isHeader = lineText.contains("RECEIPT") ||
                        lineText.contains("INVOICE") ||
                        lineText.contains("TEL:") ||
                        lineText.contains("THANK YOU") ||
                        lineText.contains("CUSTOMER")

                if (!isHeader) {
                    // Check if line contains a price
                    val priceMatch = priceRegex.find(lineText)
                    if (priceMatch != null) {
                        // Everything before the price is the description
                        val descEnd = priceMatch.range.first
                        val desc = if (descEnd > 0) lineText.substring(0, descEnd).trim() else "Item"

                        // Convert price to double
                        val priceStr = priceMatch.value.replace(",", ".")
                        val price = try {
                            priceStr.toDouble()
                        } catch (e: NumberFormatException) {
                            0.0
                        }

                        items.add(LineItem(description = desc, amount = price))
                    }
                }
            }
        }

        return items
    }

    // Determine the likely tax category based on content
    private fun determineCategory(merchantName: String, items: List<LineItem>): String {
        // Check merchant name first
        val merchantLower = merchantName.lowercase()

        // Medical
        if (merchantLower.contains("clinic") ||
            merchantLower.contains("hospital") ||
            merchantLower.contains("pharmacy") ||
            merchantLower.contains("medical") ||
            merchantLower.contains("healthcare")) {
            return "Medical"
        }

        // Education
        if (merchantLower.contains("school") ||
            merchantLower.contains("college") ||
            merchantLower.contains("university") ||
            merchantLower.contains("education") ||
            merchantLower.contains("books") ||
            merchantLower.contains("stationery")) {
            return "Education"
        }

        // Sports Equipment
        if (merchantLower.contains("sports") ||
            merchantLower.contains("fitness") ||
            merchantLower.contains("gym")) {
            return "Sport Equipment"
        }

        // Childcare
        if (merchantLower.contains("childcare") ||
            merchantLower.contains("nursery") ||
            merchantLower.contains("kindergarten")) {
            return "Childcare"
        }

        // Check items if merchant name doesn't give clues
        for (item in items) {
            val itemLower = item.description.lowercase()

            if (itemLower.contains("medicine") ||
                itemLower.contains("medical") ||
                itemLower.contains("health")) {
                return "Medical"
            }

            if (itemLower.contains("book") ||
                itemLower.contains("course") ||
                itemLower.contains("class")) {
                return "Education"
            }
        }

        // Default category if no specific category is detected
        return "Lifestyle Expenses"
    }
}

// Data classes to store the extracted information

data class ReceiptData(
    val merchantName: String,
    val date: String,
    val totalAmount: Double,
    val lineItems: List<LineItem>,
    val category: String,
    val rawText: String
)

data class LineItem(
    val description: String,
    val amount: Double
)