package al.clk.gui.server.svg

import mu.KotlinLogging
import org.apache.batik.anim.dom.SAXSVGDocumentFactory
import org.apache.batik.anim.dom.SVGDOMImplementation
import org.apache.batik.swing.JSVGCanvas
import org.apache.batik.swing.svg.SVGDocumentLoaderAdapter
import org.apache.batik.swing.svg.SVGDocumentLoaderEvent
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder
import org.apache.batik.transcoder.image.JPEGTranscoder
import org.apache.batik.transcoder.image.PNGTranscoder
import org.apache.batik.transcoder.image.TIFFTranscoder
import org.apache.batik.util.XMLResourceDescriptor
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.svg.SVGDocument
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.AffineTransform
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private val logger = KotlinLogging.logger {}

/**
 * SVG output format for transcoding
 */
enum class SvgOutputFormat {
    PNG,
    JPEG,
    TIFF,
    PDF
}

/**
 * SVG viewer state
 */
data class SvgViewerState(
    val zoomLevel: Double = 1.0,
    val panX: Double = 0.0,
    val panY: Double = 0.0,
    val rotation: Double = 0.0,
    val documentUri: String? = null
)

/**
 * SVG manipulation options
 */
data class SvgManipulationOptions(
    val width: Float? = null,
    val height: Float? = null,
    val backgroundColor: Color? = null,
    val quality: Float = 0.95f,
    val dpi: Float = 96f
)

/**
 * Batik-based SVG viewer panel for Swing applications
 */
class BatikSvgViewerPanel : JPanel(BorderLayout()) {

    private val canvas: JSVGCanvas = JSVGCanvas()
    private val scrollPane: JScrollPane
    private var state = SvgViewerState()
    private var currentDocument: SVGDocument? = null

    private var onDocumentLoaded: ((SVGDocument) -> Unit)? = null
    private var onError: ((Exception) -> Unit)? = null

    init {
        // Configure canvas
        canvas.documentState = JSVGCanvas.ALWAYS_DYNAMIC
        canvas.isDoubleBuffered = true
        canvas.background = Color.WHITE

        // Add document loader listener
        canvas.addSVGDocumentLoaderListener(object : SVGDocumentLoaderAdapter() {
            override fun documentLoadingCompleted(e: SVGDocumentLoaderEvent) {
                currentDocument = canvas.svgDocument
                onDocumentLoaded?.invoke(canvas.svgDocument)
                logger.debug { "SVG document loaded successfully" }
            }

            override fun documentLoadingFailed(e: SVGDocumentLoaderEvent) {
                logger.error { "Failed to load SVG document" }
                onError?.invoke(RuntimeException("Failed to load SVG document"))
            }
        })

        // Setup mouse controls for pan/zoom
        setupMouseControls()

        // Add to scroll pane
        scrollPane = JScrollPane(canvas)
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED

        add(scrollPane, BorderLayout.CENTER)

        preferredSize = Dimension(800, 600)
    }

