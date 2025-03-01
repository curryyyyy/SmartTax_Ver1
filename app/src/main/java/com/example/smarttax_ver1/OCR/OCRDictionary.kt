package com.example.smarttax_ver1.OCR

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages a custom dictionary of corrections to improve OCR accuracy
 */
class OCRDictionary {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // In-memory cache of corrections
    private val merchantCorrections = mutableMapOf<String, String>()
    private val commonTerms = mutableMapOf<String, String>()

    // Initialize by loading dictionary from local storage and Firestore
    suspend fun initialize(context: Context) {
        try {
            // First load from local storage (faster)
            loadFromLocalStorage(context)

            // Then update from Firestore (more up-to-date)
            updateFromFirestore()
        } catch (e: Exception) {
            Log.e("OCRDictionary", "Error initializing dictionary", e)
        }
    }

    // Load dictionary from SharedPreferences
    private fun loadFromLocalStorage(context: Context) {
        val prefs = context.getSharedPreferences("ocr_dictionary", Context.MODE_PRIVATE)

        // Load merchant corrections
        val merchantJson = prefs.getString("merchant_corrections", null)
        if (!merchantJson.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val map = Gson().fromJson<Map<String, String>>(merchantJson, type)
                if (map != null) {
                    merchantCorrections.putAll(map)
                }
            } catch (e: Exception) {
                Log.e("OCRDictionary", "Error parsing merchant corrections", e)
            }
        }

        // Load common terms
        val termsJson = prefs.getString("common_terms", null)
        if (!termsJson.isNullOrEmpty()) {
            try {
                val map = com.google.gson.Gson().fromJson(termsJson, Map::class.java) as? Map<String, String>
                if (map != null) {
                    commonTerms.putAll(map)
                }
            } catch (e: Exception) {
                Log.e("OCRDictionary", "Error parsing common terms", e)
            }
        }
    }

    // Update dictionary from Firestore
    private suspend fun updateFromFirestore() {
        try {
            // Get global dictionary (shared across all users)
            val globalDictSnapshot = firestore.collection("ocrDictionary")
                .document("global")
                .get()
                .await()

            // Get user-specific dictionary
            val userId = auth.currentUser?.uid
            val userDictSnapshot = if (userId != null) {
                firestore.collection("ocrDictionary")
                    .document(userId)
                    .get()
                    .await()
            } else null

            // Process global dictionary
            val globalMerchants = globalDictSnapshot.get("merchants") as? Map<String, String>
            val globalTerms = globalDictSnapshot.get("terms") as? Map<String, String>

            if (globalMerchants != null) merchantCorrections.putAll(globalMerchants)
            if (globalTerms != null) commonTerms.putAll(globalTerms)

            // Process user dictionary (overwrites global if there's a conflict)
            if (userDictSnapshot != null) {
                val userMerchants = userDictSnapshot.get("merchants") as? Map<String, String>
                val userTerms = userDictSnapshot.get("terms") as? Map<String, String>

                if (userMerchants != null) merchantCorrections.putAll(userMerchants)
                if (userTerms != null) commonTerms.putAll(userTerms)
            }
        } catch (e: Exception) {
            Log.e("OCRDictionary", "Error updating from Firestore", e)
        }
    }

    // Save a new correction to both local storage and Firestore
    suspend fun addCorrection(originalText: String, correctedText: String, type: CorrectionType) {
        try {
            // Update in-memory dictionary
            when (type) {
                CorrectionType.MERCHANT -> merchantCorrections[originalText] = correctedText
                CorrectionType.TERM -> commonTerms[originalText] = correctedText
            }

            // Update user's dictionary in Firestore
            val userId = auth.currentUser?.uid ?: return

            val updates = when (type) {
                CorrectionType.MERCHANT -> mapOf("merchants.$originalText" to correctedText)
                CorrectionType.TERM -> mapOf("terms.$originalText" to correctedText)
            }

            firestore.collection("ocrDictionary")
                .document(userId)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .await()

            // Also send to a collection for admin review (to potentially add to global dictionary)
            val correctionData = hashMapOf(
                "userId" to userId,
                "originalText" to originalText,
                "correctedText" to correctedText,
                "type" to type.name,
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection("correctionSuggestions")
                .add(correctionData)
                .await()

        } catch (e: Exception) {
            Log.e("OCRDictionary", "Error adding correction", e)
        }
    }

    // Apply dictionary corrections to OCR results
    fun applyCorrections(text: String): String {
        var correctedText = text

        // Apply merchant name corrections (using fuzzy matching)
        for ((original, correction) in merchantCorrections) {
            if (correctedText.contains(original, ignoreCase = true)) {
                correctedText = correctedText.replace(original, correction, ignoreCase = true)
            }
        }

        // Apply common term corrections
        for ((original, correction) in commonTerms) {
            if (correctedText.contains(original, ignoreCase = true)) {
                correctedText = correctedText.replace(original, correction, ignoreCase = true)
            }
        }

        return correctedText
    }

    // Apply corrections to a specific merchant name with fuzzy matching
    fun correctMerchantName(merchantName: String): String {
        // Try to find closest match
        val closest = merchantCorrections.keys.minByOrNull { levenshteinDistance(it.lowercase(), merchantName.lowercase()) }
        if (closest != null && levenshteinDistance(closest.lowercase(), merchantName.lowercase()) < 3) {
            return merchantCorrections[closest] ?: merchantName
        }
        return merchantName
    }

    // Levenshtein distance for fuzzy matching
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in 0..s2.length) costs[i] = i

        for (i in 1..s1.length) {
            costs[0] = i
            var nw = i - 1
            for (j in 1..s2.length) {
                val cj = min(1 + min(costs[j], costs[j - 1]),
                    if (s1[i - 1] == s2[j - 1]) nw else nw + 1)
                nw = costs[j]
                costs[j] = cj
            }
        }
        return costs[s2.length]
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b

    enum class CorrectionType {
        MERCHANT, TERM
    }
}