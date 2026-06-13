package com.prplegryn.moegirl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.prplegryn.moegirl.ui.MoeGirlApp
import com.prplegryn.moegirl.ui.theme.MoeGirlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoeGirlTheme {
                MoeGirlApp()
            }
        }
    }
}

