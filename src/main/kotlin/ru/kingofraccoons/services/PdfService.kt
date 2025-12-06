package ru.kingofraccoons.services

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import ru.kingofraccoons.models.TranscriptionSegment
import java.io.ByteArrayOutputStream

class PdfService {
    fun generateTranscriptionPdf(
        recordTitle: String,
        recordDatetime: String,
        segments: List<TranscriptionSegment>
    ): ByteArray {
        return PDDocument().use { document ->
            var page = PDPage(PDRectangle.A4)
            document.addPage(page)

            var contentStream = PDPageContentStream(document, page)
            val font = PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN)
            val fontBold = PDType1Font(Standard14Fonts.FontName.TIMES_BOLD)

            var yPosition = 750f
            val margin = 50f
            val pageWidth = page.mediaBox.width - 2 * margin
            val lineHeight = 14f

            // Title section
            contentStream.beginText()
            contentStream.setFont(fontBold, 16f)
            contentStream.newLineAtOffset(margin, yPosition)
            contentStream.showText(recordTitle)
            contentStream.endText()
            yPosition -= 30f

            // Metadata block
            contentStream.beginText()
            contentStream.setFont(font, 12f)
            contentStream.newLineAtOffset(margin, yPosition)
            contentStream.showText("Date: $recordDatetime")
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
                contentStream.setFont(fontBold, 10f)
                contentStream.newLineAtOffset(margin, yPosition)
                contentStream.showText("[${formatTimestamp(segment.start)}]")
                contentStream.endText()
                yPosition -= lineHeight + 2

                val wrappedLines = wrapText(segment.text, font, pageWidth - 50)
                for (line in wrappedLines) {
                    if (yPosition < 100f) {
                        contentStream.close()
                        page = PDPage(PDRectangle.A4)
                        document.addPage(page)
                        contentStream = PDPageContentStream(document, page)
                        yPosition = 750f
                    }

                    contentStream.beginText()
                    contentStream.setFont(font, 10f)
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

    /**
     * Splits plain text into lines that fit the available width of the PDF page.
     */
    private fun wrapText(text: String, font: PDType1Font, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        text.split(" ").forEach { word ->
            val candidate = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val candidateWidth = font.getStringWidth(candidate) / 1000 * 10f

            if (candidateWidth > maxWidth && currentLine.isNotEmpty()) {
                lines += currentLine.toString()
                currentLine = StringBuilder(word)
            } else {
                if (currentLine.isNotEmpty()) currentLine.append(' ')
                currentLine.append(word)
            }
        }

        if (currentLine.isNotEmpty()) {
            lines += currentLine.toString()
        }

        return lines
    }
}
