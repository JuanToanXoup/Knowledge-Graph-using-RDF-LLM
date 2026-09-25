package io.github.juantoanxoup.kg

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentProcessorTest {
    private val processor = DocumentProcessor()

    @Test
    fun `txt files are read as UTF-8`(
        @TempDir dir: Path,
    ) {
        val path = dir.resolve("doc.txt")
        Files.writeString(path, "Zürich é")
        assertEquals("Zürich é", processor.loadDocument(path))
    }

    @Test
    fun `docx paragraphs are joined with newlines`(
        @TempDir dir: Path,
    ) {
        val path = dir.resolve("doc.docx")
        XWPFDocument().use { doc ->
            doc.createParagraph().createRun().setText("First paragraph")
            doc.createParagraph().createRun().setText("Second paragraph")
            Files.newOutputStream(path).use { doc.write(it) }
        }
        assertEquals("First paragraph\nSecond paragraph", processor.loadDocument(path))
    }

    @Test
    fun `pdf pages are extracted`(
        @TempDir dir: Path,
    ) {
        val path = dir.resolve("doc.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.newLineAtOffset(50f, 700f)
                content.showText("Hello PDF")
                content.endText()
            }
            doc.save(path.toFile())
        }
        assertContains(processor.loadDocument(path), "Hello PDF")
    }

    @Test
    fun `unsupported and missing files are rejected`(
        @TempDir dir: Path,
    ) {
        val csv = dir.resolve("data.csv")
        Files.writeString(csv, "a,b")
        assertFailsWith<IllegalArgumentException> { processor.loadDocument(csv) }
        assertFailsWith<NoSuchFileException> { processor.loadDocument(dir.resolve("missing.txt")) }
    }

    @Test
    fun `sample document mentions the three scientists`() {
        val sample = processor.createSampleDocument()
        assertContains(sample, "Albert Einstein")
        assertContains(sample, "Marie Curie")
        assertContains(sample, "Isaac Newton")
    }
}
