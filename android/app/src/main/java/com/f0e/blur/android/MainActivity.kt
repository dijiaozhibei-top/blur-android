package com.f0e.blur.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.f0e.blur.android.ui.BlurApp
import com.f0e.blur.android.ui.BlurTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlurTheme {
                BlurApp(viewModel)
            }
        }
    }
}
