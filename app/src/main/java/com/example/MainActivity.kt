package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.example.ui.AliToolsApp
import com.example.viewmodel.AliToolsViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AliToolsViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup state ViewModel
        viewModel = ViewModelProvider(this)[AliToolsViewModel::class.java]
        
        // Request launcher permissions on launch
        val list = mutableListOf(android.Manifest.permission.CAMERA)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.add(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            list.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            list.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        try {
            permissionLauncher.launch(list.toTypedArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Edge-to-edge rendering
        enableEdgeToEdge()
        
        setContent {
            AliToolsApp(viewModel = viewModel)
        }
    }
}
