package com.example.smarttax_ver1.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttax_ver1.model.ExpenseItem
import com.example.smarttax_ver1.model.ReceiptModel
import com.example.smarttax_ver1.repository.ReceiptRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CategoryViewModel : ViewModel() {
    private val repository = ReceiptRepository()

    // UI State
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var showDeleteConfirmation by mutableStateOf(false)
    var receiptToDelete by mutableStateOf<ReceiptModel?>(null)
    var expenseToDelete by mutableStateOf<ExpenseItem?>(null)

    // Edit state
    var isEditingReceipt by mutableStateOf(false)
    var currentEditReceipt by mutableStateOf<ReceiptModel?>(null)
    var editMerchantName by mutableStateOf("")
    var editTotal by mutableStateOf("")
    var editDate by mutableStateOf("")
    var editCategory by mutableStateOf("")
    var editExpenseItems by mutableStateOf<List<ExpenseItem>>(emptyList())

    // Data state
    var categoryData by mutableStateOf<Map<String, List<ReceiptModel>>>(emptyMap())
    var categorySummary by mutableStateOf<Map<String, Double>>(emptyMap())
    var expandedCategories by mutableStateOf<Set<String>>(emptySet())

    // Available categories (same as in other screens for consistency)
    val availableCategories = listOf(
        "Lifestyle Expenses",
        "Childcare",
        "Sport Equipment",
        "Donations",
        "Medical",
        "Education"
    )

    init {
        loadCategoryData()
    }

    fun loadCategoryData() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                // Get all receipts
                val receiptsResult = repository.getUserReceipts()
                if (receiptsResult.isFailure) {
                    throw receiptsResult.exceptionOrNull() ?: Exception("Failed to load receipts")
                }

                val receipts = receiptsResult.getOrNull() ?: emptyList()

                if (receipts.isEmpty()) {
                    categoryData = emptyMap()
                    categorySummary = emptyMap()
                    errorMessage = "No receipts found. Add some receipts to see categories."
                    return@launch
                }

                // Group receipts by category
                val groupedReceipts = receipts.groupBy { it.category }

                // Calculate summary (total amount per category)
                val summary = groupedReceipts.mapValues { (_, receipts) ->
                    receipts.sumOf { it.total }
                }

                // Update state
                categoryData = groupedReceipts
                categorySummary = summary

                // If this is the first load, expand the first category by default
                if (expandedCategories.isEmpty() && groupedReceipts.isNotEmpty()) {
                    expandedCategories = setOf(groupedReceipts.keys.first())
                }

            } catch (e: Exception) {
                Log.e("CategoryViewModel", "Error loading category data", e)
                errorMessage = e.localizedMessage ?: "Failed to load category data"
                categoryData = emptyMap()
                categorySummary = emptyMap()
            } finally {
                isLoading = false
            }
        }
    }

    // Toggle category expansion
    fun toggleCategoryExpansion(category: String) {
        expandedCategories = if (expandedCategories.contains(category)) {
            expandedCategories - category
        } else {
            expandedCategories + category
        }
    }

    // Format date for display
    fun formatDate(date: Date): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return dateFormat.format(date)
    }

    // Format currency for display
    fun formatCurrency(amount: Double): String {
        return String.format("RM %.2f", amount)
    }

    // Parse date from string
    fun parseDate(dateStr: String): Date {
        return try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            dateFormat.parse(dateStr) ?: Date()
        } catch (e: Exception) {
            Log.e("CategoryViewModel", "Error parsing date: $dateStr", e)
            Date()
        }
    }

    // Start editing receipt
    fun startEditingReceipt(receipt: ReceiptModel) {
        currentEditReceipt = receipt
        editMerchantName = receipt.merchantName
        editTotal = receipt.total.toString()
        editDate = formatDate(receipt.date)
        editCategory = receipt.category
        editExpenseItems = receipt.items ?: emptyList()
        isEditingReceipt = true
    }

    // Cancel editing
    fun cancelEditing() {
        isEditingReceipt = false
        currentEditReceipt = null
        clearEditFields()
    }

    // Clear edit fields
    private fun clearEditFields() {
        editMerchantName = ""
        editTotal = ""
        editDate = ""
        editCategory = ""
        editExpenseItems = emptyList()
    }

    // Save edited receipt
    fun saveEditedReceipt(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentReceipt = currentEditReceipt ?: return

        viewModelScope.launch {
            isLoading = true

            try {
                // Parse values
                val totalAmount = editTotal.replace(",", ".").toDoubleOrNull() ?: 0.0
                val receiptDate = parseDate(editDate)

                // Create updated receipt
                val updatedReceipt = currentReceipt.copy(
                    merchantName = editMerchantName,
                    total = totalAmount,
                    date = receiptDate,
                    category = editCategory,
                    items = editExpenseItems,
                    updatedAt = Timestamp.now()
                )

                // Update receipt in Firestore
                val result = repository.updateReceipt(updatedReceipt)
                if (result.isFailure) {
                    throw result.exceptionOrNull() ?: Exception("Failed to update receipt")
                }

                // Refresh data
                loadCategoryData()

                // Reset edit state
                isEditingReceipt = false
                currentEditReceipt = null
                clearEditFields()

                onSuccess()
            } catch (e: Exception) {
                Log.e("CategoryViewModel", "Error updating receipt", e)
                onError(e.localizedMessage ?: "Failed to update receipt")
            } finally {
                isLoading = false
            }
        }
    }

    // Confirm delete receipt
    fun confirmDeleteReceipt(receipt: ReceiptModel) {
        receiptToDelete = receipt
        showDeleteConfirmation = true
    }

    // Delete receipt
    fun deleteReceipt(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val receipt = receiptToDelete ?: return

        viewModelScope.launch {
            isLoading = true

            try {
                // Delete receipt from Firestore
                val result = repository.deleteReceipt(receipt.id)
                if (result.isFailure) {
                    throw result.exceptionOrNull() ?: Exception("Failed to delete receipt")
                }

                // Refresh data
                loadCategoryData()

                // Reset delete state
                showDeleteConfirmation = false
                receiptToDelete = null

                onSuccess()
            } catch (e: Exception) {
                Log.e("CategoryViewModel", "Error deleting receipt", e)
                onError(e.localizedMessage ?: "Failed to delete receipt")
            } finally {
                isLoading = false
            }
        }
    }

    // Cancel delete
    fun cancelDelete() {
        showDeleteConfirmation = false
        receiptToDelete = null
        expenseToDelete = null
    }

    // Update expense item name
    fun updateExpenseItemName(expenseItem: ExpenseItem, newName: String) {
        editExpenseItems = editExpenseItems.map {
            if (it.id == expenseItem.id) it.copy(description = newName) else it
        }
    }

    // Update expense item amount
    fun updateExpenseItemAmount(expenseItem: ExpenseItem, newAmount: Double) {
        editExpenseItems = editExpenseItems.map {
            if (it.id == expenseItem.id) it.copy(amount = newAmount) else it
        }

        // Recalculate total
        updateTotalFromItems()
    }

    // Update expense item category
    fun updateExpenseItemCategory(expenseItem: ExpenseItem, newCategory: String) {
        editExpenseItems = editExpenseItems.map {
            if (it.id == expenseItem.id) it.copy(category = newCategory) else it
        }
    }

    // Recalculate total amount from all expense items
    private fun updateTotalFromItems() {
        val sum = editExpenseItems.sumOf { it.amount }
        editTotal = String.format(Locale.getDefault(), "%.2f", sum)
    }

    // Confirm delete expense item
    fun confirmDeleteExpenseItem(expenseItem: ExpenseItem) {
        expenseToDelete = expenseItem
        showDeleteConfirmation = true
    }

    // Delete expense item
    fun deleteExpenseItem() {
        val expense = expenseToDelete ?: return

        // Remove expense from list
        editExpenseItems = editExpenseItems.filter { it.id != expense.id }

        // Recalculate total
        updateTotalFromItems()

        // Reset delete state
        showDeleteConfirmation = false
        expenseToDelete = null
    }
}