package com.vaibhav.emicalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vaibhav.emicalc.ui.EmiApp
import com.vaibhav.emicalc.ui.theme.EmiCalcTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as EmiCalcApplication).container

        setContent {
            EmiCalcTheme {
                EmiApp(container = container)
            }
        }
    }
}
