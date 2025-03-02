package com.example.smarttax_ver1.screen

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.example.smarttax_ver1.AppUtil
import com.example.smarttax_ver1.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadReceiptScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text(
                        text = "Upload Receipt",
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
                    IconButton(onClick = { /* Already on this screen */ }) {
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
        UploadReceiptContent(
            modifier = modifier.padding(innerPadding),
            navController = navController
        )
    }
}

@Composable
fun UploadReceiptContent(
    modifier: Modifier = Modifier,
    navController: NavHostController
) {
    val context = LocalContext.current

    // State for tracking the selected image URI
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // State for loading indicator
    var isLoading by remember { mutableStateOf(false) }

    // Create a launcher for content selection (gallery)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            // After selecting from gallery, navigate to receipt summary or processing screen
            // For now, just show a toast
            AppUtil.showToast(context, "Receipt selected from gallery")
            // TODO: Process the receipt image and navigate to summary screen
            // navController.navigate("receiptSummary")
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && selectedImageUri != null) {
            // Image captured successfully
            AppUtil.showToast(context, "Receipt captured with camera")
            // TODO: Process the receipt image and navigate to summary screen
            // navController.navigate("receiptSummary")
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted, proceed with camera
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, "New Receipt")
                put(MediaStore.Images.Media.DESCRIPTION, "From Smart Tax Handler Camera")
            }

            val imageUri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            if (imageUri != null) {
                selectedImageUri = imageUri
                cameraLauncher.launch(imageUri)
            } else {
                AppUtil.showToast(context, "Failed to create image file")
            }
        } else {
            // Permission denied
            AppUtil.showToast(context, "Camera permission is required to take pictures")
        }
    }

    // We've moved the camera launch logic directly into the permission callback

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "How would you like to add your receipt?",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(bottom = 40.dp)
        )

        // Display selected image if available
        if (selectedImageUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Image(
                    painter = rememberAsyncImagePainter(selectedImageUri),
                    contentDescription = "Selected Receipt",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Button(
                onClick = {
                    isLoading = true
                    // Navigate to the receipt summary screen with the image URI
                    if (selectedImageUri != null) {
                        val encodedUri = Uri.encode(selectedImageUri.toString())
                        navController.navigate("receiptSummary/${encodedUri}")
                    } else {
                        AppUtil.showToast(context, "No image selected")
                        isLoading = false
                    }
//                    // Simulate processing delay
//                    android.os.Handler().postDelayed({
//                        isLoading = false
//                        navController.navigate("home") // Change to receipt summary when available
//                    }, 2000)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading && selectedImageUri != null
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Process Receipt", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { selectedImageUri = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Choose Another Receipt", fontSize = 18.sp)
            }
        } else {
            // Option Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Take photo option
                UploadOption(
                    icon = Icons.Default.Face,
                    title = "Take Photo",
                    description = "Capture receipt using camera",
                    onClick = {
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) -> {
                                // Permission already granted, proceed with camera
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.Images.Media.TITLE, "New Receipt")
                                    put(MediaStore.Images.Media.DESCRIPTION, "From Smart Tax Handler Camera")
                                }

                                val imageUri = context.contentResolver.insert(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    contentValues
                                )

                                if (imageUri != null) {
                                    selectedImageUri = imageUri
                                    cameraLauncher.launch(imageUri)
                                } else {
                                    AppUtil.showToast(context, "Failed to create image file")
                                }
                            }
                            else -> {
                                // Request permission
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Upload from gallery option
                UploadOption(
                    icon = Icons.Default.AddCircle,
                    title = "From Gallery",
                    description = "Select receipt from gallery",
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Illustration
            Image(
                painter = painterResource(id = R.drawable.login), // Replace with a receipt image resource
                contentDescription = "Upload Receipt Illustration",
                modifier = Modifier
                    .size(240.dp)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun UploadOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = TextStyle(
                    fontSize = 12.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}