    private fun setupMouseControls() {
        var lastX = 0
        var lastY = 0
        var isDragging = false

        canvas.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isMiddleMouseButton(e) ||
                    (SwingUtilities.isLeftMouseButton(e) && e.isControlDown)) {
                    lastX = e.x
                    lastY = e.y
                    isDragging = true
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                isDragging = false
            }
        })

        canvas.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (isDragging) {
                    val dx = e.x - lastX
                    val dy = e.y - lastY
                    pan(dx.toDouble(), dy.toDouble())
                    lastX = e.x
                    lastY = e.y
                }
            }
        })

        canvas.addMouseWheelListener { e: MouseWheelEvent ->
            val rotation = e.wheelRotation
            val zoomFactor = if (rotation < 0) 1.1 else 0.9
            zoom(state.zoomLevel * zoomFactor)
        }
    }

    /**
     * Load SVG from a string
     */
    fun loadSvgFromString(svgContent: String) {
        try {
            val parser = XMLResourceDescriptor.getXMLParserClassName()
            val factory = SAXSVGDocumentFactory(parser)
            val document = factory.createSVGDocument(
                "http://www.w3.org/2000/svg",
                StringReader(svgContent)
            )
            canvas.svgDocument = document
            state = state.copy(zoomLevel = 1.0, panX = 0.0, panY = 0.0)
        } catch (e: Exception) {
            logger.error(e) { "Failed to load SVG from string" }
            onError?.invoke(e)
        }
    }

    /**
     * Load SVG from a file
     */
    fun loadSvgFromFile(file: File) {
        try {
            canvas.uri = file.toURI().toString()
            state = state.copy(documentUri = file.toURI().toString())
        } catch (e: Exception) {
            logger.error(e) { "Failed to load SVG from file: ${file.path}" }
            onError?.invoke(e)
        }
    }

    /**
     * Load SVG from a URI
     */
    fun loadSvgFromUri(uri: URI) {
        try {
            canvas.uri = uri.toString()
            state = state.copy(documentUri = uri.toString())
        } catch (e: Exception) {
            logger.error(e) { "Failed to load SVG from URI: $uri" }
            onError?.invoke(e)
        }
    }

    /**
     * Zoom to specified level
     */
    fun zoom(level: Double) {
        val clampedLevel = level.coerceIn(0.1, 10.0)
        state = state.copy(zoomLevel = clampedLevel)
        applyTransform()
    }

    /**
     * Zoom in by factor
     */
    fun zoomIn(factor: Double = 1.2) {
        zoom(state.zoomLevel * factor)
    }

    /**
     * Zoom out by factor
     */
    fun zoomOut(factor: Double = 1.2) {
        zoom(state.zoomLevel / factor)
    }

    /**
     * Reset zoom to 100%
     */
    fun resetZoom() {
        zoom(1.0)
    }

    /**
     * Fit SVG to viewport
     */
    fun fitToViewport() {
        canvas.setRenderingTransform(AffineTransform())
        state = state.copy(zoomLevel = 1.0, panX = 0.0, panY = 0.0)
    }

    /**
     * Pan by offset
     */
    fun pan(dx: Double, dy: Double) {
        state = state.copy(
            panX = state.panX + dx,
            panY = state.panY + dy
        )
        applyTransform()
    }

    /**
     * Rotate by degrees
     */
    fun rotate(degrees: Double) {
        state = state.copy(rotation = (state.rotation + degrees) % 360)
        applyTransform()
    }

    private fun applyTransform() {
        val transform = AffineTransform()
        transform.translate(state.panX, state.panY)
        transform.scale(state.zoomLevel, state.zoomLevel)
        transform.rotate(Math.toRadians(state.rotation))
        canvas.setRenderingTransform(transform)
    }

    /**
     * Get current viewer state
     */
    fun getState(): SvgViewerState = state

    /**
     * Set document loaded callback
     */
    fun setOnDocumentLoaded(callback: (SVGDocument) -> Unit) {
        onDocumentLoaded = callback
    }

    /**
     * Set error callback
     */
    fun setOnError(callback: (Exception) -> Unit) {
        onError = callback
    }

    /**
     * Get the current SVG document
     */
    fun getSvgDocument(): SVGDocument? = currentDocument

    /**
     * Get the canvas component
     */
    fun getCanvas(): JSVGCanvas = canvas
}

/**
 * SVG transcoder for converting SVG to various raster formats
 */
class SvgTranscoder {

