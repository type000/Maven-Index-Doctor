package io.github.mavenindexdoctor.domain

import java.time.Instant
import java.nio.file.Path

data class MavenDiagnosticReport(
    val scannedAt: Instant,
    val repositoryPath: Path,
    val indexPath: Path,
    val issues: List<DiagnosticIssue>,
)
