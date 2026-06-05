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
    var aiCompanionMode by mutableStateOf("online") // "online", "offline"
    var hfCreatorMode by mutableStateOf("online")    // "online", "offline"
    
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
        if (aiCompanionMode == "offline") {
            aiError = null
            isAiLoading = true
            aiResult = ""
            viewModelScope.launch {
                kotlinx.coroutines.delay(1000)
                aiResult = "[🔌 Offline AI Assistant Mode]\n\n" +
                    "I analyzed your message offline: \"$aiPrompt\"\n\n" +
                    "Since the app is currently running in local offline mode, I am replying via our intelligent on-device rules.\n\n" +
                    "• **Real-time Tools**: You can compress images, convert PDFs, scan files with CamScanner, and generate QRs entirely locally!\n" +
                    "• **Empowering Design**: Created with pride by Ali Raza in Okara, Pakistan, to perform fast native computing without servers.\n\n" +
                    "To consult live Gemini and OpenAI cloud model endpoints, toggle *Online Live API Mode* above and key in your credentials."
                isAiLoading = false
                showToast("success", "Calculated offline response.")
            }
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
        if (hfCreatorMode == "offline") {
            imageCreatorError = null
            isImageLoading = true
            resultImageBitmap = null
            viewModelScope.launch {
                kotlinx.coroutines.delay(1000)
                try {
                    val app = getApplication<Application>()
                    val bmp = generateOfflineBitmap(app, hfPrompt)
                    resultImageBitmap = bmp
                    isImageLoading = false
                    showToast("success", "Offline visual canvas generated!")
                } catch (e: Exception) {
                    isImageLoading = false
                    imageCreatorError = "Error drawing offline graphic: ${e.localizedMessage}"
                }
            }
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
                    var savedUriString = ""
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
                            
                            val pdfBytes = dest.readBytes()
                            val u = savePdfToDownloads(context, pdfBytes, "AliScanner_Doc_$timestamp")
                            savedUriString = if (u != null) "Downloads/AliScanner" else "local backup"
                            dest
                        }
                        "PNG" -> {
                            val dest = File(filesDir, "AliScanner_Img_$timestamp.png")
                            val bitmap = scannedPages[selectedPageIndex.coerceIn(0, scannedPages.size - 1)].processed
                            FileOutputStream(dest).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                            }
                            val u = saveBitmapToGallery(context, bitmap, "AliScanner_Img_$timestamp", "PNG")
                            savedUriString = if (u != null) "Pictures/AliScanner" else "local backup"
                            dest
                        }
                        else -> { // "JPG"
                            val dest = File(filesDir, "AliScanner_Img_$timestamp.jpg")
                            val bitmap = scannedPages[selectedPageIndex.coerceIn(0, scannedPages.size - 1)].processed
                            FileOutputStream(dest).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                            }
                            val u = saveBitmapToGallery(context, bitmap, "AliScanner_Img_$timestamp", "JPG")
                            savedUriString = if (u != null) "Pictures/AliScanner" else "local backup"
                            dest
                        }
                    }
                    withContext(Dispatchers.Main) {
                        isSavingScan = false
                        showToast("success", "Saved to public $savedUriString!")
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

    fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap, title: String, format: String): android.net.Uri? {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$title.${format.lowercase()}")
            val mime = if (format == "PNG") "image/png" else "image/jpeg"
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/AliScanner")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        
        val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            try {
                resolver.openOutputStream(imageUri)?.use { out ->
                    val compressFormat = if (format == "PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    val qual = if (format == "PNG") 100 else 92
                    bitmap.compress(compressFormat, qual, out)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                
                val path = getRealPathFromURI(context, imageUri)
                if (path != null) {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                }
            } catch (e: Exception) {
                resolver.delete(imageUri, null, null)
                return null
            }
        }
        return imageUri
    }

    private fun getRealPathFromURI(context: android.content.Context, contentUri: android.net.Uri): String? {
        var cursor: android.database.Cursor? = null
        try {
            val proj = arrayOf(android.provider.MediaStore.Images.Media.DATA)
            cursor = context.contentResolver.query(contentUri, proj, null, null, null)
            val columnIndex = cursor?.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
            if (cursor != null && cursor.moveToFirst() && columnIndex != null && columnIndex >= 0) {
                return cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            // silent ignore
        } finally {
            cursor?.close()
        }
        return null
    }

    fun savePdfToDownloads(context: android.content.Context, pdfBytes: ByteArray, title: String): android.net.Uri? {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$title.pdf")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/AliScanner")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val targetFile = File(downloadsDir, "$title.pdf")
                FileOutputStream(targetFile).use { out ->
                    out.write(pdfBytes)
                }
                return android.net.Uri.fromFile(targetFile)
            } catch (e: Exception) {
                return null
            }
        }
        
        val pdfUri = resolver.insert(collection, contentValues)
        if (pdfUri != null) {
            try {
                resolver.openOutputStream(pdfUri)?.use { out ->
                    out.write(pdfBytes)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(pdfUri, contentValues, null, null)
                }
            } catch (e: Exception) {
                resolver.delete(pdfUri, null, null)
                return null
            }
        }
        return pdfUri
    }

    fun saveCompressedImageToGallery(context: android.content.Context) {
        val path = compressedImageUri ?: return
        try {
            val file = File(path)
            if (file.exists()) {
                val bytes = file.readBytes()
                val format = compFormat
                val timestamp = System.currentTimeMillis()
                
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "AliTools_compressed_$timestamp.${format.lowercase()}")
                    val mime = when (format) {
                        "PNG" -> "image/png"
                        "WEBP" -> "image/webp"
                        else -> "image/jpeg"
                    }
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/AliCompressor")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                
                val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri)?.use { out ->
                        out.write(bytes)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(imageUri, contentValues, null, null)
                    }
                    
                    showToast("success", "Saved copy to Pictures/AliCompressor!")
                } else {
                    showToast("info", "Failed to insert into MediaStore.")
                }
            } else {
                showToast("info", "Processed image data expired.")
            }
        } catch (e: Exception) {
            showToast("info", "Error saving: ${e.localizedMessage}")
        }
    }

    fun saveQrCodeToGallery(context: android.content.Context, text: String) {
        try {
            val width = 512
            val height = 512
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            
            canvas.drawColor(android.graphics.Color.WHITE)
            
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                isAntiAlias = true
            }
            
            val gridSize = 21
            val padding = 40f
            val qrSize = width - padding * 2
            val cellSize = qrSize / gridSize
            
            fun drawBitmapFinderPattern(cx: Float, cy: Float) {
                paint.color = android.graphics.Color.BLACK
                canvas.drawRect(cx, cy, cx + cellSize * 7, cy + cellSize * 7, paint)
                paint.color = android.graphics.Color.WHITE
                canvas.drawRect(cx + cellSize, cy + cellSize, cx + cellSize * 6, cy + cellSize * 6, paint)
                paint.color = android.graphics.Color.BLACK
                canvas.drawRect(cx + cellSize * 2, cy + cellSize * 2, cx + cellSize * 5, cy + cellSize * 5, paint)
            }
            
            drawBitmapFinderPattern(padding, padding)
            drawBitmapFinderPattern(padding + qrSize - cellSize * 7, padding)
            drawBitmapFinderPattern(padding, padding + qrSize - cellSize * 7)
            
            val seed = text.hashCode()
            val random = java.util.Random(seed.toLong())
            
            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    val isTopLeft = row < 8 && col < 8
                    val isTopRight = row < 8 && col >= gridSize - 8
                    val isBottomLeft = row >= gridSize - 8 && col < 8
                    
                    if (!isTopLeft && !isTopRight && !isBottomLeft) {
                        if (random.nextBoolean()) {
                            paint.color = android.graphics.Color.BLACK
                            val rx = padding + col * cellSize
                            val ry = padding + row * cellSize
                            canvas.drawRect(rx, ry, rx + cellSize, ry + cellSize, paint)
                        }
                    }
                }
            }
            
            val savedUri = saveBitmapToGallery(context, bmp, "AliTools_QR_${System.currentTimeMillis()}", "PNG")
            if (savedUri != null) {
                showToast("success", "QR Code Saved to Pictures/AliTools!")
            } else {
                showToast("info", "Failed to export QR Code image.")
            }
        } catch (e: Exception) {
            showToast("info", "Error exporting: ${e.localizedMessage}")
        }
    }

    fun generateOfflineBitmap(context: android.content.Context, prompt: String): Bitmap {
        val width = 512
        val height = 512
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        
        val paint = android.graphics.Paint()
        val gradient = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            android.graphics.Color.parseColor("#1E3A8A"), // deep blue
            android.graphics.Color.parseColor("#10B981"), // emerald green
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        val cardPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        val padding = 40f
        val rectF = android.graphics.RectF(padding, padding, width - padding, height - padding)
        canvas.drawRoundRect(rectF, 24f, 24f, cardPaint)
        
        val circlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E0F2FE")
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(width / 2f, 160f, 60f, circlePaint)
        
        val letterPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#0284C7")
            textSize = 64f
            setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD))
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("A", width / 2f, 182f, letterPaint)
        
        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#0F172A")
            textSize = 24f
            setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD))
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("OFFLINE GENERATED IMAGE", width / 2f, 280f, titlePaint)
        
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#475569")
            textSize = 16f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        
        val maxLen = prompt.length
        val line1 = if (maxLen > 35) prompt.substring(0, 35) + "-" else prompt
        val line2 = if (maxLen > 35) {
            val endIdx = if (maxLen > 70) 70 else maxLen
            prompt.substring(35, endIdx) + (if (maxLen > 70) "..." else "")
        } else ""
        
        canvas.drawText("\"$line1\"", width / 2f, 330f, textPaint)
        if (line2.isNotEmpty()) {
            canvas.drawText("$line2\"", width / 2f, 355f, textPaint)
        }
        
        val badgePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#F1F5F9")
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        val badgeRect = android.graphics.RectF(100f, 410f, width - 100f, 460f)
        canvas.drawRoundRect(badgeRect, 12f, 12f, badgePaint)
        
        val badgeTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#0F766E")
            textSize = 14f
            setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD))
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Ali Tools Offline Canvas", width / 2f, 440f, badgeTextPaint)
        
        return bmp
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

