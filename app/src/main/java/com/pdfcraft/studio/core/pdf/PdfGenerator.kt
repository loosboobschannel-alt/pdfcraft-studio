package com.pdfcraft.studio.core.pdf

/**
 * Contract for turning an in-memory document (pages, text blocks, images,
 * links) into an exported PDF file. No implementation yet — this stage only
 * establishes the seam so the editor UI can be built against a stable
 * interface. A concrete implementation (e.g. backed by PdfDocument /
 * PdfBox-Android) will be added when export is implemented.
 */
interface PdfGenerator {
    // fun export(document: PdfDocumentModel, outputPath: String): Result<Unit>
}