    /**
     * Transcode SVG string to image bytes
     */
    fun transcode(
        svgContent: String,
        format: SvgOutputFormat,
        options: SvgManipulationOptions = SvgManipulationOptions()
    ): ByteArray {
        val transcoder = when (format) {
            SvgOutputFormat.PNG -> PNGTranscoder()
            SvgOutputFormat.JPEG -> JPEGTranscoder().apply {
                addTranscodingHint(JPEGTranscoder.KEY_QUALITY, options.quality)
            }
            SvgOutputFormat.TIFF -> TIFFTranscoder()
            SvgOutputFormat.PDF -> createPdfTranscoder()
        }

        // Set dimensions if provided
        options.width?.let {
            transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, it)
        }
        options.height?.let {
            transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, it)
        }
        options.backgroundColor?.let {
            transcoder.addTranscodingHint(ImageTranscoder.KEY_BACKGROUND_COLOR, it)
        }
        transcoder.addTranscodingHint(ImageTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, 25.4f / options.dpi)

        val input = TranscoderInput(StringReader(svgContent))
        val outputStream = ByteArrayOutputStream()
        val output = TranscoderOutput(outputStream)

        transcoder.transcode(input, output)

        return outputStream.toByteArray()
    }

    /**
     * Transcode SVG bytes to image bytes
     */
    fun transcode(
        svgBytes: ByteArray,
        format: SvgOutputFormat,
        options: SvgManipulationOptions = SvgManipulationOptions()
    ): ByteArray {
        return transcode(String(svgBytes, Charsets.UTF_8), format, options)
    }

    /**
     * Transcode SVG file to image file
     */
    fun transcodeFile(
        inputFile: File,
        outputFile: File,
        format: SvgOutputFormat,
        options: SvgManipulationOptions = SvgManipulationOptions()
    ) {
        val svgContent = inputFile.readText()
        val imageBytes = transcode(svgContent, format, options)
        outputFile.writeBytes(imageBytes)
    }

    private fun createPdfTranscoder(): ImageTranscoder {
        // Use FOP PDF transcoder if available, otherwise fall back to PNG
        return try {
            Class.forName("org.apache.fop.svg.PDFTranscoder")
                .getDeclaredConstructor()
                .newInstance() as ImageTranscoder
        } catch (e: Exception) {
            logger.warn { "PDF transcoder not available, using PNG instead" }
            PNGTranscoder()
        }
    }
}

/**
 * SVG manipulation utilities
 */
object SvgManipulator {

    private val documentFactory: SAXSVGDocumentFactory by lazy {
        val parser = XMLResourceDescriptor.getXMLParserClassName()
        SAXSVGDocumentFactory(parser)
    }

    /**
     * Parse SVG string to document
     */
    fun parse(svgContent: String): SVGDocument {
        return documentFactory.createSVGDocument(
            SVGDOMImplementation.SVG_NAMESPACE_URI,
            StringReader(svgContent)
        )
    }

    /**
     * Parse SVG bytes to document
     */
    fun parse(svgBytes: ByteArray): SVGDocument {
        return documentFactory.createSVGDocument(
            SVGDOMImplementation.SVG_NAMESPACE_URI,
            ByteArrayInputStream(svgBytes)
        )
    }

    /**
     * Serialize SVG document to string
     */
    fun serialize(document: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")

        val writer = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }

    /**
     * Set SVG dimensions
     */
    fun setDimensions(document: SVGDocument, width: String, height: String): SVGDocument {
        val root = document.rootElement
        root.setAttribute("width", width)
        root.setAttribute("height", height)
        return document
    }

    /**
     * Set SVG viewBox
     */
    fun setViewBox(document: SVGDocument, minX: Double, minY: Double, width: Double, height: Double): SVGDocument {
        val root = document.rootElement
        root.setAttribute("viewBox", "$minX $minY $width $height")
        return document
    }

