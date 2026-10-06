package com.tymed.app.export

import com.tymed.app.data.repository.ExportSnapshot

enum class ExportFormat(val label: String, val mimeType: String, val fileExtension: String) {
    JSON("JSON", "application/json", "json"),
    CSV("CSV", "application/zip", "zip"),
    PDF("PDF", "application/pdf", "pdf"),
}

fun ExportFormat.render(snapshot: ExportSnapshot): ByteArray = when (this) {
    ExportFormat.JSON -> JsonExporter.render(snapshot).toByteArray(Charsets.UTF_8)
    ExportFormat.CSV -> CsvExporter.render(snapshot)
    ExportFormat.PDF -> PdfExporter.render(snapshot)
}
