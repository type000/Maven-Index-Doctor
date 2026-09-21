package io.github.mavenindexdoctor.application;

import io.github.mavenindexdoctor.domain.ArtifactCoordinate;
import io.github.mavenindexdoctor.domain.DiagnosticIssue;
import io.github.mavenindexdoctor.domain.DiagnosticIssueType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MavenFileScannerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void findsDownloadMarkersAndLocalArtifacts() throws Exception {
        Path repository = temporaryFolder.newFolder("repository").toPath();
        Path versionDirectory = repository.resolve("com/acme/demo/1.0.0");
        Files.createDirectories(versionDirectory);
        Files.writeString(versionDirectory.resolve("demo-1.0.0.jar"), "artifact");
        Path marker = versionDirectory.resolve("demo-1.0.0.pom.lastUpdated");
        Files.writeString(marker, "https\\://repo.example/.error=Connection refused");

        MavenFileScanner scanner = new MavenFileScanner();
        List<DiagnosticIssue> issues = scanner.scanLastUpdatedFiles(repository);
        List<ArtifactCoordinate> artifacts = scanner.findLocalArtifacts(repository);

        assertEquals(1, issues.size());
        assertEquals(DiagnosticIssueType.LAST_UPDATED, issues.getFirst().type());
        assertTrue(issues.getFirst().details().contains("Connection refused"));
        assertEquals(List.of(new ArtifactCoordinate("com.acme", "demo", "1.0.0")), artifacts);
    }

    @Test
    public void reportsEmptyIndexDirectoryAndZeroByteFiles() throws Exception {
        Path indexDirectory = temporaryFolder.newFolder("indices").toPath();
        MavenFileScanner scanner = new MavenFileScanner();

        List<DiagnosticIssue> emptyDirectoryIssues = scanner.scanIndexDirectory(indexDirectory);
        assertEquals(DiagnosticIssueType.INDEX_DIRECTORY_EMPTY, emptyDirectoryIssues.getFirst().type());

        Files.createFile(indexDirectory.resolve("broken-index"));
        List<DiagnosticIssue> zeroByteIssues = scanner.scanIndexDirectory(indexDirectory);
        assertTrue(zeroByteIssues.stream().anyMatch(
                issue -> issue.type() == DiagnosticIssueType.EMPTY_INDEX_FILE
        ));
    }
}