    /**
     * Add a CSS style to the SVG
     */
    fun addStyle(document: SVGDocument, css: String): SVGDocument {
        val defs = document.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, "defs")
        val style = document.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, "style")
        style.setAttribute("type", "text/css")
        style.textContent = css
        defs.appendChild(style)
        document.rootElement.insertBefore(defs, document.rootElement.firstChild)
        return document
    }

    /**
     * Add a transform to the root element
     */
    fun addRootTransform(document: SVGDocument, transform: String): SVGDocument {
        val root = document.rootElement
        val existing = root.getAttribute("transform")
        root.setAttribute("transform", if (existing.isNotEmpty()) "$existing $transform" else transform)
        return document
    }

    /**
     * Find elements by ID
     */
    fun getElementById(document: SVGDocument, id: String): Element? {
        return document.getElementById(id)
    }

    /**
     * Find elements by tag name
     */
    fun getElementsByTagName(document: SVGDocument, tagName: String): List<Element> {
        val nodeList = document.getElementsByTagNameNS(SVGDOMImplementation.SVG_NAMESPACE_URI, tagName)
        return (0 until nodeList.length).map { nodeList.item(it) as Element }
    }

    /**
     * Add a watermark to the SVG
     */
    fun addWatermark(document: SVGDocument, text: String, opacity: Double = 0.3): SVGDocument {
        val watermark = document.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, "text")
        watermark.setAttribute("x", "50%")
        watermark.setAttribute("y", "50%")
        watermark.setAttribute("text-anchor", "middle")
        watermark.setAttribute("dominant-baseline", "middle")
        watermark.setAttribute("font-size", "48")
        watermark.setAttribute("fill", "gray")
        watermark.setAttribute("opacity", opacity.toString())
        watermark.setAttribute("transform", "rotate(-45)")
        watermark.textContent = text
        document.rootElement.appendChild(watermark)
        return document
    }

    /**
     * Optimize SVG by removing unnecessary elements
     */
    fun optimize(document: SVGDocument): SVGDocument {
        // Remove comments
        removeComments(document.rootElement)

        // Remove empty groups
        removeEmptyGroups(document)

        // Compact whitespace in text content
        compactWhitespace(document.rootElement)

        return document
    }

    private fun removeComments(element: Element) {
        val children = element.childNodes
        val toRemove = mutableListOf<org.w3c.dom.Node>()

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == org.w3c.dom.Node.COMMENT_NODE) {
                toRemove.add(child)
            } else if (child is Element) {
                removeComments(child)
            }
        }

        toRemove.forEach { element.removeChild(it) }
    }

    private fun removeEmptyGroups(document: SVGDocument) {
        val groups = getElementsByTagName(document, "g")
        groups.filter { it.childNodes.length == 0 }
            .forEach { it.parentNode?.removeChild(it) }
    }

    private fun compactWhitespace(element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == org.w3c.dom.Node.TEXT_NODE) {
                child.textContent = child.textContent?.trim()?.replace(Regex("\\s+"), " ")
            } else if (child is Element) {
                compactWhitespace(child)
            }
        }
    }

    /**
     * Merge multiple SVG documents into one
     */
    fun merge(documents: List<SVGDocument>, layout: MergeLayout = MergeLayout.VERTICAL): SVGDocument {
        if (documents.isEmpty()) {
            throw IllegalArgumentException("Cannot merge empty list of documents")
        }

        if (documents.size == 1) {
            return documents.first()
        }

        val impl = SVGDOMImplementation.getDOMImplementation()
        val merged = impl.createDocument(
            SVGDOMImplementation.SVG_NAMESPACE_URI,
            "svg",
            null
        ) as SVGDocument

        var offsetX = 0.0
        var offsetY = 0.0
        var maxWidth = 0.0
        var maxHeight = 0.0

        documents.forEach { doc ->
            val width = doc.rootElement.getAttribute("width").toDoubleOrNull() ?: 100.0
            val height = doc.rootElement.getAttribute("height").toDoubleOrNull() ?: 100.0

            val group = merged.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, "g")
            group.setAttribute("transform", "translate($offsetX, $offsetY)")

            // Copy children
            val children = doc.rootElement.childNodes
            for (i in 0 until children.length) {
                val imported = merged.importNode(children.item(i), true)
                group.appendChild(imported)
            }

            merged.rootElement.appendChild(group)

            when (layout) {
                MergeLayout.HORIZONTAL -> {
                    offsetX += width
                    maxHeight = maxOf(maxHeight, height)
                    maxWidth = offsetX
                }
                MergeLayout.VERTICAL -> {
                    offsetY += height
                    maxWidth = maxOf(maxWidth, width)
                    maxHeight = offsetY
                }
                MergeLayout.GRID -> {
                    // Grid layout with 3 columns
                    val col = documents.indexOf(doc) % 3
                    val row = documents.indexOf(doc) / 3
                    offsetX = col * width
                    offsetY = row * height
                    maxWidth = maxOf(maxWidth, offsetX + width)
                    maxHeight = maxOf(maxHeight, offsetY + height)
                }
            }
        }

        merged.rootElement.setAttribute("width", maxWidth.toString())
        merged.rootElement.setAttribute("height", maxHeight.toString())
        merged.rootElement.setAttribute("viewBox", "0 0 $maxWidth $maxHeight")

        return merged
    }

    enum class MergeLayout {
        HORIZONTAL,
        VERTICAL,
        GRID
    }
}

/**
 * SVG viewer factory
 */
object BatikSvgViewerFactory {

    /**
     * Create a new viewer panel
     */
    fun createViewerPanel(): BatikSvgViewerPanel {
        return BatikSvgViewerPanel()
    }

    /**
     * Create a transcoder
     */
    fun createTranscoder(): SvgTranscoder {
        return SvgTranscoder()
    }
}
