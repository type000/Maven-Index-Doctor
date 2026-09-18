package io.github.mavenindexdoctor.application

import com.intellij.openapi.application.PathManager
import io.github.mavenindexdoctor.domain.DiagnosticIssue
import io.github.mavenindexdoctor.domain.DiagnosticSeverity
import io.github.mavenindexdoctor.domain.MavenDiagnosticReport
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class MavenDiagnosticsService {
    fun scan(): MavenDiagnosticReport {
        val repositoryPath = Path.of(System.getProperty("user.home"), ".m2", "repository")
        val indexPath = Path.of(PathManager.getSystemPath(), "maven", "indices")
        val issues = buildList {
            addAll(scanLastUpdatedFiles(repositoryPath))
            addAll(scanIndexDirectory(indexPath))
        }

        return MavenDiagnosticReport(Instant.now(), repositoryPath, indexPath, issues)
    }

    private fun scanLastUpdatedFiles(repositoryPath: Path): List<DiagnosticIssue> {
        if (!Files.exists(repositoryPath)) {
            return listOf(
                DiagnosticIssue(
                    DiagnosticSeverity.WARNING,
                    "Local Maven repository not found",
                    "The default local repository does not exist yet.",
                    repositoryPath,
                ),
            )
        }

        Files.walk(repositoryPath).use { paths ->
            return paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".lastUpdated") }
                .map {
                    DiagnosticIssue(
                        DiagnosticSeverity.WARNING,
                        "Failed Maven download marker",
                        "Maven left a .lastUpdated marker; the dependency may be skipped until it is retried.",
                        it,
                    )
                }
                .toList()
        }
    }

    private fun scanIndexDirectory(indexPath: Path): List<DiagnosticIssue> {
        if (!Files.exists(indexPath)) {
            return listOf(
                DiagnosticIssue(
                    DiagnosticSeverity.INFO,
                    "Maven index directory not found",
                    "The directory will be created when IDEA downloads its first Maven index.",
                    indexPath,
                ),
            )
        }
        if (!Files.isReadable(indexPath)) {
            return listOf(
                DiagnosticIssue(
                    DiagnosticSeverity.ERROR,
                    "Maven index directory is not readable",
                    "IDEA cannot inspect the Maven index directory.",
                    indexPath,
                ),
            )
        }

        Files.walk(indexPath).use { paths ->
            return paths
                .filter { Files.isRegularFile(it) && Files.size(it) == 0L }
                .map {
                    DiagnosticIssue(
                        DiagnosticSeverity.ERROR,
                        "Empty Maven index file",
                        "A zero-byte index file may indicate an interrupted or corrupted download.",
                        it,
                    )
                }
                .toList()
        }
    }
}
