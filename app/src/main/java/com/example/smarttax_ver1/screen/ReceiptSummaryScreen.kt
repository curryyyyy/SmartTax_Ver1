package com.example.smarttax_ver1.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.smarttax_ver1.AppUtil
import com.example.smarttax_ver1.OCR.LineItem
import com.example.smarttax_ver1.OCR.ReceiptData
import com.example.smarttax_ver1.viewmodel.ReceiptViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptSummaryScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    receiptViewModel: ReceiptViewModel = viewModel(),
    imageUri: Uri
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text(
                        text = "Receipt Summary",
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    // Home
                    IconButton(onClick = { navController.navigate("home") }) {
                        Icon(Icons.Filled.Home, contentDescription = "Home")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Upload receipt
                    IconButton(onClick = { navController.navigate("uploadReceipt") }) {
                        Icon(
                            Icons.Filled.AddCircle,
                            contentDescription = "Upload Receipt",
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Profile
                    IconButton(onClick = { navController.navigate("editProfile") }) {
                        Icon(
                            Icons.Filled.Face,
                            contentDescription = "Profile",
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        ReceiptSummaryContent(
            modifier = modifier.padding(innerPadding),
            navController = navController,
            receiptViewModel = receiptViewModel,
            imageUri = imageUri,
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
fun ReceiptSummaryContent(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    receiptViewModel: ReceiptViewModel,
    imageUri: Uri,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val receiptData = receiptViewModel.receiptData
    val isLoading = receiptViewModel.isLoading
    val errorMessage = receiptViewModel.errorMessage

    // Track if OCR corrections were made
    var userMadeCorrections by remember { mutableStateOf(false) }

    // Track original values for feedback
    var originalData by remember { mutableStateOf<ReceiptData?>(null) }

    if (receiptData == null && !isLoading) {
        // No data yet, initiate processing
        receiptViewModel.processReceiptImage(context, imageUri)
    }

    // Save the original data for comparison
    LaunchedEffect(receiptData) {
        if (receiptData != null && originalData == null) {
            originalData = receiptData
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            // Show loading indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(60.dp),
                        strokeWidth = 5.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Processing receipt...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else if (errorMessage != null) {
            // Show error message
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { navController.navigateUp() }) {
                    Text("Go Back")
                }
            }
        } else if (receiptData != null) {
            // Show receipt data for confirmation
            EditableReceiptData(
                receiptData = receiptData,
                originalData = originalData,
                onDataChanged = { updatedData, hasUserCorrections ->
                    receiptViewModel.updateReceiptData(updatedData)
                    userMadeCorrections = hasUserCorrections
                },
                onSave = {
                    // If user made corrections, submit them for training
                    if (userMadeCorrections && originalData != null) {
                        scope.launch {
                            receiptViewModel.submitUserCorrections(
                                originalText = originalData!!.rawText,
                                correctedText = receiptData.rawText,
                                imageUri = imageUri,
                                context = context
                            )

                            // Show feedback message to user
                            snackbarHostState.showSnackbar("Thank you for improving our OCR!")
                        }
                    }

                    // Save receipt to Firebase
                    receiptViewModel.saveReceiptToFirebase(imageUri) { success, message ->
                        if (success) {
                            AppUtil.showToast(context, "Receipt saved successfully")
                            navController.navigate("home") {
                                popUpTo("uploadReceipt") { inclusive = true }
                            }
                        } else {
                            AppUtil.showToast(context, message ?: "Failed to save receipt")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun EditableReceiptData(
    receiptData: ReceiptData,
    originalData: ReceiptData?,
    onDataChanged: (ReceiptData, Boolean) -> Unit,
    onSave: () -> Unit
) {
    var merchantName by remember { mutableStateOf(receiptData.merchantName) }
    var date by remember { mutableStateOf(receiptData.date) }
    var totalAmount by remember { mutableStateOf(receiptData.totalAmount.toString()) }
    var category by remember { mutableStateOf(receiptData.category) }
    var lineItems by remember { mutableStateOf(receiptData.lineItems) }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    // Feedback indicators
    var showFeedbackSection by remember { mutableStateOf(false) }
    var userMadeCorrections by remember { mutableStateOf(false) }
    var userFeedback by remember { mutableStateOf("") }
    var feedbackSubmitted by remember { mutableStateOf(false) }

    // Helper function to compare line items
    fun areLineItemsEqual(list1: List<LineItem>, list2: List<LineItem>): Boolean {
        if (list1.size != list2.size) return false
        return list1.zip(list2).all { (item1, item2) ->
            item1.description == item2.description &&
                    item1.amount == item2.amount
        }
    }

    // Check if user has made any corrections
    fun checkForCorrections() {
        // Only check if we have the original data to compare against
        if (originalData != null) {
            userMadeCorrections = merchantName != originalData.merchantName ||
                    date != originalData.date ||
                    totalAmount != originalData.totalAmount.toString() ||
                    category != originalData.category ||
                    !areLineItemsEqual(lineItems, originalData.lineItems)
        }
    }



    // Tax relief categories in Malaysia
    val categories = listOf(
        "Medical",
        "Education",
        "Lifestyle Expenses",
        "Sport Equipment",
        "Childcare",
        "Donations"
    )

    // Function to update the receipt data
    fun updateReceiptData() {
        val updatedData = ReceiptData(
            merchantName = merchantName,
            date = date,
            totalAmount = totalAmount.toDoubleOrNull() ?: 0.0,
            lineItems = lineItems,
            category = category,
            rawText = receiptData.rawText
        )

        // Check for user corrections
        checkForCorrections()

        // Pass updated data and correction status
        onDataChanged(updatedData, userMadeCorrections)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Review Receipt Details",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Merchant name
                    OutlinedTextField(
                        value = merchantName,
                        onValueChange = {
                            merchantName = it
                            updateReceiptData()
                        },
                        label = { Text("Merchant") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Date
                    OutlinedTextField(
                        value = date,
                        onValueChange = {
                            date = it
                            updateReceiptData()
                        },
                        label = { Text("Date") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Total amount
                    OutlinedTextField(
                        value = totalAmount,
                        onValueChange = {
                            totalAmount = it
                            updateReceiptData()
                        },
                        label = { Text("Total Amount (RM)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        prefix = { Text("RM ") }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category dropdown
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { /* No direct input */ },
                            label = { Text("Category") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { showCategoryDropdown = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Select Category")
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = showCategoryDropdown,
                            onDismissRequest = { showCategoryDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            categories.forEach { categoryOption ->
                                DropdownMenuItem(
                                    text = { Text(categoryOption) },
                                    onClick = {
                                        category = categoryOption
                                        showCategoryDropdown = false
                                        updateReceiptData()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Show correction feedback section if user made changes
            if (userMadeCorrections && !feedbackSubmitted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ThumbUp,
                                contentDescription = "Feedback",
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = "You've corrected some data! This helps us improve.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            TextButton(
                                onClick = {
                                    showFeedbackSection = !showFeedbackSection
                                }
                            ) {
                                Text(if (showFeedbackSection) "Hide" else "Add Feedback")
                            }
                        }

                        if (showFeedbackSection) {
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = userFeedback,
                                onValueChange = { userFeedback = it },
                                label = { Text("Additional feedback (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(
                                onClick = {
                                    // Here you would save the feedback
                                    feedbackSubmitted = true
                                    showFeedbackSection = false
                                }
                            ) {
                                Text("Submit Feedback")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Line Items",
                    style = MaterialTheme.typography.titleMedium
                )

                TextButton(
                    onClick = {
                        lineItems = lineItems + LineItem("New Item", 0.0)
                        updateReceiptData()
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Item")
                }
            }

            Divider()
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Line items
        items(lineItems) { item ->
            LineItemRow(
                item = item,
                onItemChanged = { updatedItem ->
                    lineItems = lineItems.map { if (it === item) updatedItem else it }
                    updateReceiptData()
                },
                onItemDeleted = { deletedItem ->
                    lineItems = lineItems.filter { it !== deletedItem }
                    updateReceiptData()
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))

            // Total amount display
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total Amount:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "RM ${
                            try {
                                "%.2f".format(totalAmount.toDoubleOrNull() ?: 0.0)
                            } catch (e: Exception) {
                                "0.00"
                            }
                        }",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Save Receipt", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun LineItemRow(
    item: LineItem,
    onItemChanged: (LineItem) -> Unit,
    onItemDeleted: (LineItem) -> Unit
) {
    var description by remember { mutableStateOf(item.description) }
    var amount by remember { mutableStateOf(item.amount.toString()) }

    // Function to update the item
    fun updateItem() {
        val updatedItem = LineItem(
            description = description,
            amount = amount.toDoubleOrNull() ?: 0.0
        )
        onItemChanged(updatedItem)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Description field
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    updateItem()
                },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Amount field
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        updateItem()
                    },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    prefix = { Text("RM ") }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Delete button
                IconButton(
                    onClick = { onItemDeleted(item) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete item",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}