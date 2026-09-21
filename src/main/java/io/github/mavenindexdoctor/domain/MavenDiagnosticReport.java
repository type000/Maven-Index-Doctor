package io.github.mavenindexdoctor.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record MavenDiagnosticReport(
        Instant scannedAt,
        Path repositoryPath,
        Path indexPath,
        Path settingsPath,
        int localArtifactCount,
        int missingFromIndexCount,
        List<DiagnosticIssue> issues
) {
}
