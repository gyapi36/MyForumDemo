package com.example.myforumapplication.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Main : Screen("main")
    object WritePost : Screen("writepost")
}