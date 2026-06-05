package com.example.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class Tool(
    val id: String,
    val name: String,
    val desc: String,
    val category: String, // "Images", "PDFs", "Converter", "Text", "Security", "AI"
    val icon: ImageVector,
    val popular: Boolean,
    val iconBgColor: Color,
    val iconTint: Color
)

object ToolRegistry {
    val tools = listOf(
        // AI TOOLS
        Tool(
            id = "ai_companion",
            name = "AI Companion",
            desc = "Live chat with Gemini & OpenRouter models. Attach files or ask questions.",
            category = "AI",
            icon = Icons.Default.Chat,
            popular = true,
            iconBgColor = Color(0xFFE0F2FE),
            iconTint = Color(0xFF0284C7)
        ),
        Tool(
            id = "ai_image_creator",
            name = "AI Image Creator",
            desc = "Generate rich, artistic images locally using the FLUX.1-schnell model.",
            category = "AI",
            icon = Icons.Default.AutoAwesome,
            popular = true,
            iconBgColor = Color(0xFFFEE2E2),
            iconTint = Color(0xFFDC2626)
        ),

        // IMAGES
        Tool(
            id = "image_compressor",
            name = "Image Compressor",
            desc = "Compress PNG/JPG image file size offline with visual preview & parameters.",
            category = "Images",
            icon = Icons.Default.KeyboardArrowDown,
            popular = true,
            iconBgColor = Color(0xFFECFDF5),
            iconTint = Color(0xFF059669)
        ),
        Tool(
            id = "image_resizer",
            name = "Image Resizer",
            desc = "Resize image dimensions (width, height) with scale aspect locking.",
            category = "Images",
            icon = Icons.Default.AspectRatio,
            popular = false,
            iconBgColor = Color(0xFFF3E8FF),
            iconTint = Color(0xFF9333EA)
        ),
        Tool(
            id = "image_cropper",
            name = "Image Cropper",
            desc = "Crop custom aspect ratios (1:1, 16:9, etc.) locally on your device.",
            category = "Images",
            icon = Icons.Default.Crop,
            popular = false,
            iconBgColor = Color(0xFFFFF7ED),
            iconTint = Color(0xFFEA580C)
        ),
        Tool(
            id = "image_grayscale",
            name = "Grayscale Filter",
            desc = "Instantly turn colored photography into stark, expressive monochrome.",
            category = "Images",
            icon = Icons.Default.ColorLens,
            popular = false,
            iconBgColor = Color(0xFFF1F5F9),
            iconTint = Color(0xFF475569)
        ),
        Tool(
            id = "image_blur",
            name = "Blur Image Filter",
            desc = "Add stylized focus attenuation and artistic gaussian softening factors.",
            category = "Images",
            icon = Icons.Default.BlurOn,
            popular = false,
            iconBgColor = Color(0xFFEEF2F6),
            iconTint = Color(0xFF1E293B)
        ),
        Tool(
            id = "image_brightness",
            name = "Brightness & Contrast",
            desc = "Optimize brightness exposure curves and fine-tune contrast ranges.",
            category = "Images",
            icon = Icons.Default.LightMode,
            popular = false,
            iconBgColor = Color(0xFFFEF9C3),
            iconTint = Color(0xFFCA8A04)
        ),
        Tool(
            id = "image_invert",
            name = "Invert Image Filter",
            desc = "Flip all color channel values to render classic dark/light negatives.",
            category = "Images",
            icon = Icons.Default.InvertColors,
            popular = false,
            iconBgColor = Color(0xFFFDE8FF),
            iconTint = Color(0xFFC084FC)
        ),
        Tool(
            id = "image_sepia",
            name = "Sepia Filter",
            desc = "Apply elegant antique vintage warm brown shading overlays.",
            category = "Images",
            icon = Icons.Default.SettingsBackupRestore,
            popular = false,
            iconBgColor = Color(0xFFFEF3C7),
            iconTint = Color(0xFFD97706)
        ),
        Tool(
            id = "image_watermark",
            name = "Watermark Maker",
            desc = "Draw customized text labels, copyright letters, or shadows over images.",
            category = "Images",
            icon = Icons.Default.BorderColor,
            popular = false,
            iconBgColor = Color(0xFFEBF5FF),
            iconTint = Color(0xFF3B82F6)
        ),
        Tool(
            id = "image_pixelart",
            name = "Pixel Art Stylist",
            desc = "Scale pictures down to chunky retro low-res 8-bit grids and block palettes.",
            category = "Images",
            icon = Icons.Default.GridOn,
            popular = false,
            iconBgColor = Color(0xFFF0FDF4),
            iconTint = Color(0xFF16A34A)
        ),
        Tool(
            id = "image_round_corners",
            name = "Round Corners Creator",
            desc = "Clip sharp picture margins with custom radius round boundaries.",
            category = "Images",
            icon = Icons.Default.RoundedCorner,
            popular = false,
            iconBgColor = Color(0xFFFAF5FF),
            iconTint = Color(0xFF7C3AED)
        ),
        Tool(
            id = "image_to_base64",
            name = "Image to Base64",
            desc = "Convert binary graphic bytes directly into copyable Base64 string tags.",
            category = "Images",
            icon = Icons.Default.Code,
            popular = false,
            iconBgColor = Color(0xFFECFDF5),
            iconTint = Color(0xFF0F766E)
        ),

        // PDFs
        Tool(
            id = "cam_scanner",
            name = "Document CamScanner",
            desc = "Scan paper sheets via camera, auto-detect/crop boundaries, apply magic filters, and compile multi-page files.",
            category = "PDFs",
            icon = Icons.Default.FilterCenterFocus,
            popular = true,
            iconBgColor = Color(0xFFF0FDF4),
            iconTint = Color(0xFF16A34A)
        ),
        Tool(
            id = "pdf_merger",
            name = "PDF Merger",
            desc = "Select and combine multiple PDF documents into a single consolidated file.",
            category = "PDFs",
            icon = Icons.Default.PictureAsPdf,
            popular = true,
            iconBgColor = Color(0xFFFEF2F2),
            iconTint = Color(0xFFEF4444)
        ),
        Tool(
            id = "pdf_splitter",
            name = "PDF Splitter",
            desc = "Decompose large documents, extracting specific page ranges to new PDFs.",
            category = "PDFs",
            icon = Icons.Default.CallSplit,
            popular = false,
            iconBgColor = Color(0xFFFFF1F2),
            iconTint = Color(0xFFE11D48)
        ),
        Tool(
            id = "image_to_pdf",
            name = "Image to PDF",
            desc = "Assemble multiple photo captures together and export as a multi-page PDF.",
            category = "PDFs",
            icon = Icons.Default.PhotoLibrary,
            popular = true,
            iconBgColor = Color(0xFFECFDF5),
            iconTint = Color(0xFF059669)
        ),
        Tool(
            id = "pdf_rotator",
            name = "PDF Page Rotator",
            desc = "Fix orientation by rotating single pages or entire PDFs by increments of 90°.",
            category = "PDFs",
            icon = Icons.Default.Refresh,
            popular = false,
            iconBgColor = Color(0xFFFAF5FF),
            iconTint = Color(0xFF7C3AED)
        ),
        Tool(
            id = "pdf_encrypt",
            name = "Password Protect PDF",
            desc = "Encrypt document layers to prevent unauthorized access or reading permissions.",
            category = "PDFs",
            icon = Icons.Default.Lock,
            popular = false,
            iconBgColor = Color(0xFFFEF2F2),
            iconTint = Color(0xFFEF4444)
        ),
        Tool(
            id = "pdf_decrypt",
            name = "Unlock PDF Document",
            desc = "Remove protection constraints from PDFs using the correct known password.",
            category = "PDFs",
            icon = Icons.Default.LockOpen,
            popular = false,
            iconBgColor = Color(0xFFF0FDF4),
            iconTint = Color(0xFF15803D)
        ),
        Tool(
            id = "pdf_text_extractor",
            name = "PDF to Text Engine",
            desc = "Parse embedded texts from formatting containers and export as plain strings.",
            category = "PDFs",
            icon = Icons.Default.TextFormat,
            popular = false,
            iconBgColor = Color(0xFFEFF6FF),
            iconTint = Color(0xFF1D4ED8)
        ),

        // CONVERTER
        Tool(
            id = "png_to_jpg",
            name = "PNG to JPG Converter",
            desc = "Convert transparent PNGs into highly compressed, light JPG matrices.",
            category = "Converter",
            icon = Icons.Default.SwapHoriz,
            popular = true,
            iconBgColor = Color(0xFFE0F2FE),
            iconTint = Color(0xFF0284C7)
        ),
        Tool(
            id = "webp_to_png",
            name = "WEBP to PNG Converter",
            desc = "Convert web-optimized WEBP frames back into lossless high fidelity PNG files.",
            category = "Converter",
            icon = Icons.Default.CompareArrows,
            popular = false,
            iconBgColor = Color(0xFFEEF2F6),
            iconTint = Color(0xFF475569)
        ),
        Tool(
            id = "csv_to_json",
            name = "CSV to JSON Converter",
            desc = "Transform dense spreadsheet grid files into organized lists of nested JSON files.",
            category = "Converter",
            icon = Icons.Default.DataObject,
            popular = false,
            iconBgColor = Color(0xFFFFFBEB),
            iconTint = Color(0xFFD97706)
        ),
        Tool(
            id = "json_to_csv",
            name = "JSON to CSV Exporter",
            desc = "Flatten array records into row-column formats ready for Excel or Sheets.",
            category = "Converter",
            icon = Icons.Default.TableChart,
            popular = false,
            iconBgColor = Color(0xFFECFDF5),
            iconTint = Color(0xFF047857)
        ),
        Tool(
            id = "temp_converter",
            name = "Temperature Base",
            desc = "Convert temperature metrics seamlessly among Celsius, Fahrenheit, and Kelvin.",
            category = "Converter",
            icon = Icons.Default.Thermostat,
            popular = false,
            iconBgColor = Color(0xFFFEF2F2),
            iconTint = Color(0xFFE11D48)
        ),
        Tool(
            id = "unit_converter",
            name = "Unit & Measurement",
            desc = "Convert general standards of length, weight, volume, areas, speeds and times.",
            category = "Converter",
            icon = Icons.Default.LinearScale,
            popular = false,
            iconBgColor = Color(0xFFEFF6FF),
            iconTint = Color(0xFF2563EB)
        ),

        // TEXT
        Tool(
            id = "word_counter",
            name = "Word & Chars Counter",
            desc = "Parse text fields to count letters, words, paragraphs, and reading duration.",
            category = "Text",
            icon = Icons.Default.Numbers,
            popular = false,
            iconBgColor = Color(0xFFF0FDFA),
            iconTint = Color(0xFF0D9488)
        ),
        Tool(
            id = "json_formatter",
            name = "JSON Formatter",
            desc = "Beautify cluttered JSON lines with custom indentation spacing, or minify.",
            category = "Text",
            icon = Icons.Default.Code,
            popular = false,
            iconBgColor = Color(0xFFFAF5FF),
            iconTint = Color(0xFF7C3AED)
        ),
        Tool(
            id = "lorem_ipsum",
            name = "Lorem Ipsum Generator",
            desc = "Generate customizable, classic placeholder paragraph templates instantly.",
            category = "Text",
            icon = Icons.Default.TextFields,
            popular = false,
            iconBgColor = Color(0xFFFFF7ED),
            iconTint = Color(0xFFEA580C)
        ),
        Tool(
            id = "hash_generator",
            name = "Hash Generator",
            desc = "Securely generate MD5, SHA-1, SHA-256, and SHA-512 cryptographic digests.",
            category = "Text",
            icon = Icons.Default.Fingerprint,
            popular = false,
            iconBgColor = Color(0xFFFFE4E6),
            iconTint = Color(0xFFE11D48)
        ),
        Tool(
            id = "case_converter",
            name = "Text Case Converter",
            desc = "Toggle input content rapidly between UPPER, lower, Title Case, camelCase, etc.",
            category = "Text",
            icon = Icons.Default.FormatSize,
            popular = false,
            iconBgColor = Color(0xFFEDF2F7),
            iconTint = Color(0xFF2D3748)
        ),

        // SECURITY & OTHERS
        Tool(
            id = "password_generator",
            name = "Password Generator",
            desc = "Create randomized, strong password keys combining numerals and symbols.",
            category = "Security",
            icon = Icons.Default.VpnKey,
            popular = false,
            iconBgColor = Color(0xFFECFDF5),
            iconTint = Color(0xFF059669)
        ),
        Tool(
            id = "qr_scanner_generator",
            name = "QR Gen & Scanner",
            desc = "Create customized high contrast QR codes or scan graphics to extract urls.",
            category = "Security",
            icon = Icons.Default.QrCodeScanner,
            popular = true,
            iconBgColor = Color(0xFFEFF6FF),
            iconTint = Color(0xFF1D4ED8)
        )
    )
}
