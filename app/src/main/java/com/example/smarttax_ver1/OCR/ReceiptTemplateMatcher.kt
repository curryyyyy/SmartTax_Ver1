package com.example.smarttax_ver1.OCR

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

/**
 * Receipt template matcher that uses predefined patterns for known merchants
 */
class ReceiptTemplateMatcher {
    private val firestore = FirebaseFirestore.getInstance()
    private val templates = mutableMapOf<String, ReceiptTemplate>()

    suspend fun initialize(context: Context) {
        try {
            // Load templates from assets
            loadDefaultTemplates(context)

            // Update from Firestore
            updateTemplatesFromFirestore()
        } catch (e: Exception) {
            Log.e("ReceiptTemplateMatcher", "Error initializing templates", e)
        }
    }

    private fun loadDefaultTemplates(context: Context) {
        try {
            // Load from assets/receipt_templates.json
            val inputStream = context.assets.open("receipt_templates.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()

            val json = String(buffer, Charsets.UTF_8)
            val jsonObject = JSONObject(json)
            val templatesArray = jsonObject.getJSONArray("templates")

            for (i in 0 until templatesArray.length()) {
                val templateObj = templatesArray.getJSONObject(i)
                val merchantName = templateObj.getString("merchantName")
                val template = ReceiptTemplate(
                    merchantName = merchantName,
                    headerPattern = templateObj.getString("headerPattern"),
                    datePattern = templateObj.getString("datePattern"),
                    totalPattern = templateObj.getString("totalPattern"),
                    itemPattern = templateObj.getString("itemPattern"),
                    category = templateObj.getString("category")
                )
                templates[merchantName.lowercase()] = template
            }
        } catch (e: Exception) {
            Log.e("ReceiptTemplateMatcher", "Error loading default templates", e)
        }
    }

    private suspend fun updateTemplatesFromFirestore() {
        try {
            val snapshot = firestore.collection("receiptTemplates").get().await()

            for (doc in snapshot.documents) {
                val merchantName = doc.getString("merchantName") ?: continue
                val template = ReceiptTemplate(
                    merchantName = merchantName,
                    headerPattern = doc.getString("headerPattern") ?: "",
                    datePattern = doc.getString("datePattern") ?: "",
                    totalPattern = doc.getString("totalPattern") ?: "",
                    itemPattern = doc.getString("itemPattern") ?: "",
                    category = doc.getString("category") ?: ""
                )
                templates[merchantName.lowercase()] = template
            }
        } catch (e: Exception) {
            Log.e("ReceiptTemplateMatcher", "Error updating templates from Firestore", e)
        }
    }

    // Try to match a receipt text against known templates
    fun matchReceipt(receiptText: String): ReceiptData? {
        // First, try to identify the merchant
        val merchantName = identifyMerchant(receiptText)

        // If we found a match and have a template for it, use template-based extraction
        val template = templates[merchantName.lowercase()]
        if (template != null) {
            try {
                Log.d("ReceiptTemplateMatcher", "Found template match for $merchantName")

                // Extract data using the template patterns
                val extractedDate = extractWithPattern(receiptText, template.datePattern)
                val extractedTotal = extractTotalWithPattern(receiptText, template.totalPattern)
                val extractedItems = extractItemsWithPattern(receiptText, template.itemPattern)

                return ReceiptData(
                    merchantName = template.merchantName, // Use the canonical name from template
                    date = extractedDate ?: "Unknown Date",
                    totalAmount = extractedTotal ?: 0.0,
                    lineItems = extractedItems,
                    category = template.category,
                    rawText = receiptText
                )
            } catch (e: Exception) {
                Log.e("ReceiptTemplateMatcher", "Error applying template for $merchantName", e)
            }
        }

        return null // No match found
    }

    // Identify merchant from receipt text
    private fun identifyMerchant(receiptText: String): String {
        val lines = receiptText.split("\n")

        // Try each template header pattern
        for ((merchantName, template) in templates) {
            val pattern = Pattern.compile(template.headerPattern, Pattern.CASE_INSENSITIVE)

            // Check first few lines for header match
            for (i in 0 until minOf(5, lines.size)) {
                val matcher = pattern.matcher(lines[i])
                if (matcher.find()) {
                    return merchantName
                }
            }

            // Also check the full text in case the header is anywhere
            val fullTextMatcher = pattern.matcher(receiptText)
            if (fullTextMatcher.find()) {
                return merchantName
            }
        }

        return ""
    }

    // Extract text using regex pattern
    private fun extractWithPattern(text: String, pattern: String): String? {
        try {
            val compiledPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = compiledPattern.matcher(text)

            if (matcher.find() && matcher.groupCount() >= 1) {
                return matcher.group(1)
            }
        } catch (e: Exception) {
            Log.e("ReceiptTemplateMatcher", "Error extracting with pattern: $pattern", e)
        }

        return null
    }

    // Extract total amount using pattern
    private fun extractTotalWithPattern(text: String, pattern: String): Double? {
        val extracted = extractWithPattern(text, pattern)

        if (extracted != null) {
            try {
                // Clean up the amount string
                val cleanAmount = extracted
                    .replace("RM", "")
                    .replace("MYR", "")
                    .replace(" ", "")
                    .replace(",", ".")
                    .trim()

                return cleanAmount.toDouble()
            } catch (e: NumberFormatException) {
                Log.e("ReceiptTemplateMatcher", "Error parsing amount: $extracted", e)
            }
        }

        return null
    }

    // Extract items using pattern
    private fun extractItemsWithPattern(text: String, pattern: String): List<LineItem> {
        val items = mutableListOf<LineItem>()

        try {
            val compiledPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
            val matcher = compiledPattern.matcher(text)

            while (matcher.find()) {
                if (matcher.groupCount() >= 2) {
                    val description = matcher.group(1)?.trim() ?: continue
                    val amountStr = matcher.group(2)?.trim() ?: continue

                    try {
                        // Clean up the amount string
                        val cleanAmount = amountStr
                            .replace("RM", "")
                            .replace("MYR", "")
                            .replace(" ", "")
                            .replace(",", ".")
                            .trim()

                        val amount = cleanAmount.toDouble()
                        items.add(LineItem(description = description, amount = amount))
                    } catch (e: NumberFormatException) {
                        Log.e("ReceiptTemplateMatcher", "Error parsing item amount: $amountStr", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ReceiptTemplateMatcher", "Error extracting items with pattern: $pattern", e)
        }

        return items
    }

    // A template contains patterns for a specific merchant's receipts
    data class ReceiptTemplate(
        val merchantName: String,
        val headerPattern: String,  // Pattern to identify the merchant
        val datePattern: String,    // Pattern to extract date, with group 1 as the date
        val totalPattern: String,   // Pattern to extract total, with group 1 as the amount
        val itemPattern: String,    // Pattern to extract items, with group 1 as description and group 2 as amount
        val category: String        // Default tax category
    )
}