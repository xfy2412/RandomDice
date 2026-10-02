package com.xfy.randomdice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xfy.randomdice.ui.RandomDiceApp
import com.xfy.randomdice.ui.theme.RandomDiceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RandomDiceTheme {
                RandomDiceApp()
            }
        }
    }
}
