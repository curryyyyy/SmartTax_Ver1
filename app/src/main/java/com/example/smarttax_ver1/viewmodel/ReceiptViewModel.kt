package com.example.smarttax_ver1.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttax_ver1.OCR.OCRProcessor
import com.example.smarttax_ver1.OCR.ReceiptData
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ReceiptViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
    private val storage = Firebase.storage
    private val ocrProcessor = OCRProcessor()

    // UI states
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var receiptData by mutableStateOf<ReceiptData?>(null)
    var processingComplete by mutableStateOf(false)

    // Process image from gallery or camera
    fun processReceiptImage(context: Context, imageUri: Uri) {
        isLoading = true
        errorMessage = null
        processingComplete = false

        viewModelScope.launch {
            try {
                // Process the image to extract receipt data
                val data = ocrProcessor.processImageFromUri(context, imageUri)
                receiptData = data
                processingComplete = true
                Log.d("ReceiptViewModel", "Receipt processed successfully: $data")
            } catch (e: Exception) {
                errorMessage = "Failed to process receipt: ${e.message}"
                Log.e("ReceiptViewModel", "Error processing receipt", e)
            } finally {
                isLoading = false
            }
        }
    }

    // Save receipt data to Firebase
    fun saveReceiptToFirebase(imageUri: Uri, onComplete: (Boolean, String?) -> Unit) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            onComplete(false, "User not logged in")
            return
        }

        val currentData = receiptData
        if (currentData == null) {
            onComplete(false, "No receipt data to save")
            return
        }

        isLoading = true

        viewModelScope.launch {
            try {
                // 1. Upload image to Firebase Storage
                val imageFileName = "receipts/${userId}/${UUID.randomUUID()}.jpg"
                val storageRef = storage.reference.child(imageFileName)

                storageRef.putFile(imageUri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // 2. Save receipt data to Firestore
                val receiptId = UUID.randomUUID().toString()
                val receiptMap = hashMapOf(
                    "id" to receiptId,
                    "userId" to userId,
                    "merchantName" to currentData.merchantName,
                    "date" to currentData.date,
                    "totalAmount" to currentData.totalAmount,
                    "category" to currentData.category,
                    "imageUrl" to downloadUrl,
                    "rawText" to currentData.rawText,
                    "timestamp" to System.currentTimeMillis()
                )

                // Save line items as a subcollection
                firestore.collection("receipts").document(receiptId)
                    .set(receiptMap).await()

                // Save each line item
                currentData.lineItems.forEachIndexed { index, item ->
                    val itemId = UUID.randomUUID().toString()
                    val itemMap = hashMapOf(
                        "id" to itemId,
                        "receiptId" to receiptId,
                        "description" to item.description,
                        "amount" to item.amount,
                        "order" to index
                    )

                    firestore.collection("receipts")
                        .document(receiptId)
                        .collection("items")
                        .document(itemId)
                        .set(itemMap).await()
                }

                // 3. Update category totals
                updateCategoryTotal(userId, currentData.category, currentData.totalAmount)

                onComplete(true, null)
                Log.d("ReceiptViewModel", "Receipt saved successfully")
            } catch (e: Exception) {
                onComplete(false, "Failed to save receipt: ${e.message}")
                Log.e("ReceiptViewModel", "Error saving receipt", e)
            } finally {
                isLoading = false
            }
        }
    }

    // Update the total for a category
    private suspend fun updateCategoryTotal(userId: String, category: String, amount: Double) {
        try {
            // Get current category document
            val categoryDoc = firestore.collection("users")
                .document(userId)
                .collection("categories")
                .whereEqualTo("name", category)
                .get()
                .await()

            if (categoryDoc.documents.isEmpty()) {
                // Category doesn't exist, create it
                val newCategory = hashMapOf(
                    "name" to category,
                    "totalAmount" to amount,
                    "timestamp" to System.currentTimeMillis()
                )

                firestore.collection("users")
                    .document(userId)
                    .collection("categories")
                    .add(newCategory)
                    .await()
            } else {
                // Category exists, update it
                val doc = categoryDoc.documents[0]
                val currentTotal = doc.getDouble("totalAmount") ?: 0.0
                val newTotal = currentTotal + amount

                firestore.collection("users")
                    .document(userId)
                    .collection("categories")
                    .document(doc.id)
                    .update("totalAmount", newTotal)
                    .await()
            }
        } catch (e: Exception) {
            Log.e("ReceiptViewModel", "Error updating category total", e)
        }
    }

    // Add this method to save corrections
    fun submitUserCorrections(originalText: String, correctedText: String, imageUri: Uri, context: Context) {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                // 1. Upload the original image to storage if not already there
                val imageFileName = "training/${userId}/${UUID.randomUUID()}.jpg"
                val storageRef = storage.reference.child(imageFileName)

                // Upload image
                val inputStream = context.contentResolver.openInputStream(imageUri)
                storageRef.putStream(inputStream!!).await()
                val imageUrl = storageRef.downloadUrl.await().toString()

                // 2. Save original OCR result and user corrections to Firestore
                val correctionData = hashMapOf(
                    "userId" to userId,
                    "originalText" to originalText,
                    "correctedText" to correctedText,
                    "imageUrl" to imageUrl,
                    "timestamp" to System.currentTimeMillis()
                )

                firestore.collection("ocrCorrections")
                    .add(correctionData)
                    .await()

                Log.d("ReceiptViewModel", "Saved OCR correction data for training")
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Error saving OCR correction data", e)
            }
        }
    }

    // Update receipt data for manual corrections
    fun updateReceiptData(updatedData: ReceiptData) {
        receiptData = updatedData
    }

    // Clear the current receipt data
    fun clearReceiptData() {
        receiptData = null
        processingComplete = false
        errorMessage = null
    }
}