package io.github.mavenindexdoctor.application;

import com.intellij.openapi.project.Project;
import io.github.mavenindexdoctor.domain.ArtifactCoordinate;
import io.github.mavenindexdoctor.domain.DiagnosticIssue;
import io.github.mavenindexdoctor.domain.DiagnosticIssueType;
import io.github.mavenindexdoctor.domain.DiagnosticSeverity;
import io.github.mavenindexdoctor.domain.MavenDiagnosticReport;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MavenDiagnosticsService {

    private static final String DEFAULT_SETTINGS_DIRECTORY = ".m2";
    private static final String DEFAULT_SETTINGS_FILE = "settings.xml";
    private static final String INDEX_DIRECTORY = "Indices";

    private final Project project;
    private final MavenFileScanner fileScanner;
    private final MavenSettingsScanner settingsScanner;

    public MavenDiagnosticsService(Project project) {
        this.project = project;
        this.fileScanner = new MavenFileScanner();
        this.settingsScanner = new MavenSettingsScanner();
    }

    public MavenDiagnosticReport scan() throws IOException {
        MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
        Path repositoryPath = projectsManager.getRepositoryPath();
        Path indexPath = MavenUtil.getPluginSystemDir(INDEX_DIRECTORY);
        Path settingsPath = resolveSettingsPath(projectsManager.getGeneralSettings());

        List<DiagnosticIssue> issues = new ArrayList<>();
        issues.addAll(fileScanner.scanLastUpdatedFiles(repositoryPath));
        issues.addAll(fileScanner.scanIndexDirectory(indexPath));
        issues.addAll(settingsScanner.scan(settingsPath));
        inspectIdeaMavenSettings(projectsManager.getGeneralSettings(), settingsPath, issues);

        List<ArtifactCoordinate> artifacts = fileScanner.findLocalArtifacts(repositoryPath);
        int missingFromIndexCount = compareRepositoryWithIndex(artifacts, issues);
        issues.sort(Comparator.comparingInt(issue -> severityRank(issue.severity())));

        return new MavenDiagnosticReport(
                Instant.now(),
                repositoryPath,
                indexPath,
                settingsPath,
                artifacts.size(),
                missingFromIndexCount,
                List.copyOf(issues)
        );
    }

    public Path resolveSettingsPath() {
        return resolveSettingsPath(MavenProjectsManager.getInstance(project).getGeneralSettings());
    }

    private int compareRepositoryWithIndex(
            List<ArtifactCoordinate> artifacts,
            List<DiagnosticIssue> issues
    ) {
        MavenIndicesManager indicesManager = MavenIndicesManager.getInstance(project);
        indicesManager.scheduleUpdateIndicesListAndWait();
        indicesManager.waitForGavUpdateCompleted();

        int missingCount = 0;
        for (ArtifactCoordinate artifact : artifacts) {
            if (indicesManager.hasLocalVersion(
                    artifact.groupId(),
                    artifact.artifactId(),
                    artifact.version()
            )) {
                continue;
            }
            missingCount++;
            issues.add(new DiagnosticIssue(
                    DiagnosticIssueType.ARTIFACT_MISSING_FROM_INDEX,
                    DiagnosticSeverity.ERROR,
                    "Artifact missing from IDEA index",
                    artifact + " exists in the local repository but is absent from IDEA's local Maven index.",
                    null,
                    "Refresh the local repository index."
            ));
        }
        return missingCount;
    }

    private Path resolveSettingsPath(MavenGeneralSettings settings) {
        String configuredSettings = settings.getUserSettingsFile();
        if (configuredSettings != null && !configuredSettings.isBlank()) {
            return Path.of(configuredSettings).toAbsolutePath().normalize();
        }
        return Path.of(
                System.getProperty("user.home"),
                DEFAULT_SETTINGS_DIRECTORY,
                DEFAULT_SETTINGS_FILE
        ).toAbsolutePath().normalize();
    }

    private void inspectIdeaMavenSettings(
            MavenGeneralSettings settings,
            Path settingsPath,
            List<DiagnosticIssue> issues
    ) {
        if (settings.isWorkOffline()) {
            issues.add(new DiagnosticIssue(
                    DiagnosticIssueType.MAVEN_OFFLINE_MODE,
                    DiagnosticSeverity.ERROR,
                    "IDEA Maven offline mode is enabled",
                    "IDEA cannot download dependencies or repository index updates while Maven is offline.",
                    settingsPath,
                    "Disable Work offline in IDEA Maven settings and refresh indexes."
            ));
        }
    }

    private int severityRank(DiagnosticSeverity severity) {
        return switch (severity) {
            case ERROR -> 0;
            case WARNING -> 1;
            case INFO -> 2;
        };
    }
}
