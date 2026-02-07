package al.clk.gui.server.handlers

import mu.KotlinLogging
import al.clk.gui.server.di.MimeCategory
import al.clk.gui.server.svg.SvgManipulator
import al.clk.gui.server.svg.SvgOutputFormat
import al.clk.gui.server.svg.SvgTranscoder
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private val logger = KotlinLogging.logger {}

/**
 * Base implementation for image handlers
 */
abstract class AbstractImageHandler : ImageHandler {
    override val category: MimeCategory = MimeCategory.IMAGE

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val dimensions = getDimensions(content)
            val metadata = extractMetadata(content)

            ProcessingResult(
                success = true,
                contentType = primaryMimeType,
                data = java.util.Base64.getEncoder().encodeToString(content.content),
                metadata = metadata + mapOf(
                    "width" to (dimensions?.width?.toString() ?: "unknown"),
                    "height" to (dimensions?.height?.toString() ?: "unknown")
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process image ${content.name}" }
            ProcessingResult(
                success = false,
                contentType = primaryMimeType,
                error = e.message
            )
        }
    }

    override suspend fun validate(content: FileContent): Boolean {
        return getDimensions(content) != null
    }
}

/**
 * PNG image handler
 */
class PngHandler : AbstractImageHandler() {
    override val handlerId = "png"
    override val displayName = "PNG Image"
    override val supportedExtensions = setOf("png")
    override val mimeTypes = setOf("image/png")
    override val priority = 10

    override suspend fun getDimensions(content: FileContent): ImageDimensions? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content))
            ImageDimensions(
                width = image.width,
                height = image.height,
                colorDepth = image.colorModel.pixelSize,
                hasAlpha = image.colorModel.hasAlpha()
            )
        } catch (e: Exception) {
            logger.warn { "Failed to get PNG dimensions: ${e.message}" }
            null
        }
    }

    override suspend fun convert(content: FileContent, targetFormat: String): ByteArray? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content))
            val output = ByteArrayOutputStream()
            ImageIO.write(image, targetFormat.lowercase(), output)
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to convert PNG: ${e.message}" }
            null
        }
    }

    override suspend fun thumbnail(content: FileContent, maxWidth: Int, maxHeight: Int): ByteArray? {
        return try {
            val original = ImageIO.read(ByteArrayInputStream(content.content))
            val scaled = scaleImage(original, maxWidth, maxHeight)
            val output = ByteArrayOutputStream()
            ImageIO.write(scaled, "png", output)
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to create thumbnail: ${e.message}" }
            null
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val dims = getDimensions(content)
        return mapOf(
            "format" to "PNG",
            "width" to (dims?.width?.toString() ?: "unknown"),
            "height" to (dims?.height?.toString() ?: "unknown"),
            "hasAlpha" to (dims?.hasAlpha?.toString() ?: "unknown"),
            "size" to content.size.toString()
        )
    }
}

/**
 * JPEG image handler
 */
class JpegHandler : AbstractImageHandler() {
    override val handlerId = "jpeg"
    override val displayName = "JPEG Image"
    override val supportedExtensions = setOf("jpg", "jpeg")
    override val mimeTypes = setOf("image/jpeg")
    override val priority = 10

    override suspend fun getDimensions(content: FileContent): ImageDimensions? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content))
            ImageDimensions(
                width = image.width,
                height = image.height,
                colorDepth = image.colorModel.pixelSize,
                hasAlpha = false
            )
        } catch (e: Exception) {
            logger.warn { "Failed to get JPEG dimensions: ${e.message}" }
            null
        }
    }

    override suspend fun convert(content: FileContent, targetFormat: String): ByteArray? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content))
            val output = ByteArrayOutputStream()
            ImageIO.write(image, targetFormat.lowercase(), output)
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to convert JPEG: ${e.message}" }
            null
        }
    }

    override suspend fun thumbnail(content: FileContent, maxWidth: Int, maxHeight: Int): ByteArray? {
        return try {
            val original = ImageIO.read(ByteArrayInputStream(content.content))
            val scaled = scaleImage(original, maxWidth, maxHeight)
            val output = ByteArrayOutputStream()
            ImageIO.write(scaled, "jpg", output)
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to create thumbnail: ${e.message}" }
            null
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val dims = getDimensions(content)
        return mapOf(
            "format" to "JPEG",
            "width" to (dims?.width?.toString() ?: "unknown"),
            "height" to (dims?.height?.toString() ?: "unknown"),
            "size" to content.size.toString()
        )
    }
}

/**
 * GIF image handler
 */
class GifHandler : AbstractImageHandler() {
    override val handlerId = "gif"
    override val displayName = "GIF Image"
    override val supportedExtensions = setOf("gif")
    override val mimeTypes = setOf("image/gif")
    override val priority = 10

