package io.github.juantoanxoup.kg

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension

/** Loads text from PDF, TXT, DOCX and DOC files (document_processor.py). */
class DocumentProcessor {
    private val log = LoggerFactory.getLogger(DocumentProcessor::class.java)

    /** Detects the file type from the extension and loads the document. */
    fun loadDocument(path: Path): String {
        if (!path.exists()) throw NoSuchFileException(path.toFile(), reason = "File not found: $path")
        return when (path.extension.lowercase()) {
            "txt" -> loadFromTxt(path)
            "pdf" -> loadFromPdf(path)
            "docx", "doc" -> loadFromDocx(path)
            else -> throw IllegalArgumentException("Unsupported file format: .${path.extension}")
        }
    }

    /** Built-in sample used when no input file is supplied. */
    fun createSampleDocument(): String {
        log.info("Sample document created")
        return SAMPLE_DOCUMENT
    }

    fun loadFromTxt(path: Path): String {
        val text = Files.readString(path, StandardCharsets.UTF_8)
        log.info("Loaded document from {}", path)
        return text
    }

    fun loadFromDocx(path: Path): String {
        val text =
            Files.newInputStream(path).use { input ->
                XWPFDocument(input).use { doc -> doc.paragraphs.joinToString("\n") { it.text } }
            }
        log.info("Loaded document from {}", path)
        return text
    }

    /** Page texts are concatenated without a separator, as in the original. */
    fun loadFromPdf(path: Path): String {
        val text =
            Loader.loadPDF(path.toFile()).use { doc ->
                val stripper = PDFTextStripper()
                buildString {
                    for (page in 1..doc.numberOfPages) {
                        stripper.startPage = page
                        stripper.endPage = page
                        append(stripper.getText(doc))
                    }
                }
            }
        log.info("Loaded document from {}", path)
        return text
    }

    companion object {
        val SAMPLE_DOCUMENT =
            """
            Albert Einstein was a theoretical physicist who developed the theory of relativity.
            He was born in Ulm, Germany on March 14, 1879. Einstein worked at the University 
            of Zurich and later at Princeton University. He received the Nobel Prize in Physics 
            in 1921 for his explanation of the photoelectric effect.
            
            Marie Curie was a Polish physicist and chemist who conducted pioneering research 
            on radioactivity. She was the first woman to win a Nobel Prize and remains the only 
            person to win Nobel Prizes in two different sciences. Marie Curie worked at the 
            University of Paris and discovered the elements polonium and radium.
            
            Isaac Newton was an English mathematician, physicist, and astronomer. He formulated 
            the laws of motion and universal gravitation. Newton studied at Cambridge University 
            and later became a professor there. His work laid the foundation for classical mechanics.
            
            The theory of relativity revolutionized our understanding of space, time, and gravity.
            Einstein published his special theory of relativity in 1905 and the general theory 
            in 1915. This work had profound implications for physics and cosmology.
            """.trimIndent()
    }
}
