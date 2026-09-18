package io.github.mavenindexdoctor.domain

import java.nio.file.Path

data class DiagnosticIssue(
    val severity: DiagnosticSeverity,
    val title: String,
    val details: String,
    val path: Path?,
)
