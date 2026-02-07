package al.clk.gui.server.handlers

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.iptc.IptcDirectory
import com.drew.metadata.xmp.XmpDirectory
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.apache.fontbox.ttf.OTFParser
import org.apache.fontbox.ttf.TTFParser
import org.apache.fontbox.ttf.TrueTypeFont
import al.clk.gui.server.di.MimeCategory
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.info.MultimediaInfo
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

// ============================================
// Font Data Classes
// ============================================

/**
 * Comprehensive font information data class
 */
@Serializable
data class FontInfo(
    val fontFamily: String,
    val fontSubfamily: String,
    val fullName: String,
    val postScriptName: String,
    val version: String,
    val copyright: String?,
    val trademark: String?,
    val manufacturer: String?,
    val designer: String?,
    val description: String?,
    val vendorUrl: String?,
    val designerUrl: String?,
    val license: String?,
    val licenseUrl: String?,
    val format: FontFormat,
    val unitsPerEm: Int,
    val numGlyphs: Int,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isMonospace: Boolean,
    val supportsLatin: Boolean,
    val supportsCyrillic: Boolean,
    val supportsGreek: Boolean,
    val supportsCJK: Boolean,
    val supportsArabic: Boolean,
    val fileSize: Long,
    val createdDate: String?,
    val modifiedDate: String?
)

@Serializable
enum class FontFormat {
    TTF, OTF, WOFF, WOFF2, EOT, UNKNOWN
}

/**
 * Character/glyph information
 */
@Serializable
data class GlyphInfo(
    val unicode: Int,
    val character: String,
    val name: String?,
    val width: Int,
    val category: String
)

// ============================================
// Enhanced FontBox Handler
// ============================================

/**
 * Enhanced font handler using Apache FontBox
 * Generates Bootstrap grid XHTML for character display
 */
class FontBoxEnhancedHandler : FileExtensionHandler {
    override val handlerId = "fontbox-enhanced"
    override val displayName = "Font (FontBox Enhanced)"
    override val supportedExtensions = setOf("ttf", "otf", "woff", "woff2", "eot")
    override val mimeTypes = setOf(
        "font/ttf", "font/otf", "font/woff", "font/woff2",
        "application/font-ttf", "application/font-otf",
        "application/vnd.ms-fontobject"
    )
    override val category = MimeCategory.BINARY
    override val priority = 25

    private val ttfParser = TTFParser()
    private val otfParser = OTFParser()

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val fontInfo = extractFontInfo(content.content, content.extension)
            val xhtml = generateFontXhtml(fontInfo, content.content, content.extension)

