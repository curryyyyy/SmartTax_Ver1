package com.example.smarttax_ver1

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smarttax_ver1.screen.AuthScreen
import com.example.smarttax_ver1.screen.CategoryScreen
import com.example.smarttax_ver1.screen.EditProfileScreen
import com.example.smarttax_ver1.screen.HomeScreen
import com.example.smarttax_ver1.screen.LoginScreen
import com.example.smarttax_ver1.screen.ProfileScreen
import com.example.smarttax_ver1.screen.ReceiptSummaryScreen
import com.example.smarttax_ver1.screen.RegisterScreen
import com.example.smarttax_ver1.screen.UploadReceiptScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun AppNavigation(modifier: Modifier = Modifier){
    val navController = rememberNavController()

    val isLoggedIn = Firebase.auth.currentUser != null
    val firstPage = if(isLoggedIn) "home" else "auth"

    NavHost(navController = navController, startDestination = firstPage) {
        composable("auth"){
            AuthScreen(modifier, navController)
        }

        composable("login"){
            LoginScreen(modifier, navController)
        }

        composable("register"){
            RegisterScreen(modifier, navController)
        }

        composable("home"){
            HomeScreen(modifier, navController)
        }

        composable("profile"){
            ProfileScreen(modifier, navController)
        }

        composable("editProfile"){
            EditProfileScreen(modifier, navController)
        }

        composable("category"){
            CategoryScreen(modifier)
        }

        composable("uploadReceipt") {
            UploadReceiptScreen(modifier, navController)
        }

        // New route for receipt summary
        composable("receiptSummary/{imageUri}") { backStackEntry ->
            val imageUriString = backStackEntry.arguments?.getString("imageUri")
            val imageUri = if (imageUriString != null) Uri.parse(imageUriString) else Uri.EMPTY
            ReceiptSummaryScreen(modifier, navController, imageUri = imageUri)
        }
    }
}