    override suspend fun getDimensions(content: FileContent): ImageDimensions? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content))
            ImageDimensions(
                width = image.width,
                height = image.height,
                colorDepth = image.colorModel.pixelSize,
                hasAlpha = image.colorModel.hasAlpha()
            )
        } catch (e: Exception) {
            logger.warn { "Failed to get GIF dimensions: ${e.message}" }
            null
        }
    }

    override suspend fun convert(content: FileContent, targetFormat: String): ByteArray? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content))
            val output = ByteArrayOutputStream()
            ImageIO.write(image, targetFormat.lowercase(), output)
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to convert GIF: ${e.message}" }
            null
        }
    }

    override suspend fun thumbnail(content: FileContent, maxWidth: Int, maxHeight: Int): ByteArray? {
        return try {
            val original = ImageIO.read(ByteArrayInputStream(content.content))
            val scaled = scaleImage(original, maxWidth, maxHeight)
            val output = ByteArrayOutputStream()
            ImageIO.write(scaled, "gif", output)
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to create thumbnail: ${e.message}" }
            null
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val dims = getDimensions(content)
        return mapOf(
            "format" to "GIF",
            "width" to (dims?.width?.toString() ?: "unknown"),
            "height" to (dims?.height?.toString() ?: "unknown"),
            "size" to content.size.toString()
        )
    }
}

/**
 * WebP image handler
 */
class WebPHandler : AbstractImageHandler() {
    override val handlerId = "webp"
    override val displayName = "WebP Image"
    override val supportedExtensions = setOf("webp")
    override val mimeTypes = setOf("image/webp")
    override val priority = 10

    override suspend fun getDimensions(content: FileContent): ImageDimensions? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content))
            if (image != null) {
                ImageDimensions(
                    width = image.width,
                    height = image.height,
                    colorDepth = image.colorModel.pixelSize,
                    hasAlpha = image.colorModel.hasAlpha()
                )
            } else null
        } catch (e: Exception) {
            logger.warn { "Failed to get WebP dimensions: ${e.message}" }
            null
        }
    }

    override suspend fun convert(content: FileContent, targetFormat: String): ByteArray? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content)) ?: return null
            val output = ByteArrayOutputStream()
            ImageIO.write(image, targetFormat.lowercase(), output)
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to convert WebP: ${e.message}" }
            null
        }
    }

    override suspend fun thumbnail(content: FileContent, maxWidth: Int, maxHeight: Int): ByteArray? {
        return try {
            val original = ImageIO.read(ByteArrayInputStream(content.content)) ?: return null
            val scaled = scaleImage(original, maxWidth, maxHeight)
            val output = ByteArrayOutputStream()
            ImageIO.write(scaled, "png", output) // WebP write may not be supported
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to create thumbnail: ${e.message}" }
            null
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val dims = getDimensions(content)
        return mapOf(
            "format" to "WebP",
            "width" to (dims?.width?.toString() ?: "unknown"),
            "height" to (dims?.height?.toString() ?: "unknown"),
            "size" to content.size.toString()
        )
    }
}

/**
 * BMP image handler
 */
class BmpHandler : AbstractImageHandler() {
    override val handlerId = "bmp"
    override val displayName = "BMP Image"
    override val supportedExtensions = setOf("bmp")
    override val mimeTypes = setOf("image/bmp")
    override val priority = 10

    override suspend fun getDimensions(content: FileContent): ImageDimensions? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content))
            ImageDimensions(
                width = image.width,
                height = image.height,
                colorDepth = image.colorModel.pixelSize,
                hasAlpha = image.colorModel.hasAlpha()
            )
        } catch (e: Exception) {
            logger.warn { "Failed to get BMP dimensions: ${e.message}" }
            null
        }
    }

    override suspend fun convert(content: FileContent, targetFormat: String): ByteArray? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content))
            val output = ByteArrayOutputStream()
            ImageIO.write(image, targetFormat.lowercase(), output)
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to convert BMP: ${e.message}" }
            null
        }
    }

    override suspend fun thumbnail(content: FileContent, maxWidth: Int, maxHeight: Int): ByteArray? {
        return try {
            val original = ImageIO.read(ByteArrayInputStream(content.content))
            val scaled = scaleImage(original, maxWidth, maxHeight)
            val output = ByteArrayOutputStream()
            ImageIO.write(scaled, "bmp", output)
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to create thumbnail: ${e.message}" }
            null
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val dims = getDimensions(content)
        return mapOf(
            "format" to "BMP",
            "width" to (dims?.width?.toString() ?: "unknown"),
            "height" to (dims?.height?.toString() ?: "unknown"),
            "size" to content.size.toString()
        )
    }
}

/**
 * SVG image handler using Apache Batik
 */
class SvgHandler : AbstractImageHandler() {
    override val handlerId = "svg"
    override val displayName = "SVG Image"
    override val supportedExtensions = setOf("svg")
    override val mimeTypes = setOf("image/svg+xml")
    override val priority = 10

