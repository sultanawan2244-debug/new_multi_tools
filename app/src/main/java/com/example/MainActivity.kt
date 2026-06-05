package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.AliToolsApp
import com.example.viewmodel.AliToolsViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AliToolsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup state ViewModel
        viewModel = ViewModelProvider(this)[AliToolsViewModel::class.java]
        
        // Edge-to-edge rendering
        enableEdgeToEdge()
        
        setContent {
            AliToolsApp(viewModel = viewModel)
        }
    }
}
