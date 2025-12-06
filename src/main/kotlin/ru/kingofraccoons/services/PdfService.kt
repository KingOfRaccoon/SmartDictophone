package ru.kingofraccoons.services

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import mu.KotlinLogging
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import ru.kingofraccoons.models.TranscriptionSegment
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

class PdfService {

    private data class PdfFonts(val regular: PDFont, val bold: PDFont)

    private val regularFontBytes = loadFontBytes(
        System.getenv("PDF_FONT_REGULAR") ?: "fonts/NotoSans-Regular.ttf"
    )
    private val boldFontBytes = loadFontBytes(
        System.getenv("PDF_FONT_BOLD") ?: "fonts/NotoSans-Bold.ttf"
    )

    init {
        if (regularFontBytes == null) {
            logger.warn { "Regular PDF font not found; falling back to Standard 14 font (Latin only)." }
        }
        if (boldFontBytes == null) {
            logger.warn { "Bold PDF font not found; falling back to regular font or Standard 14 font." }
        }
    }

    fun generateTranscriptionPdf(
        recordTitle: String,
        recordDatetime: String,
        segments: List<TranscriptionSegment>
    ): ByteArray {
        return PDDocument().use { document ->
            val fonts = loadFonts(document)
            var page = PDPage(PDRectangle.A4)
            document.addPage(page)

            var contentStream = PDPageContentStream(document, page)

            var yPosition = 750f
            val margin = 50f
            val pageWidth = page.mediaBox.width - 2 * margin
            val lineHeight = 14f
            val textFontSize = 10f

            // Title section
            contentStream.beginText()
            contentStream.setFont(fonts.bold, 16f)
            contentStream.newLineAtOffset(margin, yPosition)
            contentStream.showText(recordTitle)
            contentStream.endText()
            yPosition -= 30f

            // Metadata block
            contentStream.beginText()
            contentStream.setFont(fonts.regular, 12f)
            contentStream.newLineAtOffset(margin, yPosition)
            contentStream.showText("Дата: ${formatDateRu(recordDatetime)}")
            contentStream.endText()
            yPosition -= 40f

            // Render each transcription segment with timestamp and wrapped text
            for (segment in segments) {
                if (yPosition < 100f) {
                    contentStream.close()
                    page = PDPage(PDRectangle.A4)
                    document.addPage(page)
                    contentStream = PDPageContentStream(document, page)
                    yPosition = 750f
                }

                contentStream.beginText()
                contentStream.setFont(fonts.bold, textFontSize)
                contentStream.newLineAtOffset(margin, yPosition)
                contentStream.showText("[${formatTimestamp(segment.start)}]")
                contentStream.endText()
                yPosition -= lineHeight + 2

                val wrappedLines = wrapText(segment.text, fonts.regular, textFontSize, pageWidth - 50)
                for (line in wrappedLines) {
                    if (yPosition < 100f) {
                        contentStream.close()
                        page = PDPage(PDRectangle.A4)
                        document.addPage(page)
                        contentStream = PDPageContentStream(document, page)
                        yPosition = 750f
                    }

                    contentStream.beginText()
                    contentStream.setFont(fonts.regular, textFontSize)
                    contentStream.newLineAtOffset(margin + 10, yPosition)
                    contentStream.showText(line)
                    contentStream.endText()
                    yPosition -= lineHeight
                }

                yPosition -= 10f // extra space between segments
            }

            contentStream.close()

            ByteArrayOutputStream().also { outputStream ->
                document.save(outputStream)
            }.toByteArray()
        }
    }
    
    private fun formatTimestamp(seconds: Float): String {
        val totalSeconds = seconds.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    private fun formatDateRu(raw: String): String {
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

        val parsed = sequenceOf<(String) -> LocalDateTime>(
            { OffsetDateTime.parse(it).toLocalDateTime() },
            { LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME) }
        ).firstNotNullOfOrNull { parser ->
            runCatching { parser(raw) }.getOrNull()
        }

        return parsed?.format(formatter) ?: raw
    }

    /**
     * Splits plain text into lines that fit the available width of the PDF page.
     */
    private fun wrapText(text: String, font: PDFont, fontSize: Float, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()

        val hasWhitespace = text.any { it.isWhitespace() }
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        val words = if (hasWhitespace) {
            text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        } else {
            listOf(text)
        }

        for (word in words) {
            val separator = if (currentLine.isEmpty()) "" else " "
            val candidate = currentLine.toString() + separator + word
            val candidateWidth = measureWidth(candidate, font, fontSize)

            if (candidateWidth <= maxWidth) {
                currentLine.append(separator).append(word)
                continue
            }

            if (currentLine.isNotEmpty()) {
                lines += currentLine.toString()
                currentLine = StringBuilder()
            }

            val brokenWord = breakLongWord(word, font, fontSize, maxWidth)
            if (brokenWord.isNotEmpty()) {
                lines += brokenWord.dropLast(1)
                currentLine = StringBuilder(brokenWord.last())
            }
        }

        if (currentLine.isNotEmpty()) {
            lines += currentLine.toString()
        }

        return lines
    }

    private fun breakLongWord(
        word: String,
        font: PDFont,
        fontSize: Float,
        maxWidth: Float
    ): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (char in word) {
            val candidate = current.toString() + char
            if (measureWidth(candidate, font, fontSize) <= maxWidth) {
                current.append(char)
            } else {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                }
                current = StringBuilder(char.toString())
            }
        }

        if (current.isNotEmpty()) {
            lines += current.toString()
        }

        return lines
    }

    private fun measureWidth(text: String, font: PDFont, fontSize: Float): Float =
        font.getStringWidth(text) / 1000 * fontSize

    private fun loadFonts(document: PDDocument): PdfFonts {
        val regular = regularFontBytes?.let {
            PDType0Font.load(document, ByteArrayInputStream(it), true)
        } ?: PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN)

        val bold = boldFontBytes?.let {
            PDType0Font.load(document, ByteArrayInputStream(it), true)
        } ?: when {
            regularFontBytes != null -> regular // keep glyph coverage even if weight is not bold
            else -> PDType1Font(Standard14Fonts.FontName.TIMES_BOLD)
        }

        return PdfFonts(regular, bold)
    }

    private fun loadFontBytes(path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null

        // Try filesystem path first (env override), then classpath resource
        val file = File(path)
        if (file.exists() && file.isFile) {
            return runCatching { file.readBytes() }
                .onFailure { logger.warn(it) { "Failed to read font from $path" } }
                .getOrNull()
        }

        val resourceStream = javaClass.classLoader.getResourceAsStream(path) ?: return null
        return resourceStream.use { it.readBytes() }
    }
}