    private val transcoder = SvgTranscoder()

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val metadata = extractMetadata(content)
            ProcessingResult(
                success = true,
                contentType = primaryMimeType,
                data = content.asString(),
                metadata = metadata
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process SVG ${content.name}" }
            ProcessingResult(
                success = false,
                contentType = primaryMimeType,
                error = e.message
            )
        }
    }

    override suspend fun getDimensions(content: FileContent): ImageDimensions? {
        return try {
            val doc = SvgManipulator.parse(content.asString())
            val root = doc.rootElement
            val width = root.getAttribute("width").replace(Regex("[^0-9.]"), "").toIntOrNull() ?: 0
            val height = root.getAttribute("height").replace(Regex("[^0-9.]"), "").toIntOrNull() ?: 0
            ImageDimensions(width = width, height = height, hasAlpha = true)
        } catch (e: Exception) {
            logger.warn { "Failed to get SVG dimensions: ${e.message}" }
            null
        }
    }

    override suspend fun convert(content: FileContent, targetFormat: String): ByteArray? {
        return try {
            val format = when (targetFormat.lowercase()) {
                "png" -> SvgOutputFormat.PNG
                "jpeg", "jpg" -> SvgOutputFormat.JPEG
                "tiff", "tif" -> SvgOutputFormat.TIFF
                "pdf" -> SvgOutputFormat.PDF
                else -> return null
            }
            transcoder.transcode(content.asString(), format)
        } catch (e: Exception) {
            logger.warn { "Failed to convert SVG: ${e.message}" }
            null
        }
    }

    override suspend fun thumbnail(content: FileContent, maxWidth: Int, maxHeight: Int): ByteArray? {
        return try {
            val options = al.clk.gui.server.svg.SvgManipulationOptions(
                width = maxWidth.toFloat(),
                height = maxHeight.toFloat()
            )
            transcoder.transcode(content.asString(), SvgOutputFormat.PNG, options)
        } catch (e: Exception) {
            logger.warn { "Failed to create SVG thumbnail: ${e.message}" }
            null
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val dims = getDimensions(content)
        val svgContent = content.asString()

        return mapOf(
            "format" to "SVG",
            "width" to (dims?.width?.toString() ?: "unknown"),
            "height" to (dims?.height?.toString() ?: "unknown"),
            "hasViewBox" to svgContent.contains("viewBox").toString(),
            "elementCount" to Regex("""<\w+""").findAll(svgContent).count().toString(),
            "size" to content.size.toString()
        )
    }
}

/**
 * ICO icon handler
 */
class IcoHandler : AbstractImageHandler() {
    override val handlerId = "ico"
    override val displayName = "ICO Icon"
    override val supportedExtensions = setOf("ico")
    override val mimeTypes = setOf("image/x-icon", "image/vnd.microsoft.icon")
    override val priority = 10

    override suspend fun getDimensions(content: FileContent): ImageDimensions? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content))
            if (image != null) {
                ImageDimensions(
                    width = image.width,
                    height = image.height,
                    colorDepth = image.colorModel.pixelSize,
                    hasAlpha = image.colorModel.hasAlpha()
                )
            } else null
        } catch (e: Exception) {
            logger.warn { "Failed to get ICO dimensions: ${e.message}" }
            null
        }
    }

    override suspend fun convert(content: FileContent, targetFormat: String): ByteArray? {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(content.content)) ?: return null
            val output = ByteArrayOutputStream()
            ImageIO.write(image, targetFormat.lowercase(), output)
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to convert ICO: ${e.message}" }
            null
        }
    }

    override suspend fun thumbnail(content: FileContent, maxWidth: Int, maxHeight: Int): ByteArray? {
        return try {
            val original = ImageIO.read(ByteArrayInputStream(content.content)) ?: return null
            val scaled = scaleImage(original, maxWidth, maxHeight)
            val output = ByteArrayOutputStream()
            ImageIO.write(scaled, "png", output)
            output.toByteArray()
        } catch (e: Exception) {
            logger.warn { "Failed to create thumbnail: ${e.message}" }
            null
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val dims = getDimensions(content)
        return mapOf(
            "format" to "ICO",
            "width" to (dims?.width?.toString() ?: "unknown"),
            "height" to (dims?.height?.toString() ?: "unknown"),
            "size" to content.size.toString()
        )
    }
}

/**
 * Utility function to scale an image
 */
private fun scaleImage(original: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
    val widthRatio = maxWidth.toDouble() / original.width
    val heightRatio = maxHeight.toDouble() / original.height
    val ratio = minOf(widthRatio, heightRatio, 1.0)

    val newWidth = (original.width * ratio).toInt()
    val newHeight = (original.height * ratio).toInt()

    val scaled = BufferedImage(newWidth, newHeight, original.type.takeIf { it != 0 } ?: BufferedImage.TYPE_INT_ARGB)
    val g = scaled.createGraphics()
    g.drawImage(original, 0, 0, newWidth, newHeight, null)
    g.dispose()

    return scaled
}