            ProcessingResult(
                success = true,
                contentType = "application/xhtml+xml",
                processedContent = xhtml,
                metadata = fontInfoToMetadata(fontInfo)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process font: ${content.name}" }
            ProcessingResult(
                success = false,
                contentType = "application/octet-stream",
                error = e.message
            )
        }
    }

    override suspend fun validate(content: FileContent): Boolean {
        return try {
            parseFont(content.content, content.extension)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        return try {
            val fontInfo = extractFontInfo(content.content, content.extension)
            fontInfoToMetadata(fontInfo)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Failed to extract font metadata"))
        }
    }

    /**
     * Parse font file to TrueTypeFont
     */
    private fun parseFont(bytes: ByteArray, extension: String): TrueTypeFont {
        val tempFile = Files.createTempFile("font", ".$extension").toFile()
        try {
            tempFile.writeBytes(bytes)
            return when (extension.lowercase()) {
                "otf" -> otfParser.parse(tempFile)
                else -> ttfParser.parse(tempFile)
            }
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Extract comprehensive font information
     */
    fun extractFontInfo(bytes: ByteArray, extension: String): FontInfo {
        val font = parseFont(bytes, extension)
        val naming = font.naming
        val os2 = font.oS2Windows
        val head = font.header

        return FontInfo(
            fontFamily = naming?.fontFamily ?: "Unknown",
            fontSubfamily = naming?.fontSubFamily ?: "Regular",
            fullName = naming?.fullFontName ?: "Unknown",
            postScriptName = naming?.postScriptName ?: "Unknown",
            version = naming?.version ?: "1.0",
            copyright = naming?.copyright,
            trademark = naming?.trademark,
            manufacturer = naming?.manufacturer,
            designer = naming?.designer,
            description = naming?.description,
            vendorUrl = naming?.vendorURL,
            designerUrl = naming?.designerURL,
            license = naming?.licenseDescription,
            licenseUrl = naming?.licenseURL,
            format = detectFormat(extension),
            unitsPerEm = head?.unitsPerEm?.toInt() ?: 1000,
            numGlyphs = font.numberOfGlyphs,
            isBold = os2?.weightClass?.let { it >= 700 } ?: false,
            isItalic = head?.macStyle?.let { (it.toInt() and 2) != 0 } ?: false,
            isMonospace = font.horizontalMetrics?.let { hm ->
                val widths = (0 until minOf(100, font.numberOfGlyphs)).map {
                    hm.getAdvanceWidth(it)
                }.distinct()
                widths.size <= 2
            } ?: false,
            supportsLatin = hasUnicodeRange(os2, 0), // Basic Latin
            supportsCyrillic = hasUnicodeRange(os2, 9), // Cyrillic
            supportsGreek = hasUnicodeRange(os2, 7), // Greek
            supportsCJK = hasUnicodeRange(os2, 59) || hasUnicodeRange(os2, 60), // CJK
            supportsArabic = hasUnicodeRange(os2, 13), // Arabic
            fileSize = bytes.size.toLong(),
            createdDate = head?.created?.let { formatFontDate(it) },
            modifiedDate = head?.modified?.let { formatFontDate(it) }
        )
    }

    private fun hasUnicodeRange(os2: org.apache.fontbox.ttf.OS2WindowsMetricsTable?, bit: Int): Boolean {
        if (os2 == null) return false
        val rangeIndex = bit / 32
        val bitIndex = bit % 32
        val ranges = listOf(
            os2.unicodeRange1.toLong() and 0xFFFFFFFFL,
            os2.unicodeRange2.toLong() and 0xFFFFFFFFL,
            os2.unicodeRange3.toLong() and 0xFFFFFFFFL,
            os2.unicodeRange4.toLong() and 0xFFFFFFFFL
        )
        return if (rangeIndex < ranges.size) {
            (ranges[rangeIndex] and (1L shl bitIndex)) != 0L
        } else false
    }

    private fun formatFontDate(timestamp: java.util.Calendar): String {
        return DateTimeFormatter.ISO_DATE_TIME
            .withZone(ZoneId.systemDefault())
            .format(timestamp.toInstant())
    }

    private fun detectFormat(extension: String): FontFormat {
        return when (extension.lowercase()) {
            "ttf" -> FontFormat.TTF
            "otf" -> FontFormat.OTF
            "woff" -> FontFormat.WOFF
            "woff2" -> FontFormat.WOFF2
            "eot" -> FontFormat.EOT
            else -> FontFormat.UNKNOWN
        }
    }

    /**
     * Generate Bootstrap grid XHTML for font character display
     */
    fun generateFontXhtml(fontInfo: FontInfo, bytes: ByteArray, extension: String): String {
        val base64Font = java.util.Base64.getEncoder().encodeToString(bytes)
        val fontFormat = when (extension.lowercase()) {
            "ttf" -> "truetype"
            "otf" -> "opentype"
            "woff" -> "woff"
            "woff2" -> "woff2"
            else -> "truetype"
        }

        // Generate character grid for printable ASCII and extended
        val charGrid = generateCharacterGrid(fontInfo)

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
            <head>
                <meta http-equiv="Content-Type" content="application/xhtml+xml; charset=UTF-8" />
                <title>${escapeHtml(fontInfo.fullName)} - Font Preview</title>
                <style type="text/css">
                    @font-face {
                        font-family: 'PreviewFont';
                        src: url(data:font/$fontFormat;base64,$base64Font) format('$fontFormat');
                        font-weight: normal;
                        font-style: normal;
                    }

                    /* Bootstrap-inspired Grid System */
                    .container { max-width: 1200px; margin: 0 auto; padding: 20px; }
                    .row { display: flex; flex-wrap: wrap; margin: 0 -10px; }
                    .col { flex: 1; padding: 10px; }
                    .col-12 { flex: 0 0 100%; max-width: 100%; }
                    .col-6 { flex: 0 0 50%; max-width: 50%; }
                    .col-4 { flex: 0 0 33.333%; max-width: 33.333%; }
                    .col-3 { flex: 0 0 25%; max-width: 25%; }
                    .col-2 { flex: 0 0 16.666%; max-width: 16.666%; }

                    /* Card styling */
                    .card { background: #fff; border: 1px solid #ddd; border-radius: 4px; margin-bottom: 20px; }
                    .card-header { background: #f5f5f5; padding: 15px; border-bottom: 1px solid #ddd; font-weight: bold; }
                    .card-body { padding: 15px; }

                    /* Font info table */
                    .info-table { width: 100%; border-collapse: collapse; }
                    .info-table th, .info-table td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; }
                    .info-table th { width: 40%; color: #666; font-weight: normal; }
                    .info-table td { font-weight: 500; }

                    /* Character grid */
                    .char-grid { display: grid; grid-template-columns: repeat(16, 1fr); gap: 2px; }
                    .char-cell {
                        font-family: 'PreviewFont', sans-serif;
                        font-size: 24px;
                        text-align: center;
                        padding: 10px 5px;
                        border: 1px solid #eee;
                        background: #fafafa;
                        min-height: 50px;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                    }
                    .char-cell:hover { background: #e3f2fd; border-color: #2196f3; }
                    .char-code { font-family: monospace; font-size: 10px; color: #999; margin-top: 4px; }

                    /* Sample text */
                    .sample-text { font-family: 'PreviewFont', sans-serif; line-height: 1.5; }
                    .sample-12 { font-size: 12px; }
                    .sample-16 { font-size: 16px; }
                    .sample-24 { font-size: 24px; }
                    .sample-36 { font-size: 36px; }
                    .sample-48 { font-size: 48px; }
                    .sample-72 { font-size: 72px; }

                    /* Badges */
                    .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; margin: 2px; }
                    .badge-primary { background: #2196f3; color: white; }
                    .badge-success { background: #4caf50; color: white; }
                    .badge-warning { background: #ff9800; color: white; }
                    .badge-info { background: #00bcd4; color: white; }
                </style>
            </head>
            <body class="container">
                <div class="row">
                    <div class="col-12">
                        <h1 style="font-family: 'PreviewFont', sans-serif; font-size: 48px;">${escapeHtml(fontInfo.fullName)}</h1>
                        <p>
                            ${if (fontInfo.isBold) "<span class='badge badge-primary'>Bold</span>" else ""}
                            ${if (fontInfo.isItalic) "<span class='badge badge-primary'>Italic</span>" else ""}
                            ${if (fontInfo.isMonospace) "<span class='badge badge-info'>Monospace</span>" else ""}
                            <span class='badge badge-success'>${fontInfo.format}</span>
                            <span class='badge badge-warning'>${fontInfo.numGlyphs} glyphs</span>
                        </p>
                    </div>
                </div>

                <div class="row">
                    <div class="col-6">
                        <div class="card">
                            <div class="card-header">Font Information</div>
                            <div class="card-body">
                                <table class="info-table">
                                    <tr><th>Font Family</th><td>${escapeHtml(fontInfo.fontFamily)}</td></tr>
                                    <tr><th>Subfamily</th><td>${escapeHtml(fontInfo.fontSubfamily)}</td></tr>
                                    <tr><th>PostScript Name</th><td>${escapeHtml(fontInfo.postScriptName)}</td></tr>
                                    <tr><th>Version</th><td>${escapeHtml(fontInfo.version)}</td></tr>
                                    <tr><th>Units per Em</th><td>${fontInfo.unitsPerEm}</td></tr>
                                    <tr><th>Number of Glyphs</th><td>${fontInfo.numGlyphs}</td></tr>
                                    <tr><th>File Size</th><td>${formatFileSize(fontInfo.fileSize)}</td></tr>
                                    ${fontInfo.copyright?.let { "<tr><th>Copyright</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                                    ${fontInfo.designer?.let { "<tr><th>Designer</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                                    ${fontInfo.manufacturer?.let { "<tr><th>Manufacturer</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                                </table>
                            </div>
                        </div>
                    </div>

                    <div class="col-6">
                        <div class="card">
                            <div class="card-header">Unicode Support</div>
                            <div class="card-body">
                                <table class="info-table">
                                    <tr><th>Basic Latin</th><td>${if (fontInfo.supportsLatin) "✓" else "✗"}</td></tr>
                                    <tr><th>Cyrillic</th><td>${if (fontInfo.supportsCyrillic) "✓" else "✗"}</td></tr>
                                    <tr><th>Greek</th><td>${if (fontInfo.supportsGreek) "✓" else "✗"}</td></tr>
                                    <tr><th>CJK</th><td>${if (fontInfo.supportsCJK) "✓" else "✗"}</td></tr>
                                    <tr><th>Arabic</th><td>${if (fontInfo.supportsArabic) "✓" else "✗"}</td></tr>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="row">
                    <div class="col-12">
                        <div class="card">
                            <div class="card-header">Sample Text</div>
                            <div class="card-body">
                                <p class="sample-text sample-12">12px: The quick brown fox jumps over the lazy dog. 0123456789</p>
                                <p class="sample-text sample-16">16px: The quick brown fox jumps over the lazy dog. 0123456789</p>
                                <p class="sample-text sample-24">24px: The quick brown fox jumps over the lazy dog.</p>
                                <p class="sample-text sample-36">36px: The quick brown fox jumps over the lazy dog.</p>
                                <p class="sample-text sample-48">48px: ABCDEFGHIJKLMNOPQRSTUVWXYZ</p>
                                <p class="sample-text sample-72">72px: Aa Bb Cc</p>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="row">
                    <div class="col-12">
                        <div class="card">
                            <div class="card-header">Character Grid (ASCII)</div>
                            <div class="card-body">
                                <div class="char-grid">
                                    $charGrid
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun generateCharacterGrid(fontInfo: FontInfo): String {
        val sb = StringBuilder()
        // Printable ASCII characters (32-126)
        for (code in 32..126) {
            val char = code.toChar()
            val displayChar = if (code == 32) "␣" else escapeHtml(char.toString())
            sb.append("""
                <div class="char-cell">
                    <span>$displayChar</span>
                    <span class="char-code">U+${String.format("%04X", code)}</span>
                </div>
            """.trimIndent())
        }
        return sb.toString()
    }

    private fun fontInfoToMetadata(fontInfo: FontInfo): Map<String, String> {
        return buildMap {
            put("fontFamily", fontInfo.fontFamily)
            put("fontSubfamily", fontInfo.fontSubfamily)
            put("fullName", fontInfo.fullName)
            put("postScriptName", fontInfo.postScriptName)
            put("version", fontInfo.version)
            put("format", fontInfo.format.name)
            put("numGlyphs", fontInfo.numGlyphs.toString())
            put("unitsPerEm", fontInfo.unitsPerEm.toString())
            put("isBold", fontInfo.isBold.toString())
            put("isItalic", fontInfo.isItalic.toString())
            put("isMonospace", fontInfo.isMonospace.toString())
            put("fileSize", fontInfo.fileSize.toString())
            fontInfo.copyright?.let { put("copyright", it) }
            fontInfo.designer?.let { put("designer", it) }
            fontInfo.manufacturer?.let { put("manufacturer", it) }
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
            bytes >= 1_000 -> "${bytes / 1_000} KB"
            else -> "$bytes bytes"
        }
    }
}

// ============================================
// Image Metadata Handler
// ============================================

/**
 * Image metadata data class
 */
@Serializable
data class ImageMetadata(
    val width: Int?,
    val height: Int?,
    val colorDepth: Int?,
    val format: String,
    val fileSize: Long,
    val exif: ExifData?,
    val gps: GpsData?,
    val iptc: IptcData?,
    val xmp: XmpData?
)

@Serializable
data class ExifData(
    val make: String?,
    val model: String?,
    val software: String?,
    val dateTimeOriginal: String?,
    val dateTimeDigitized: String?,
    val exposureTime: String?,
    val fNumber: String?,
    val iso: Int?,
    val focalLength: String?,
    val flash: String?,
    val whiteBalance: String?,
    val orientation: Int?
)

@Serializable
data class GpsData(
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val timestamp: String?
)

@Serializable
data class IptcData(
    val headline: String?,
    val caption: String?,
    val copyright: String?,
    val byline: String?,
    val keywords: List<String>?,
    val city: String?,
    val country: String?
)

@Serializable
data class XmpData(
    val creator: String?,
    val description: String?,
    val rights: String?,
    val subject: List<String>?
)

/**
 * Image metadata extraction handler using metadata-extractor
 */
class ImageMetadataHandler : FileExtensionHandler {
    override val handlerId = "image-metadata"
    override val displayName = "Image Metadata (EXIF/IPTC/XMP)"
    override val supportedExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif", "heic", "heif", "raw", "cr2", "nef", "arw")
    override val mimeTypes = setOf(
        "image/jpeg", "image/png", "image/gif", "image/bmp",
        "image/webp", "image/tiff", "image/heic", "image/heif"
    )
    override val category = MimeCategory.IMAGE
    override val priority = 25

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val metadata = extractImageMetadata(content.content, content.name)
            val xhtml = generateImageXhtml(metadata, content.content, content.name)

            ProcessingResult(
                success = true,
                contentType = "application/xhtml+xml",
                processedContent = xhtml,
                metadata = imageMetadataToMap(metadata)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to extract image metadata: ${content.name}" }
            ProcessingResult(
                success = false,
                contentType = "image/jpeg",
                error = e.message
            )
        }
    }

    override suspend fun validate(content: FileContent): Boolean {
        return try {
            ByteArrayInputStream(content.content).use { stream ->
                ImageMetadataReader.readMetadata(stream)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        return try {
            val metadata = extractImageMetadata(content.content, content.name)
            imageMetadataToMap(metadata)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Failed to extract metadata"))
        }
    }

    /**
     * Extract comprehensive image metadata
     */
    fun extractImageMetadata(bytes: ByteArray, filename: String): ImageMetadata {
        val metadata = ByteArrayInputStream(bytes).use { stream ->
            ImageMetadataReader.readMetadata(stream)
        }

        // EXIF data
        val exifIfd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val exifSubIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        val exifData = if (exifIfd0 != null || exifSubIfd != null) {
            ExifData(
                make = exifIfd0?.getString(ExifIFD0Directory.TAG_MAKE),
                model = exifIfd0?.getString(ExifIFD0Directory.TAG_MODEL),
                software = exifIfd0?.getString(ExifIFD0Directory.TAG_SOFTWARE),
                dateTimeOriginal = exifSubIfd?.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL),
                dateTimeDigitized = exifSubIfd?.getString(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED),
                exposureTime = exifSubIfd?.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME),
                fNumber = exifSubIfd?.getString(ExifSubIFDDirectory.TAG_FNUMBER),
                iso = exifSubIfd?.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT),
                focalLength = exifSubIfd?.getString(ExifSubIFDDirectory.TAG_FOCAL_LENGTH),
                flash = exifSubIfd?.getString(ExifSubIFDDirectory.TAG_FLASH),
                whiteBalance = exifSubIfd?.getString(ExifSubIFDDirectory.TAG_WHITE_BALANCE_MODE),
                orientation = exifIfd0?.getInteger(ExifIFD0Directory.TAG_ORIENTATION)
            )
        } else null

        // GPS data
        val gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
        val gpsData = if (gpsDir != null) {
            val geoLocation = gpsDir.geoLocation
            GpsData(
                latitude = geoLocation?.latitude,
                longitude = geoLocation?.longitude,
                altitude = gpsDir.getDoubleObject(GpsDirectory.TAG_ALTITUDE),
                timestamp = gpsDir.getString(GpsDirectory.TAG_TIME_STAMP)
            )
        } else null

        // IPTC data
        val iptcDir = metadata.getFirstDirectoryOfType(IptcDirectory::class.java)
        val iptcData = if (iptcDir != null) {
            IptcData(
                headline = iptcDir.getString(IptcDirectory.TAG_HEADLINE),
                caption = iptcDir.getString(IptcDirectory.TAG_CAPTION),
                copyright = iptcDir.getString(IptcDirectory.TAG_COPYRIGHT_NOTICE),
                byline = iptcDir.getString(IptcDirectory.TAG_BY_LINE),
                keywords = iptcDir.getStringArray(IptcDirectory.TAG_KEYWORDS)?.toList(),
                city = iptcDir.getString(IptcDirectory.TAG_CITY),
                country = iptcDir.getString(IptcDirectory.TAG_COUNTRY_OR_PRIMARY_LOCATION_NAME)
            )
        } else null

        // XMP data
        val xmpDir = metadata.getFirstDirectoryOfType(XmpDirectory::class.java)
        val xmpData = if (xmpDir != null) {
            XmpData(
                creator = xmpDir.xmpMeta?.let {
                    try { it.getArrayItem("http://purl.org/dc/elements/1.1/", "creator", 1)?.value }
                    catch (e: Exception) { null }
                },
                description = xmpDir.xmpMeta?.let {
                    try { it.getLocalizedText("http://purl.org/dc/elements/1.1/", "description", null, "x-default")?.value }
                    catch (e: Exception) { null }
                },
                rights = xmpDir.xmpMeta?.let {
                    try { it.getLocalizedText("http://purl.org/dc/elements/1.1/", "rights", null, "x-default")?.value }
                    catch (e: Exception) { null }
                },
                subject = null // XMP subjects would need more complex extraction
            )
        } else null

        // Get image dimensions from various sources
        var width: Int? = null
        var height: Int? = null
        for (dir in metadata.directories) {
            if (width == null) width = dir.getInteger(256) // Common tag for width
            if (height == null) height = dir.getInteger(257) // Common tag for height
        }

        return ImageMetadata(
            width = width,
            height = height,
            colorDepth = null,
            format = filename.substringAfterLast('.').uppercase(),
            fileSize = bytes.size.toLong(),
            exif = exifData,
            gps = gpsData,
            iptc = iptcData,
            xmp = xmpData
        )
    }

    /**
     * Generate XHTML with image and metadata display
     */
    private fun generateImageXhtml(metadata: ImageMetadata, bytes: ByteArray, filename: String): String {
        val base64 = java.util.Base64.getEncoder().encodeToString(bytes)
        val mimeType = when (filename.substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
            <head>
                <meta http-equiv="Content-Type" content="application/xhtml+xml; charset=UTF-8" />
                <title>${escapeHtml(filename)} - Image Metadata</title>
                <style type="text/css">
                    .container { max-width: 1200px; margin: 0 auto; padding: 20px; }
                    .row { display: flex; flex-wrap: wrap; margin: 0 -10px; }
                    .col-6 { flex: 0 0 50%; max-width: 50%; padding: 10px; }
                    .col-12 { flex: 0 0 100%; max-width: 100%; padding: 10px; }
                    .card { background: #fff; border: 1px solid #ddd; border-radius: 4px; margin-bottom: 20px; }
                    .card-header { background: #f5f5f5; padding: 15px; border-bottom: 1px solid #ddd; font-weight: bold; }
                    .card-body { padding: 15px; }
                    .image-preview { max-width: 100%; height: auto; border: 1px solid #ddd; }
                    .info-table { width: 100%; border-collapse: collapse; }
                    .info-table th, .info-table td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; }
                    .info-table th { width: 40%; color: #666; }
                    .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; margin: 2px; background: #2196f3; color: white; }
                </style>
            </head>
            <body class="container">
                <h1>${escapeHtml(filename)}</h1>
                <p>
                    <span class="badge">${metadata.format}</span>
                    ${metadata.width?.let { w -> metadata.height?.let { h -> "<span class='badge'>${w}x${h}</span>" } } ?: ""}
                    <span class="badge">${formatFileSize(metadata.fileSize)}</span>
                </p>

                <div class="row">
                    <div class="col-6">
                        <div class="card">
                            <div class="card-header">Preview</div>
                            <div class="card-body">
                                <img class="image-preview" src="data:$mimeType;base64,$base64" alt="${escapeHtml(filename)}" />
                            </div>
                        </div>
                    </div>

                    <div class="col-6">
                        ${metadata.exif?.let { exif -> """
                        <div class="card">
                            <div class="card-header">EXIF Data</div>
                            <div class="card-body">
                                <table class="info-table">
                                    ${exif.make?.let { "<tr><th>Camera Make</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                                    ${exif.model?.let { "<tr><th>Camera Model</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                                    ${exif.dateTimeOriginal?.let { "<tr><th>Date Taken</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                                    ${exif.exposureTime?.let { "<tr><th>Exposure</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                                    ${exif.fNumber?.let { "<tr><th>F-Number</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                                    ${exif.iso?.let { "<tr><th>ISO</th><td>$it</td></tr>" } ?: ""}
                                    ${exif.focalLength?.let { "<tr><th>Focal Length</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                                    ${exif.flash?.let { "<tr><th>Flash</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                                </table>
                            </div>
                        </div>
                        """ } ?: ""}

                        ${metadata.gps?.let { gps -> if (gps.latitude != null && gps.longitude != null) """
                        <div class="card">
                            <div class="card-header">GPS Location</div>
                            <div class="card-body">
                                <table class="info-table">
                                    <tr><th>Latitude</th><td>${gps.latitude}</td></tr>
                                    <tr><th>Longitude</th><td>${gps.longitude}</td></tr>
                                    ${gps.altitude?.let { "<tr><th>Altitude</th><td>${it}m</td></tr>" } ?: ""}
                                </table>
                            </div>
                        </div>
                        """ else "" } ?: ""}
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun imageMetadataToMap(metadata: ImageMetadata): Map<String, String> {
        return buildMap {
            metadata.width?.let { put("width", it.toString()) }
            metadata.height?.let { put("height", it.toString()) }
            put("format", metadata.format)
            put("fileSize", metadata.fileSize.toString())
            metadata.exif?.let { exif ->
                exif.make?.let { put("exif.make", it) }
                exif.model?.let { put("exif.model", it) }
                exif.dateTimeOriginal?.let { put("exif.dateTimeOriginal", it) }
                exif.iso?.let { put("exif.iso", it.toString()) }
            }
            metadata.gps?.let { gps ->
                gps.latitude?.let { put("gps.latitude", it.toString()) }
                gps.longitude?.let { put("gps.longitude", it.toString()) }
            }
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
            bytes >= 1_000 -> "${bytes / 1_000} KB"
            else -> "$bytes bytes"
        }
    }
}

// ============================================
// FFmpeg Media Handler
// ============================================

/**
 * Media info data class for audio/video
 */
@Serializable
data class MediaInfo(
    val duration: Long, // milliseconds
    val format: String,
    val fileSize: Long,
    val bitRate: Int?,
    val video: VideoStreamInfo?,
    val audio: AudioStreamInfo?
)

@Serializable
data class VideoStreamInfo(
    val codec: String?,
    val width: Int,
    val height: Int,
    val frameRate: Float?,
    val bitRate: Int?
)

@Serializable
data class AudioStreamInfo(
    val codec: String?,
    val channels: Int?,
    val sampleRate: Int?,
    val bitRate: Int?
)

/**
 * FFmpeg-based media handler for audio/video files
 */
class FFmpegMediaHandler : FileExtensionHandler {
    override val handlerId = "ffmpeg-media"
    override val displayName = "Media (FFmpeg)"
    override val supportedExtensions = setOf(
        // Video
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpeg", "mpg", "3gp",
        // Audio
        "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "aiff"
    )
    override val mimeTypes = setOf(
        // Video
        "video/mp4", "video/x-matroska", "video/x-msvideo", "video/quicktime",
        "video/x-ms-wmv", "video/x-flv", "video/webm", "video/mpeg",
        // Audio
        "audio/mpeg", "audio/wav", "audio/flac", "audio/aac",
        "audio/ogg", "audio/x-ms-wma", "audio/mp4", "audio/opus"
    )
    override val category = MimeCategory.BINARY
    override val priority = 20

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val mediaInfo = extractMediaInfo(content.content, content.name)
            val xhtml = generateMediaXhtml(mediaInfo, content.name)

            ProcessingResult(
                success = true,
                contentType = "application/xhtml+xml",
                processedContent = xhtml,
                metadata = mediaInfoToMap(mediaInfo)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to extract media info: ${content.name}" }
            ProcessingResult(
                success = false,
                contentType = "application/octet-stream",
                error = e.message
            )
        }
    }

    override suspend fun validate(content: FileContent): Boolean {
        return try {
            extractMediaInfo(content.content, content.name)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        return try {
            val mediaInfo = extractMediaInfo(content.content, content.name)
            mediaInfoToMap(mediaInfo)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Failed to extract media info"))
        }
    }

    /**
     * Extract media information using JAVE (FFmpeg wrapper)
     */
    fun extractMediaInfo(bytes: ByteArray, filename: String): MediaInfo {
        val extension = filename.substringAfterLast('.')
        val tempFile = Files.createTempFile("media", ".$extension").toFile()

        try {
            tempFile.writeBytes(bytes)
            val multimediaObject = MultimediaObject(tempFile)
            val info = multimediaObject.info

            val videoInfo = info.video?.let { video ->
                VideoStreamInfo(
                    codec = video.decoder,
                    width = video.size?.width ?: 0,
                    height = video.size?.height ?: 0,
                    frameRate = video.frameRate,
                    bitRate = video.bitRate
                )
            }

            val audioInfo = info.audio?.let { audio ->
                AudioStreamInfo(
                    codec = audio.decoder,
                    channels = audio.channels,
                    sampleRate = audio.samplingRate,
                    bitRate = audio.bitRate
                )
            }

            return MediaInfo(
                duration = info.duration,
                format = info.format ?: extension.uppercase(),
                fileSize = bytes.size.toLong(),
                bitRate = info.audio?.bitRate ?: info.video?.bitRate,
                video = videoInfo,
                audio = audioInfo
            )
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Generate XHTML with media info display
     */
    private fun generateMediaXhtml(info: MediaInfo, filename: String): String {
        val isVideo = info.video != null
        val duration = formatDuration(info.duration)

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
            <head>
                <meta http-equiv="Content-Type" content="application/xhtml+xml; charset=UTF-8" />
                <title>${escapeHtml(filename)} - Media Info</title>
                <style type="text/css">
                    .container { max-width: 800px; margin: 0 auto; padding: 20px; }
                    .card { background: #fff; border: 1px solid #ddd; border-radius: 4px; margin-bottom: 20px; }
                    .card-header { background: #f5f5f5; padding: 15px; border-bottom: 1px solid #ddd; font-weight: bold; }
                    .card-body { padding: 15px; }
                    .info-table { width: 100%; border-collapse: collapse; }
                    .info-table th, .info-table td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; }
                    .info-table th { width: 40%; color: #666; }
                    .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; margin: 2px; }
                    .badge-video { background: #9c27b0; color: white; }
                    .badge-audio { background: #2196f3; color: white; }
                    .badge-info { background: #4caf50; color: white; }
                    .icon { font-size: 48px; text-align: center; padding: 20px; }
                </style>
            </head>
            <body class="container">
                <h1>${escapeHtml(filename)}</h1>
                <p>
                    <span class="badge ${if (isVideo) "badge-video" else "badge-audio"}">${if (isVideo) "Video" else "Audio"}</span>
                    <span class="badge badge-info">${info.format}</span>
                    <span class="badge badge-info">$duration</span>
                    <span class="badge badge-info">${formatFileSize(info.fileSize)}</span>
                </p>

                <div class="icon">${if (isVideo) "🎬" else "🎵"}</div>

                <div class="card">
                    <div class="card-header">General Information</div>
                    <div class="card-body">
                        <table class="info-table">
                            <tr><th>Format</th><td>${info.format}</td></tr>
                            <tr><th>Duration</th><td>$duration</td></tr>
                            <tr><th>File Size</th><td>${formatFileSize(info.fileSize)}</td></tr>
                            ${info.bitRate?.let { "<tr><th>Bit Rate</th><td>${formatBitRate(it)}</td></tr>" } ?: ""}
                        </table>
                    </div>
                </div>

                ${info.video?.let { video -> """
                <div class="card">
                    <div class="card-header">Video Stream</div>
                    <div class="card-body">
                        <table class="info-table">
                            ${video.codec?.let { "<tr><th>Codec</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                            <tr><th>Resolution</th><td>${video.width}x${video.height}</td></tr>
                            ${video.frameRate?.let { "<tr><th>Frame Rate</th><td>${String.format("%.2f", it)} fps</td></tr>" } ?: ""}
                            ${video.bitRate?.let { "<tr><th>Bit Rate</th><td>${formatBitRate(it)}</td></tr>" } ?: ""}
                        </table>
                    </div>
                </div>
                """ } ?: ""}

                ${info.audio?.let { audio -> """
                <div class="card">
                    <div class="card-header">Audio Stream</div>
                    <div class="card-body">
                        <table class="info-table">
                            ${audio.codec?.let { "<tr><th>Codec</th><td>${escapeHtml(it)}</td></tr>" } ?: ""}
                            ${audio.channels?.let { "<tr><th>Channels</th><td>$it</td></tr>" } ?: ""}
                            ${audio.sampleRate?.let { "<tr><th>Sample Rate</th><td>${it} Hz</td></tr>" } ?: ""}
                            ${audio.bitRate?.let { "<tr><th>Bit Rate</th><td>${formatBitRate(it)}</td></tr>" } ?: ""}
                        </table>
                    </div>
                </div>
                """ } ?: ""}
            </body>
            </html>
        """.trimIndent()
    }

    private fun mediaInfoToMap(info: MediaInfo): Map<String, String> {
        return buildMap {
            put("duration", info.duration.toString())
            put("format", info.format)
            put("fileSize", info.fileSize.toString())
            info.bitRate?.let { put("bitRate", it.toString()) }
            info.video?.let { video ->
                put("video.width", video.width.toString())
                put("video.height", video.height.toString())
                video.codec?.let { put("video.codec", it) }
                video.frameRate?.let { put("video.frameRate", it.toString()) }
            }
            info.audio?.let { audio ->
                audio.codec?.let { put("audio.codec", it) }
                audio.channels?.let { put("audio.channels", it.toString()) }
                audio.sampleRate?.let { put("audio.sampleRate", it.toString()) }
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }

    private fun formatBitRate(bitRate: Int): String {
        return when {
            bitRate >= 1_000_000 -> "${bitRate / 1_000_000} Mbps"
            bitRate >= 1_000 -> "${bitRate / 1_000} kbps"
            else -> "$bitRate bps"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "${bytes / 1_000_000_000} GB"
            bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
            bytes >= 1_000 -> "${bytes / 1_000} KB"
            else -> "$bytes bytes"
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
