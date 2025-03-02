package com.example.smarttax_ver1

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.example.smarttax_ver1.screen.TaxInsightsScreen
import com.example.smarttax_ver1.screen.UploadReceiptScreen
import com.example.smarttax_ver1.viewmodel.ReceiptViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun AppNavigation(modifier: Modifier = Modifier){
    val navController = rememberNavController()
    val receiptViewModel: ReceiptViewModel = viewModel()

    val isLoggedIn = Firebase.auth.currentUser != null
    val firstPage = if(isLoggedIn) "home" else "auth"

    NavHost(navController = navController, startDestination = firstPage) {
        composable("auth") {
            AuthScreen(modifier, navController)
        }

        composable("login") {
            LoginScreen(modifier, navController)
        }

        composable("register") {
            RegisterScreen(modifier, navController)
        }

        composable("home") {
            HomeScreen(modifier, navController)
        }

        composable("profile") {
            ProfileScreen(modifier, navController)
        }

        composable("editProfile") {
            EditProfileScreen(modifier, navController)
        }

        composable("category") {
            CategoryScreen(modifier, navController)
        }

        composable("uploadReceipt") {
            UploadReceiptScreen(modifier, navController, receiptViewModel)
        }

        composable("receiptSummary") {
            ReceiptSummaryScreen(modifier, navController, receiptViewModel)
        }

        composable("taxInsights") {
            TaxInsightsScreen(modifier, navController, receiptViewModel)
        }

    }
}


