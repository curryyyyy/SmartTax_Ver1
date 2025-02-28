package com.example.smarttax_ver1.OCR

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class OCRProcessor {
    private lateinit var tessBaseAPI: TessBaseAPI
    private val dictionary = OCRDictionary()
    private val templateMatcher = ReceiptTemplateMatcher()
    private var initialized = false

    // Process image from Uri (from gallery or camera)
    suspend fun processImageFromUri(context: Context, imageUri: Uri): ReceiptData {
        return withContext(Dispatchers.IO) {
            try {
                // Initialize components if not already done
                if (!initialized) {
                    initializeComponents(context)
                }

                // Convert Uri to Bitmap
                val bitmap = android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)

                // Process the bitmap
                val result = processImageFromBitmap(context, bitmap)

                // Clean up resources
                tessBaseAPI.end()

                result
            } catch (e: IOException) {
                Log.e("OCRProcessor", "Error processing image: ${e.message}")
                throw e
            }
        }
    }

    // Initialize all OCR enhancement components
    private suspend fun initializeComponents(context: Context) {
        // Initialize Tesseract
        initTesseract(context)

        // Initialize dictionary
        dictionary.initialize(context)

        // Initialize template matcher
        templateMatcher.initialize(context)

        initialized = true
    }

    // Process image from Bitmap
    suspend fun processImageFromBitmap(context: Context, bitmap: Bitmap): ReceiptData {
        return withContext(Dispatchers.Default) {
            try {
                // Set the bitmap to be analyzed
                tessBaseAPI.setImage(bitmap)

                // Get the recognized text
                val recognizedText = tessBaseAPI.utF8Text ?: ""
                Log.d("OCRProcessor", "Original recognized text: $recognizedText")

                // APPROACH 1: Try template-based recognition first
                val templateResult = templateMatcher.matchReceipt(recognizedText)
                if (templateResult != null) {
                    Log.d("OCRProcessor", "Used template-based recognition")
                    return@withContext templateResult
                }

                // APPROACH 2: Apply dictionary corrections to the raw text
                val correctedText = dictionary.applyCorrections(recognizedText)
                Log.d("OCRProcessor", "Text after dictionary corrections: $correctedText")

                // APPROACH 3: Extract data using standard OCR techniques with the corrected text
                val result = extractReceiptData(correctedText, recognizedText)

                // Post-process merchant name with dictionary again
                result.copy(merchantName = dictionary.correctMerchantName(result.merchantName))
            } catch (e: Exception) {
                Log.e("OCRProcessor", "Error processing bitmap: ${e.message}")
                throw e
            }
        }
    }

    // Initialize Tesseract with language data
    private fun initTesseract(context: Context) {
        if (!::tessBaseAPI.isInitialized) {
            tessBaseAPI = TessBaseAPI()

            // Create a temporary directory for Tesseract
            val dataPath = context.getExternalFilesDir(null)?.absolutePath + "/tesseract/"
            val dir = File(dataPath + "tessdata/")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            // Copy trained data from assets to the tessdata directory if needed
            copyTrainedData(context, "eng.traineddata", dataPath)

            // Initialize Tesseract with English language
            val success = tessBaseAPI.init(dataPath, "eng")
            if (!success) {
                Log.e("OCRProcessor", "Could not initialize Tesseract.")
                throw RuntimeException("Could not initialize Tesseract.")
            }

            // Set Tesseract variables for receipt recognition
            tessBaseAPI.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz.,-/\\:;'\"(){}[]<>!@#$%^&*_+=|?~` ");
            tessBaseAPI.setVariable("tessedit_ocr_engine_mode", "3"); // Use LSTM for better accuracy
            tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
        }
    }

    // Copy trained data from assets
    private fun copyTrainedData(context: Context, filename: String, dataPath: String) {
        try {
            val file = File(dataPath + "/tessdata/" + filename)
            if (!file.exists()) {
                context.assets.open("tessdata/$filename").use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
                Log.d("OCRProcessor", "Copied $filename to tessdata directory")
            }
        } catch (e: IOException) {
            Log.e("OCRProcessor", "Could not copy $filename to tessdata directory: ${e.message}")
            throw e
        }
    }

    // Extract receipt data from recognized text
    private fun extractReceiptData(correctedText: String, originalText: String): ReceiptData {
        Log.d("OCRProcessor", "Extracting data from corrected text: $correctedText")

        // Extract merchant name (usually at the top of the receipt)
        val merchantName = extractMerchantName(correctedText)

        // Extract date
        val date = extractDate(correctedText)

        // Extract total amount
        val totalAmount = extractTotalAmount(correctedText)

        // Extract line items
        val lineItems = extractLineItems(correctedText)

        // Determine likely category
        val category = determineCategory(merchantName, lineItems)

        return ReceiptData(
            merchantName = merchantName,
            date = date,
            totalAmount = totalAmount,
            lineItems = lineItems,
            category = category,
            rawText = originalText
        )
    }

    // Extract merchant name - typically at the top of the receipt
    private fun extractMerchantName(text: String): String {
        // Split the text into lines
        val lines = text.split("\n")

        // The merchant name is typically in the first few lines
        // Look for lines that are capitalized or longer than typical address lines
        for (i in 0 until minOf(5, lines.size)) {
            val line = lines[i].trim()
            if (line.isNotEmpty() &&
                (line == line.uppercase(Locale.getDefault()) || line.length > 10) &&
                !line.contains("RECEIPT") &&
                !line.contains("INVOICE") &&
                !line.contains("TEL:") &&
                !line.matches(Regex(".*\\d{4}.*"))) { // Avoid lines that are likely dates or phone numbers
                return line
            }
        }

        // If we couldn't find a merchant name, return the first non-empty line
        for (line in lines) {
            if (line.trim().isNotEmpty()) {
                return line.trim()
            }
        }

        return "Unknown Merchant"
    }

    // Extract date using regex patterns
    private fun extractDate(text: String): String {
        // Common date formats
        val datePatterns = listOf(
            // DD/MM/YYYY
            Pattern.compile("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})"),
            // YYYY/MM/DD
            Pattern.compile("(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})"),
            // Month names: 01 Jan 2023, Jan 01 2023, etc.
            Pattern.compile("(\\d{1,2})\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{4}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2}\\s*,?\\s*\\d{4}", Pattern.CASE_INSENSITIVE)
        )

        val lines = text.split("\n")

        // Look for dates in each line
        for (line in lines) {
            for (pattern in datePatterns) {
                val matcher = pattern.matcher(line)
                if (matcher.find()) {
                    return matcher.group(0)
                }
            }

            // Look for words like "Date:" followed by a date
            if (line.lowercase(Locale.getDefault()).contains("date")) {
                for (pattern in datePatterns) {
                    val matcher = pattern.matcher(line)
                    if (matcher.find()) {
                        return matcher.group(0)
                    }
                }
            }
        }

        // If we couldn't find a date, try to guess today's date
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    // Extract total amount
    private fun extractTotalAmount(text: String): Double {
        // Look for lines containing words like "TOTAL", "AMOUNT", etc.
        val totalIndicators = listOf(
            "TOTAL", "Total", "total",
            "AMOUNT", "Amount", "amount",
            "GRAND TOTAL", "Grand Total",
            "SUBTOTAL", "Subtotal",
            "RM", "MYR"
        )

        // Regex for currency amount (handles Malaysian Ringgit format)
        val amountRegexes = listOf(
            // RM 123.45 or RM123.45
            Regex("RM\\s*([0-9,]+\\.?\\d*)"),
            // Just 123.45 after words like TOTAL
            Regex("(?:TOTAL|Total|AMOUNT|Amount)\\s*:?\\s*([0-9,]+\\.?\\d*)"),
            // Any decimal number in the text
            Regex("([0-9,]+\\.\\d{2})")
        )

        val lines = text.split("\n")

        // First, look for lines with both an indicator and a number
        for (line in lines) {
            val lowerLine = line.lowercase(Locale.getDefault())

            // Check if the line contains a total indicator
            val containsIndicator = totalIndicators.any { indicator ->
                lowerLine.contains(indicator.lowercase(Locale.getDefault()))
            }

            if (containsIndicator) {
                // Try each regex pattern to find an amount
                for (regex in amountRegexes) {
                    val match = regex.find(line)
                    if (match != null) {
                        val amountStr = match.groupValues[1].replace(",", "")
                        try {
                            return amountStr.toDouble()
                        } catch (e: NumberFormatException) {
                            // Continue to next match if this one fails
                        }
                    }
                }
            }
        }

        // If we didn't find a total with an indicator, look for the largest decimal number
        val allAmounts = mutableListOf<Double>()
        for (line in lines) {
            val matches = Regex("(\\d+[.,]\\d{2})").findAll(line)
            for (match in matches) {
                try {
                    val amount = match.value.replace(",", ".").toDouble()
                    allAmounts.add(amount)
                } catch (e: NumberFormatException) {
                    // Skip invalid numbers
                }
            }
        }

        // Return the largest amount found, which is likely the total
        return allAmounts.maxOrNull() ?: 0.0
    }

    // Extract line items (products/services with prices)
    private fun extractLineItems(text: String): List<LineItem> {
        val items = mutableListOf<LineItem>()
        val lines = text.split("\n")

        // Regex to match a price - looks for patterns like 12.34 or RM 12.34
        val priceRegex = Regex("(?:RM\\s*)?(\\d+[.,]\\d{2})")

        for (line in lines) {
            // Skip likely header or footer lines
            if (line.contains("RECEIPT") ||
                line.contains("INVOICE") ||
                line.contains("TEL:") ||
                line.contains("THANK YOU") ||
                line.contains("CUSTOMER") ||
                line.trim().isEmpty()) {
                continue
            }

            // Look for prices in the line
            val prices = priceRegex.findAll(line).toList()
            if (prices.isNotEmpty()) {
                // Use the last price in the line (typically the item price)
                val lastPrice = prices.last()
                val priceValue = try {
                    lastPrice.groupValues[1].replace(",", ".").toDouble()
                } catch (e: NumberFormatException) {
                    continue // Skip if we can't parse the price
                }

                // Get the description (text before the price)
                val descEnd = lastPrice.range.first
                val description = if (descEnd > 0) line.substring(0, descEnd).trim() else "Item"

                // Add the item if description is not empty and price is reasonable
                if (description.isNotEmpty() && priceValue > 0 && priceValue < 10000) {
                    items.add(LineItem(description = description, amount = priceValue))
                }
            }
        }

        return items
    }

    // Determine the likely tax category based on content
    private fun determineCategory(merchantName: String, items: List<LineItem>): String {
        // Check merchant name first
        val merchantLower = merchantName.lowercase(Locale.getDefault())

        // Medical
        if (merchantLower.contains("clinic") ||
            merchantLower.contains("hospital") ||
            merchantLower.contains("pharmacy") ||
            merchantLower.contains("medical") ||
            merchantLower.contains("healthcare") ||
            merchantLower.contains("doctor") ||
            merchantLower.contains("guardian") ||
            merchantLower.contains("watson")) {
            return "Medical"
        }

        // Education
        if (merchantLower.contains("school") ||
            merchantLower.contains("college") ||
            merchantLower.contains("university") ||
            merchantLower.contains("education") ||
            merchantLower.contains("books") ||
            merchantLower.contains("stationery") ||
            merchantLower.contains("tuition") ||
            merchantLower.contains("popular")) {
            return "Education"
        }

        // Sports Equipment
        if (merchantLower.contains("sports") ||
            merchantLower.contains("fitness") ||
            merchantLower.contains("gym") ||
            merchantLower.contains("athletic") ||
            merchantLower.contains("decathlon")) {
            return "Sport Equipment"
        }

        // Childcare
        if (merchantLower.contains("childcare") ||
            merchantLower.contains("nursery") ||
            merchantLower.contains("kindergarten") ||
            merchantLower.contains("child care") ||
            merchantLower.contains("daycare")) {
            return "Childcare"
        }

        // Check items if merchant name doesn't give clues
        for (item in items) {
            val itemLower = item.description.lowercase(Locale.getDefault())

            if (itemLower.contains("medicine") ||
                itemLower.contains("medical") ||
                itemLower.contains("health") ||
                itemLower.contains("doctor") ||
                itemLower.contains("treatment")) {
                return "Medical"
            }

            if (itemLower.contains("book") ||
                itemLower.contains("course") ||
                itemLower.contains("class") ||
                itemLower.contains("tuition") ||
                itemLower.contains("education")) {
                return "Education"
            }

            if (itemLower.contains("donation") ||
                itemLower.contains("donate") ||
                itemLower.contains("charity")) {
                return "Donations"
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