package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FeedbackMessage
import com.example.data.SettingsManager
import com.example.data.Tool
import com.example.data.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class Screen {
    object Home : Screen()
    object Search : Screen()
    object Favorites : Screen()
    object Settings : Screen()
    object AboutUs : Screen()
    object ContactUs : Screen()
    object MessageHistory : Screen()
    
    // Tool Detail screens
    object AICompanion : Screen()
    object AIImageCreator : Screen()
    object ImageCompressor : Screen()
    object PDFMerger : Screen()
    object QRScannerGenerator : Screen()
    object CamScanner : Screen()
    
    // Generic fallback for some other tools
    data class GeneralToolScreen(val toolId: String) : Screen()
}

data class ToastMessage(
    val type: String, // "success", "info"
    val message: String
)

class AliToolsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.feedbackDao()
    private val settingsManager = SettingsManager(application)
    
    // Navigation / Tabs
    var activeTab by mutableStateOf("Home") // "Home", "Search", "Favorites", "Settings"
    var currentScreen by mutableStateOf<Screen>(Screen.Home)
    private val screenStack = mutableListOf<Screen>()
    
    // Toast state
    var toast by mutableStateOf<ToastMessage?>(null)
        private set
    
    fun showToast(type: String, message: String) {
        toast = ToastMessage(type, message)
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (toast?.message == message) {
                toast = null
            }
        }
    }
    
    fun dismissToast() {
        toast = null
    }

    fun navigateTo(screen: Screen) {
        if (currentScreen != screen) {
            screenStack.add(currentScreen)
            currentScreen = screen
        }
    }

    fun navigateBack(): Boolean {
        if (screenStack.isNotEmpty()) {
            currentScreen = screenStack.removeAt(screenStack.size - 1)
            return true
        }
        if (currentScreen != Screen.Home) {
            currentScreen = Screen.Home
            activeTab = "Home"
            return true
        }
        return false // Can exit app
    }
    
    fun resetNavigation() {
        screenStack.clear()
        currentScreen = Screen.Home
        activeTab = "Home"
    }

    // Settings & Keys States
    var isDarkTheme by mutableStateOf(false)
    var geminiKey by mutableStateOf("")
    var openRouterKey by mutableStateOf("")
    var hfKey by mutableStateOf("")
    var aiProvider by mutableStateOf("gemini") // "gemini", "openrouter"
    var selectedOrModel by mutableStateOf("openai/gpt-4o")
    var favorites by mutableStateOf(setOf<String>())
    
    init {
        // Load settings values
        val savedTheme = settingsManager.getTheme()
        isDarkTheme = if (savedTheme == "system") {
            // Check system
            false // fallback default light, can dynamic toggling in App theme
        } else {
            savedTheme == "dark"
        }
        geminiKey = settingsManager.getGeminiApiKey()
        openRouterKey = settingsManager.getOpenRouterApiKey()
        hfKey = settingsManager.getHfApiKey()
        aiProvider = settingsManager.getAiProvider()
        selectedOrModel = settingsManager.getOpenRouterModel()
        favorites = settingsManager.getFavorites()
    }

    fun toggleTheme() {
        isDarkTheme = !isDarkTheme
        settingsManager.setTheme(if (isDarkTheme) "dark" else "light")
        showToast("info", "Theme shifted to ${if (isDarkTheme) "Dark" else "Light"} Mode")
    }

    fun toggleFav(toolId: String) {
        val isFav = settingsManager.toggleFavorite(toolId)
        favorites = settingsManager.getFavorites()
        if (isFav) {
            showToast("success", "Added to favorites!")
        } else {
            showToast("info", "Removed from favorites")
        }
    }

    fun saveApiKeys(gemKey: String, orKey: String, provider: String = aiProvider) {
        geminiKey = gemKey
        openRouterKey = orKey
        aiProvider = provider
        settingsManager.setGeminiApiKey(gemKey)
        settingsManager.setOpenRouterApiKey(orKey)
        settingsManager.setAiProvider(provider)
        showToast("success", "API configurations saved successfully!")
    }

    fun saveHfKey(key: String) {
        hfKey = key
        settingsManager.setHfApiKey(key)
        showToast("success", "HF API Key saved!")
    }

    fun resetAllSettings() {
        settingsManager.clearAll()
        favorites = emptySet()
        isDarkTheme = false
        geminiKey = ""
        openRouterKey = ""
        hfKey = ""
        aiProvider = "gemini"
        selectedOrModel = "openai/gpt-4o"
        viewModelScope.launch {
            dao.clearMessages()
        }
        showToast("info", "App settings and message history reset!")
    }

    // Room Feedback Storage
    val feedbackMessages: StateFlow<List<FeedbackMessage>> = dao.getAllMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    var contactName by mutableStateOf("")
    var contactEmail by mutableStateOf("")
    var contactSubject by mutableStateOf("")
    var contactMessage by mutableStateOf("")

    fun submitFeedback() {
        if (contactName.isBlank() || contactEmail.isBlank() || contactMessage.isBlank()) {
            showToast("info", "Please fill in all required fields.")
            return
        }
        val msg = FeedbackMessage(
            name = contactName,
            email = contactEmail,
            subject = contactSubject,
            message = contactMessage
        )
        viewModelScope.launch {
            dao.insertMessage(msg)
            withContext(Dispatchers.Main) {
                showToast("success", "Feedback saved! View in message history.")
                contactName = ""
                contactEmail = ""
                contactSubject = ""
                contactMessage = ""
            }
        }
    }

    // Search and All Tools
    var searchQuery by mutableStateOf("")
    var activeCategoryChip by mutableStateOf("All")

    fun getFilteredTools(): List<Tool> {
        return ToolRegistry.tools.filter { tool ->
            val matchesCategory = if (activeCategoryChip == "All") {
                true
            } else {
                tool.category.equals(activeCategoryChip, ignoreCase = true)
            }
            val matchesSearch = tool.name.contains(searchQuery, ignoreCase = true) ||
                    tool.desc.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    // --- AI COMPANION STATES & CALLS ---
    var aiPrompt by mutableStateOf("")
    var aiResult by mutableStateOf("")
    var isAiLoading by mutableStateOf(false)
    var aiError by mutableStateOf<String?>(null)
    var attachedFileMime by mutableStateOf<String?>(null) // simulated attached file
    var attachedFileName by mutableStateOf<String?>(null)

    fun runAiCompanion() {
        if (aiPrompt.isBlank() && attachedFileMime == null) {
            showToast("info", "Please enter a message or attach a file.")
            return
        }
        val keyToUse = if (aiProvider == "gemini") geminiKey else openRouterKey
        if (keyToUse.isBlank()) {
            aiError = "API key missing for ${aiProvider.uppercase()}. Enter below to generate response."
            showToast("info", "API Key setup required.")
            return
        }

        aiError = null
        isAiLoading = true
        aiResult = ""

        viewModelScope.launch(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            try {
                if (aiProvider == "gemini") {
                    // Google Gemini REST Call
                    // URL: https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={key}
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$keyToUse"
                    
                    val reqJson = JSONObject().apply {
                        val contentsArray = JSONArray().apply {
                            val msgObj = JSONObject().apply {
                                val partsArray = JSONArray().apply {
                                    val textPartVal = if (attachedFileMime != null) {
                                        "[Attached File: $attachedFileName ($attachedFileMime)]\n\n$aiPrompt"
                                    } else {
                                        aiPrompt
                                    }
                                    put(JSONObject().apply { put("text", textPartVal) })
                                }
                                put("parts", partsArray)
                            }
                            put(msgObj)
                        }
                        put("contents", contentsArray)
                    }

                    val requestBody = reqJson.toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())

                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()

                    client.newCall(request).execute().use { response ->
                        val respBody = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val json = JSONObject(respBody)
                            val candidates = json.optJSONArray("candidates")
                            val text = candidates?.optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.optJSONObject(0)
                                ?.optString("text")

                            withContext(Dispatchers.Main) {
                                aiResult = text ?: "Empty response returned from Gemini."
                                isAiLoading = false
                            }
                        } else {
                            val errMsg = JSONObject(respBody).optJSONObject("error")?.optString("message") 
                                ?: "Response code ${response.code}"
                            withContext(Dispatchers.Main) {
                                aiError = "Gemini Error: $errMsg"
                                isAiLoading = false
                            }
                        }
                    }
                } else {
                    // OpenRouter API Call
                    // POST https://openrouter.ai/api/v1/chat/completions
                    val url = "https://openrouter.ai/api/v1/chat/completions"
                    val reqJson = JSONObject().apply {
                        put("model", selectedOrModel)
                        val messagesArray = JSONArray().apply {
                            val msgDict = JSONObject().apply {
                                val textPart = if (attachedFileMime != null) {
                                    "[File Attached: $attachedFileName]\n\n$aiPrompt"
                                } else {
                                    aiPrompt
                                }
                                put("role", "user")
                                put("content", textPart)
                            }
                            put(msgDict)
                        }
                        put("messages", messagesArray)
                    }

                    val requestBody = reqJson.toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())

                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $keyToUse")
                        .header("Content-Type", "application/json")
                        .post(requestBody)
                        .build()

                    client.newCall(request).execute().use { response ->
                        val respBody = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val json = JSONObject(respBody)
                            val choices = json.optJSONArray("choices")
                            val message = choices?.optJSONObject(0)?.optJSONObject("message")
                            val contentText = message?.optString("content")

                            withContext(Dispatchers.Main) {
                                aiResult = contentText ?: "Empty response text."
                                isAiLoading = false
                            }
                        } else {
                            val errMsg = JSONObject(respBody).optJSONObject("error")?.optString("message")
                                ?: "Response code ${response.code}"
                            withContext(Dispatchers.Main) {
                                aiError = "OpenRouter Error: $errMsg"
                                isAiLoading = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    aiError = "Connection Error: ${e.localizedMessage ?: "Please try again."}"
                    isAiLoading = false
                }
            }
        }
    }

    // --- AI IMAGE CREATOR STATES & CALLS ---
    var hfPrompt by mutableStateOf("")
    var hfNegativePrompt by mutableStateOf("")
    var hfWidth by mutableStateOf("1024")
    var hfHeight by mutableStateOf("1024")
    var hfSteps by mutableStateOf(4)
    var hfGuidance by mutableStateOf(3.5f)
    var isImageLoading by mutableStateOf(false)
    var imageCreatorError by mutableStateOf<String?>(null)
    var resultImageBitmap by mutableStateOf<Bitmap?>(null)

    fun runImageCreator() {
        if (hfPrompt.isBlank()) {
            showToast("info", "Please enter a descriptive prompt.")
            return
        }
        if (hfKey.isBlank()) {
            imageCreatorError = "HF API key is missing. Set it below to generate FLUX.1 models."
            showToast("info", "API Key setup required.")
            return
        }

        imageCreatorError = null
        isImageLoading = true
        resultImageBitmap = null

        viewModelScope.launch(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build()

            try {
                // Endpoint POST https://router.huggingface.co/hf-inference/models/black-forest-labs/FLUX.1-schnell
                val url = "https://router.huggingface.co/hf-inference/models/black-forest-labs/FLUX.1-schnell"
                
                val reqJson = JSONObject().apply {
                    put("inputs", hfPrompt)
                    put("parameters", JSONObject().apply {
                        if (hfNegativePrompt.isNotEmpty()) {
                            put("negative_prompt", hfNegativePrompt)
                        }
                        put("width", hfWidth.toIntOrNull() ?: 1024)
                        put("height", hfHeight.toIntOrNull() ?: 1024)
                        put("num_inference_steps", hfSteps)
                        put("guidance_scale", hfGuidance.toDouble())
                    })
                }

                val requestBody = reqJson.toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $hfKey")
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null) {
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            withContext(Dispatchers.Main) {
                                resultImageBitmap = bitmap
                                isImageLoading = false
                                if (bitmap == null) {
                                    imageCreatorError = "Failed to parse FLUX.1 returned image blob."
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                imageCreatorError = "Empty image returned."
                                isImageLoading = false
                            }
                        }
                    } else {
                        val errString = response.body?.string() ?: "Inference error code ${response.code}"
                        val errJson = try { JSONObject(errString) } catch (e: Exception) { null }
                        val errMsg = errJson?.optString("error") ?: errString
                        withContext(Dispatchers.Main) {
                            imageCreatorError = "HF Error: $errMsg"
                            isImageLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    imageCreatorError = "Inference Connection Error: ${e.localizedMessage}"
                    isImageLoading = false
                }
            }
        }
    }

    // --- IMAGE COMPRESSOR STATES & ACTIONS ---
    var sourceImageBitmap by mutableStateOf<Bitmap?>(null)
    var compQuality by mutableStateOf(80f)
    var compFormat by mutableStateOf("JPG") // "JPG", "PNG", "WEBP"
    var isCompressing by mutableStateOf(false)
    var compressedImageUri by mutableStateOf<String?>(null)
    var sourceFileSizeValue by mutableStateOf("0 KB")
    var targetFileSizeValue by mutableStateOf("0 KB")

    fun selectCompressImage(bitmap: Bitmap, sizeString: String) {
        sourceImageBitmap = bitmap
        sourceFileSizeValue = sizeString
        targetFileSizeValue = "0 KB"
        compressedImageUri = null
    }

    fun compressImageLocal(context: android.content.Context) {
        val src = sourceImageBitmap ?: return
        isCompressing = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val stream = ByteArrayOutputStream()
                    val format = when (compFormat) {
                        "PNG" -> Bitmap.CompressFormat.PNG
                        "WEBP" -> Bitmap.CompressFormat.WEBP
                        else -> Bitmap.CompressFormat.JPEG
                    }
                    val quality = compQuality.toInt()
                    src.compress(format, quality, stream)
                    val resultBytes = stream.toByteArray()
                    
                    // Save to local cache dir to export
                    val cacheFile = File(context.cacheDir, "compressed_result.${compFormat.lowercase()}")
                    FileOutputStream(cacheFile).use { out ->
                        out.write(resultBytes)
                    }
                    
                    val calculatedSize = resultBytes.size / 1024
                    val sizeStr = if (calculatedSize > 1024) {
                        String.format("%.2f MB", calculatedSize / 1024.0)
                    } else {
                        "$calculatedSize KB"
                    }

                    withContext(Dispatchers.Main) {
                        targetFileSizeValue = sizeStr
                        compressedImageUri = cacheFile.absolutePath
                        isCompressing = false
                        showToast("success", "Image compressed to $sizeStr!")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isCompressing = false
                        showToast("info", "Error compressing image: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    // --- PDF MERGER STATES ---
    var selectedPdfFiles by mutableStateOf<List<FileItem>>(emptyList())
    data class FileItem(val name: String, val sizeStr: String, val file: File? = null)
    var isMergingPdfs by mutableStateOf(false)
    var mergedPdfPath by mutableStateOf<String?>(null)

    fun addMockPdf(name: String, size: String) {
        selectedPdfFiles = selectedPdfFiles + FileItem(name, size)
    }

    fun clearPdfs() {
        selectedPdfFiles = emptyList()
        mergedPdfPath = null
    }

    fun mergePdfsLocally(context: android.content.Context) {
        if (selectedPdfFiles.size < 2) {
            showToast("info", "Include at least 2 PDF documents to merge.")
            return
        }
        isMergingPdfs = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Simulate merging of documents
                kotlinx.coroutines.delay(2000)
                val targetFile = File(context.cacheDir, "merged_alitools_result.pdf")
                FileOutputStream(targetFile).use { out ->
                    out.write("MOCK PDF HEADER %PDF-1.4\n".toByteArray())
                    selectedPdfFiles.forEach { item ->
                        out.write("%% Content from ${item.name}\n".toByteArray())
                    }
                    out.write("%%EOF\n".toByteArray())
                }
                withContext(Dispatchers.Main) {
                    mergedPdfPath = targetFile.absolutePath
                    isMergingPdfs = false
                    showToast("success", "Successfully merged ${selectedPdfFiles.size} PDFs")
                }
            }
        }
    }

    // --- QR STATES ---
    var qrTextRaw by mutableStateOf("https://wa.me/923216957139")
    var decodedQrText by mutableStateOf("")

    // --- CAMSCANNER STATES ---
    var scScanFilter by mutableStateOf("Original") // "Original", "Auto", "Gray", "Magic", "Binarize"
    var scBrightness by mutableStateOf(0f)         // -100f to 100f (default 0)
    var scContrast by mutableStateOf(1.0f)         // 0.5f to 2.5f (default 1.0)
    var cameraMode by mutableStateOf(false)

    // In-depth Multi-page scanning support
    val scannedPages = androidx.compose.runtime.mutableStateListOf<ScannedPage>()
    var selectedPageIndex by androidx.compose.runtime.mutableIntStateOf(-1)
    var isSavingScan by mutableStateOf(false)

    fun addScannedPage(bitmap: android.graphics.Bitmap) {
        val newPage = ScannedPage(
            original = bitmap,
            processed = bitmap
        )
        scannedPages.add(newPage)
        selectedPageIndex = scannedPages.size - 1
        // Reset default settings for active edits
        scScanFilter = "Original"
        scBrightness = 0f
        scContrast = 1.0f
    }

    fun deleteScannedPage(index: Int) {
        if (index in scannedPages.indices) {
            scannedPages.removeAt(index)
            if (scannedPages.isEmpty()) {
                selectedPageIndex = -1
            } else {
                selectedPageIndex = (index - 1).coerceAtLeast(0)
                val activePage = scannedPages[selectedPageIndex]
                scScanFilter = activePage.filterName
                scBrightness = activePage.brightness
                scContrast = activePage.contrast
            }
        }
    }

    fun rotateActivePage() {
        val idx = selectedPageIndex
        if (idx in scannedPages.indices) {
            val page = scannedPages[idx]
            val matrix = android.graphics.Matrix()
            matrix.postRotate(90f)
            val rotatedOrig = android.graphics.Bitmap.createBitmap(
                page.original, 0, 0, page.original.width, page.original.height, matrix, true
            )
            val rotatedProc = android.graphics.Bitmap.createBitmap(
                page.processed, 0, 0, page.processed.width, page.processed.height, matrix, true
            )
            scannedPages[idx] = page.copy(
                original = rotatedOrig,
                processed = rotatedProc,
                rotationDegrees = (page.rotationDegrees + 90) % 360
            )
            showToast("success", "Rotated 90° clockwise")
        }
    }

    fun applyActiveFiltersAndAdjustments() {
        val idx = selectedPageIndex
        if (idx in scannedPages.indices) {
            val page = scannedPages[idx]
            // Crop first if crop fields are applied, otherwise use original
            val baseBitmap = if (page.cropLeft > 0f || page.cropTop > 0f || page.cropRight < 1f || page.cropBottom < 1f) {
                com.example.ui.components.ImageProcessor.cropBitmap(
                    page.original, page.cropLeft, page.cropTop, page.cropRight, page.cropBottom
                )
            } else {
                page.original
            }
            
            // Apply enhancements via ImageProcessor
            val out = com.example.ui.components.ImageProcessor.applyEffects(
                baseBitmap, scScanFilter, scBrightness, scContrast
            )
            scannedPages[idx] = page.copy(
                processed = out,
                filterName = scScanFilter,
                brightness = scBrightness,
                contrast = scContrast
            )
        }
    }

    fun applyCropToActivePage(left: Float, top: Float, right: Float, bottom: Float) {
        val idx = selectedPageIndex
        if (idx in scannedPages.indices) {
            val page = scannedPages[idx]
            
            // Crop base original
            val cropped = com.example.ui.components.ImageProcessor.cropBitmap(
                page.original, left, top, right, bottom
            )
            // Re-apply filter on the newly cropped image
            val processed = com.example.ui.components.ImageProcessor.applyEffects(
                cropped, scScanFilter, scBrightness, scContrast
            )

            scannedPages[idx] = page.copy(
                processed = processed,
                cropLeft = left,
                cropTop = top,
                cropRight = right,
                cropBottom = bottom
            )
            showToast("success", "Crop boundaries applied!")
        }
    }

    fun autoDetectEdgesForActivePage() {
        val idx = selectedPageIndex
        if (idx in scannedPages.indices) {
            val page = scannedPages[idx]
            val bounds = com.example.ui.components.ImageProcessor.detectEdges(page.original)
            applyCropToActivePage(bounds[0], bounds[1], bounds[2], bounds[3])
        }
    }

    fun exportDocument(context: android.content.Context, format: String, onComplete: (File) -> Unit) {
        if (scannedPages.isEmpty()) {
            showToast("info", "No scanned pages to export.")
            return
        }
        isSavingScan = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
                    val file = when (format) {
                        "PDF" -> {
                            val pdfDocument = android.graphics.pdf.PdfDocument()
                            for (i in scannedPages.indices) {
                                val page = scannedPages[i]
                                val bitmap = page.processed
                                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                                    bitmap.width, bitmap.height, i + 1
                                ).create()
                                val pdfPage = pdfDocument.startPage(pageInfo)
                                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                                pdfDocument.finishPage(pdfPage)
                            }
                            val dest = File(filesDir, "AliScanner_Doc_$timestamp.pdf")
                            FileOutputStream(dest).use { out ->
                                pdfDocument.writeTo(out)
                            }
                            pdfDocument.close()
                            dest
                        }
                        "PNG" -> {
                            // PNG output (saves first page or generates single PNG)
                            val dest = File(filesDir, "AliScanner_Img_$timestamp.png")
                            val bitmap = scannedPages[selectedPageIndex.coerceIn(0, scannedPages.size - 1)].processed
                            FileOutputStream(dest).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                            }
                            dest
                        }
                        else -> { // "JPG"
                            val dest = File(filesDir, "AliScanner_Img_$timestamp.jpg")
                            val bitmap = scannedPages[selectedPageIndex.coerceIn(0, scannedPages.size - 1)].processed
                            FileOutputStream(dest).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                            }
                            dest
                        }
                    }
                    withContext(Dispatchers.Main) {
                        isSavingScan = false
                        showToast("success", "Exported successfully to: ${file.name}")
                        onComplete(file)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isSavingScan = false
                        showToast("info", "Failed to export: ${e.localizedMessage}")
                    }
                }
            }
        }
    }
}

data class ScannedPage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val original: android.graphics.Bitmap,
    val processed: android.graphics.Bitmap,
    val filterName: String = "Original",
    val brightness: Float = 0f,
    val contrast: Float = 1.0f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val rotationDegrees: Int = 0
)

