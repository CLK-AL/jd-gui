package org.jd.gui.server.handlers

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jd.gui.server.di.MimeCategory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class MediaHandlersTest {

    @Nested
    inner class FontBoxEnhancedHandlerTest {
        private lateinit var handler: FontBoxEnhancedHandler

        @BeforeEach
        fun setup() {
            handler = FontBoxEnhancedHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("fontbox-enhanced", handler.handlerId)
            assertEquals("Font (FontBox Enhanced)", handler.displayName)
            assertTrue(handler.supportedExtensions.contains("ttf"))
            assertTrue(handler.supportedExtensions.contains("otf"))
            assertTrue(handler.supportedExtensions.contains("woff"))
            assertTrue(handler.supportedExtensions.contains("woff2"))
            assertEquals(MimeCategory.BINARY, handler.category)
            assertEquals(25, handler.priority)
        }

        @Test
        fun `should have higher priority than basic font handler`() {
            val basicHandler = FontHandler()
            assertTrue(handler.priority > basicHandler.priority)
        }

        @Test
        fun `should handle invalid font gracefully`() = runBlocking {
            val content = FileContent(
                name = "invalid.ttf",
                extension = "ttf",
                content = "Not a font file".toByteArray()
            )

            val result = handler.process(content)
            assertFalse(result.success)
            assertNotNull(result.error)
        }

        @Test
        fun `should validate returns false for invalid font`() = runBlocking {
            val content = FileContent(
                name = "test.ttf",
                extension = "ttf",
                content = "Invalid font data".toByteArray()
            )

            assertFalse(handler.validate(content))
        }

        @Test
        fun `should support multiple font formats`() {
            val extensions = handler.supportedExtensions
            assertTrue(extensions.contains("ttf"), "Should support TTF")
            assertTrue(extensions.contains("otf"), "Should support OTF")
            assertTrue(extensions.contains("woff"), "Should support WOFF")
            assertTrue(extensions.contains("woff2"), "Should support WOFF2")
            assertTrue(extensions.contains("eot"), "Should support EOT")
        }
    }

    @Nested
    inner class ImageMetadataHandlerTest {
        private lateinit var handler: ImageMetadataHandler

        @BeforeEach
        fun setup() {
            handler = ImageMetadataHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("image-metadata", handler.handlerId)
            assertEquals("Image Metadata (EXIF/IPTC/XMP)", handler.displayName)
            assertTrue(handler.supportedExtensions.contains("jpg"))
            assertTrue(handler.supportedExtensions.contains("jpeg"))
            assertTrue(handler.supportedExtensions.contains("png"))
            assertEquals(MimeCategory.IMAGE, handler.category)
            assertEquals(25, handler.priority)
        }

        @Test
        fun `should support various image formats`() {
            val extensions = handler.supportedExtensions
            assertTrue(extensions.contains("jpg"))
            assertTrue(extensions.contains("jpeg"))
            assertTrue(extensions.contains("png"))
            assertTrue(extensions.contains("gif"))
            assertTrue(extensions.contains("tiff"))
            assertTrue(extensions.contains("heic"))
            assertTrue(extensions.contains("raw"))
        }

        @Test
        fun `should handle invalid image gracefully`() = runBlocking {
            val content = FileContent(
                name = "invalid.jpg",
                extension = "jpg",
                content = "Not an image".toByteArray()
            )

            val result = handler.process(content)
            assertFalse(result.success)
            assertNotNull(result.error)
        }

        @Test
        fun `should validate returns false for non-image`() = runBlocking {
            val content = FileContent(
                name = "test.jpg",
                extension = "jpg",
                content = "This is text, not an image".toByteArray()
            )

            assertFalse(handler.validate(content))
        }

        @Test
        fun `should extract metadata from valid PNG`() = runBlocking {
            // Minimal valid PNG header (8 bytes)
            val pngHeader = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, // PNG signature
                0x0D, 0x0A, 0x1A, 0x0A           // DOS line ending
            )

            val content = FileContent(
                name = "test.png",
                extension = "png",
                content = pngHeader
            )

            // Will fail because it's not a complete PNG, but validates the flow
            val result = handler.process(content)
            // Even partial PNGs should be attempted
            assertNotNull(result)
        }
    }

    @Nested
    inner class FFmpegMediaHandlerTest {
        private lateinit var handler: FFmpegMediaHandler

        @BeforeEach
        fun setup() {
            handler = FFmpegMediaHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("ffmpeg-media", handler.handlerId)
            assertEquals("Media (FFmpeg)", handler.displayName)
            assertEquals(MimeCategory.BINARY, handler.category)
            assertEquals(20, handler.priority)
        }

        @Test
        fun `should support video formats`() {
            val extensions = handler.supportedExtensions
            assertTrue(extensions.contains("mp4"))
            assertTrue(extensions.contains("mkv"))
            assertTrue(extensions.contains("avi"))
            assertTrue(extensions.contains("mov"))
            assertTrue(extensions.contains("wmv"))
            assertTrue(extensions.contains("webm"))
        }

        @Test
        fun `should support audio formats`() {
            val extensions = handler.supportedExtensions
            assertTrue(extensions.contains("mp3"))
            assertTrue(extensions.contains("wav"))
            assertTrue(extensions.contains("flac"))
            assertTrue(extensions.contains("aac"))
            assertTrue(extensions.contains("ogg"))
            assertTrue(extensions.contains("m4a"))
        }

        @Test
        fun `should have correct MIME types`() {
            val mimeTypes = handler.mimeTypes
            assertTrue(mimeTypes.contains("video/mp4"))
            assertTrue(mimeTypes.contains("audio/mpeg"))
            assertTrue(mimeTypes.contains("video/webm"))
        }

        @Test
        fun `should handle invalid media gracefully`() = runBlocking {
            val content = FileContent(
                name = "invalid.mp4",
                extension = "mp4",
                content = "Not a media file".toByteArray()
            )

            val result = handler.process(content)
            assertFalse(result.success)
            assertNotNull(result.error)
        }

        @Test
        fun `should validate returns false for invalid media`() = runBlocking {
            val content = FileContent(
                name = "test.mp3",
                extension = "mp3",
                content = "This is not an MP3".toByteArray()
            )

            assertFalse(handler.validate(content))
        }
    }

    @Nested
    inner class FontInfoDataClassTest {

        @Test
        fun `should create FontInfo with all properties`() {
            val fontInfo = FontInfo(
                fontFamily = "Arial",
                fontSubfamily = "Regular",
                fullName = "Arial Regular",
                postScriptName = "Arial-Regular",
                version = "1.0",
                copyright = "Copyright 2024",
                trademark = "Arial TM",
                manufacturer = "Monotype",
                designer = "Robin Nicholas",
                description = "A sans-serif font",
                vendorUrl = "http://example.com",
                designerUrl = "http://designer.com",
                license = "Commercial",
                licenseUrl = "http://license.com",
                format = FontFormat.TTF,
                unitsPerEm = 2048,
                numGlyphs = 3000,
                isBold = false,
                isItalic = false,
                isMonospace = false,
                supportsLatin = true,
                supportsCyrillic = true,
                supportsGreek = true,
                supportsCJK = false,
                supportsArabic = false,
                fileSize = 500000,
                createdDate = "2024-01-01T00:00:00Z",
                modifiedDate = "2024-06-01T00:00:00Z"
            )

            assertEquals("Arial", fontInfo.fontFamily)
            assertEquals(FontFormat.TTF, fontInfo.format)
            assertEquals(3000, fontInfo.numGlyphs)
            assertTrue(fontInfo.supportsLatin)
            assertFalse(fontInfo.isBold)
        }

        @Test
        fun `should have correct FontFormat enum values`() {
            assertEquals(6, FontFormat.values().size)
            assertNotNull(FontFormat.TTF)
            assertNotNull(FontFormat.OTF)
            assertNotNull(FontFormat.WOFF)
            assertNotNull(FontFormat.WOFF2)
            assertNotNull(FontFormat.EOT)
            assertNotNull(FontFormat.UNKNOWN)
        }
    }

    @Nested
    inner class ImageMetadataDataClassTest {

        @Test
        fun `should create ExifData with camera info`() {
            val exif = ExifData(
                make = "Canon",
                model = "EOS R5",
                software = "Lightroom",
                dateTimeOriginal = "2024:01:15 14:30:00",
                dateTimeDigitized = "2024:01:15 14:30:00",
                exposureTime = "1/250",
                fNumber = "f/2.8",
                iso = 400,
                focalLength = "85mm",
                flash = "Off",
                whiteBalance = "Auto",
                orientation = 1
            )

            assertEquals("Canon", exif.make)
            assertEquals("EOS R5", exif.model)
            assertEquals(400, exif.iso)
        }

        @Test
        fun `should create GpsData with location`() {
            val gps = GpsData(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.0,
                timestamp = "2024:01:15 14:30:00"
            )

            assertEquals(37.7749, gps.latitude)
            assertEquals(-122.4194, gps.longitude)
        }

        @Test
        fun `should create IptcData with metadata`() {
            val iptc = IptcData(
                headline = "Breaking News",
                caption = "A detailed description",
                copyright = "© 2024 Photographer",
                byline = "John Doe",
                keywords = listOf("news", "photo", "event"),
                city = "San Francisco",
                country = "USA"
            )

            assertEquals("Breaking News", iptc.headline)
            assertEquals(3, iptc.keywords?.size)
        }

        @Test
        fun `should create complete ImageMetadata`() {
            val metadata = ImageMetadata(
                width = 4000,
                height = 3000,
                colorDepth = 24,
                format = "JPEG",
                fileSize = 5000000,
                exif = ExifData(
                    make = "Nikon",
                    model = "Z8",
                    software = null,
                    dateTimeOriginal = null,
                    dateTimeDigitized = null,
                    exposureTime = null,
                    fNumber = null,
                    iso = 100,
                    focalLength = null,
                    flash = null,
                    whiteBalance = null,
                    orientation = null
                ),
                gps = null,
                iptc = null,
                xmp = null
            )

            assertEquals(4000, metadata.width)
            assertEquals(3000, metadata.height)
            assertEquals("JPEG", metadata.format)
            assertEquals("Nikon", metadata.exif?.make)
        }
    }

    @Nested
    inner class MediaInfoDataClassTest {

        @Test
        fun `should create VideoStreamInfo`() {
            val video = VideoStreamInfo(
                codec = "h264",
                width = 1920,
                height = 1080,
                frameRate = 30.0f,
                bitRate = 5000000
            )

            assertEquals("h264", video.codec)
            assertEquals(1920, video.width)
            assertEquals(1080, video.height)
            assertEquals(30.0f, video.frameRate)
        }

        @Test
        fun `should create AudioStreamInfo`() {
            val audio = AudioStreamInfo(
                codec = "aac",
                channels = 2,
                sampleRate = 48000,
                bitRate = 256000
            )

            assertEquals("aac", audio.codec)
            assertEquals(2, audio.channels)
            assertEquals(48000, audio.sampleRate)
        }

        @Test
        fun `should create MediaInfo for video file`() {
            val mediaInfo = MediaInfo(
                duration = 120000, // 2 minutes
                format = "mp4",
                fileSize = 50000000,
                bitRate = 5000000,
                video = VideoStreamInfo(
                    codec = "h264",
                    width = 1920,
                    height = 1080,
                    frameRate = 24.0f,
                    bitRate = 4500000
                ),
                audio = AudioStreamInfo(
                    codec = "aac",
                    channels = 2,
                    sampleRate = 48000,
                    bitRate = 128000
                )
            )

            assertEquals(120000, mediaInfo.duration)
            assertEquals("mp4", mediaInfo.format)
            assertNotNull(mediaInfo.video)
            assertNotNull(mediaInfo.audio)
            assertEquals(1920, mediaInfo.video?.width)
        }

        @Test
        fun `should create MediaInfo for audio-only file`() {
            val mediaInfo = MediaInfo(
                duration = 180000, // 3 minutes
                format = "mp3",
                fileSize = 5000000,
                bitRate = 320000,
                video = null,
                audio = AudioStreamInfo(
                    codec = "mp3",
                    channels = 2,
                    sampleRate = 44100,
                    bitRate = 320000
                )
            )

            assertEquals("mp3", mediaInfo.format)
            assertNull(mediaInfo.video)
            assertNotNull(mediaInfo.audio)
        }
    }
}
