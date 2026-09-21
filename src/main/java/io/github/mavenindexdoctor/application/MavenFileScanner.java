package io.github.mavenindexdoctor.application;

import io.github.mavenindexdoctor.domain.ArtifactCoordinate;
import io.github.mavenindexdoctor.domain.DiagnosticIssue;
import io.github.mavenindexdoctor.domain.DiagnosticIssueType;
import io.github.mavenindexdoctor.domain.DiagnosticSeverity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class MavenFileScanner {

    public static final String LAST_UPDATED_SUFFIX = ".lastUpdated";

    public List<Path> findLastUpdatedFiles(Path repositoryPath) throws IOException {
        if (Files.notExists(repositoryPath)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(repositoryPath)) {
            return paths
                    .filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(LAST_UPDATED_SUFFIX))
                    .sorted()
                    .toList();
        }
    }

    public List<DiagnosticIssue> scanLastUpdatedFiles(Path repositoryPath) throws IOException {
        List<DiagnosticIssue> issues = new ArrayList<>();
        for (Path marker : findLastUpdatedFiles(repositoryPath)) {
            issues.add(new DiagnosticIssue(
                    DiagnosticIssueType.LAST_UPDATED,
                    DiagnosticSeverity.WARNING,
                    "Failed Maven download marker",
                    readLastUpdatedDetails(marker),
                    marker,
                    "Delete the marker and reimport Maven projects."
            ));
        }
        return List.copyOf(issues);
    }

    public List<DiagnosticIssue> scanIndexDirectory(Path indexPath) throws IOException {
        if (Files.notExists(indexPath)) {
            return List.of(new DiagnosticIssue(
                    DiagnosticIssueType.INDEX_DIRECTORY_MISSING,
                    DiagnosticSeverity.WARNING,
                    "Maven index directory not found",
                    "IDEA has not created its Maven index directory or it was removed.",
                    indexPath,
                    "Rebuild Maven indexes."
            ));
        }

        List<DiagnosticIssue> issues = new ArrayList<>();
        if (!Files.isReadable(indexPath)) {
            issues.add(new DiagnosticIssue(
                    DiagnosticIssueType.INDEX_DIRECTORY_UNREADABLE,
                    DiagnosticSeverity.ERROR,
                    "Maven index directory is not readable",
                    "IDEA cannot read its Maven index directory.",
                    indexPath,
                    "Correct the directory permissions before rebuilding."
            ));
            return List.copyOf(issues);
        }
        if (!Files.isWritable(indexPath)) {
            issues.add(new DiagnosticIssue(
                    DiagnosticIssueType.INDEX_DIRECTORY_UNWRITABLE,
                    DiagnosticSeverity.ERROR,
                    "Maven index directory is not writable",
                    "IDEA cannot update files in its Maven index directory.",
                    indexPath,
                    "Correct the directory permissions before rebuilding."
            ));
        }

        boolean regularFileFound = false;
        try (Stream<Path> paths = Files.walk(indexPath)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                regularFileFound = true;
                if (Files.size(path) == 0L) {
                    issues.add(new DiagnosticIssue(
                            DiagnosticIssueType.EMPTY_INDEX_FILE,
                            DiagnosticSeverity.ERROR,
                            "Empty Maven index file",
                            "A zero-byte index file indicates an incomplete write or corrupted index.",
                            path,
                            "Run Safe Reset Maven Indexes."
                    ));
                }
            }
        }

        if (!regularFileFound) {
            issues.add(new DiagnosticIssue(
                    DiagnosticIssueType.INDEX_DIRECTORY_EMPTY,
                    DiagnosticSeverity.WARNING,
                    "Maven index directory is empty",
                    "The directory exists but contains no index files.",
                    indexPath,
                    "Rebuild Maven indexes."
            ));
        }
        return List.copyOf(issues);
    }

    public List<ArtifactCoordinate> findLocalArtifacts(Path repositoryPath) throws IOException {
        if (Files.notExists(repositoryPath)) {
            return List.of();
        }

        Set<ArtifactCoordinate> artifacts = new HashSet<>();
        try (Stream<Path> paths = Files.walk(repositoryPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isArtifactFile)
                    .forEach(path -> addArtifactCoordinate(repositoryPath, path, artifacts));
        }
        return artifacts.stream()
                .sorted(Comparator.comparing(ArtifactCoordinate::groupId)
                        .thenComparing(ArtifactCoordinate::artifactId)
                        .thenComparing(ArtifactCoordinate::version))
                .toList();
    }

    private String readLastUpdatedDetails(Path marker) throws IOException {
        List<String> repositoryErrors = Files.readAllLines(marker, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> line.contains(".error=") && !line.endsWith(".error="))
                .toList();
        if (repositoryErrors.isEmpty()) {
            return "Maven recorded a failed download and may skip this artifact until its update interval expires.";
        }
        return "Maven recorded a failed download: " + String.join("; ", repositoryErrors);
    }

    private boolean isArtifactFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".pom") || fileName.endsWith(".jar");
    }

    private void addArtifactCoordinate(
            Path repositoryPath,
            Path artifactFile,
            Set<ArtifactCoordinate> artifacts
    ) {
        Path versionDirectory = artifactFile.getParent();
        Path artifactDirectory = versionDirectory.getParent();
        Path relativeArtifactDirectory = repositoryPath.relativize(artifactDirectory);
        if (relativeArtifactDirectory.getNameCount() < 2) {
            return;
        }

        String version = versionDirectory.getFileName().toString();
        String artifactId = artifactDirectory.getFileName().toString();
        StringBuilder groupId = new StringBuilder();
        for (int index = 0; index < relativeArtifactDirectory.getNameCount() - 1; index++) {
            if (!groupId.isEmpty()) {
                groupId.append('.');
            }
            groupId.append(relativeArtifactDirectory.getName(index));
        }
        artifacts.add(new ArtifactCoordinate(groupId.toString(), artifactId, version));
    }
}
