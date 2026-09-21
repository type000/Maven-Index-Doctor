package io.github.mavenindexdoctor.application;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import io.github.mavenindexdoctor.domain.RepairResult;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public final class MavenRepairService {

    private static final String INDEX_DIRECTORY = "Indices";
    private static final String BACKUP_DIRECTORY = "MavenIndexDoctorBackups";
    private static final String LATEST_BACKUP_PROPERTY = "maven.index.doctor.latest.backup";

    private final Project project;
    private final MavenFileScanner fileScanner;
    private final MavenIndexFileOperations indexFileOperations;

    public MavenRepairService(Project project) {
        this.project = project;
        this.fileScanner = new MavenFileScanner();
        this.indexFileOperations = new MavenIndexFileOperations();
    }

    public RepairResult retryDownloads(Collection<Path> markers) throws IOException {
        int deletedCount = deleteLastUpdatedFiles(markers);
        MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles();
        return new RepairResult(
                "Deleted " + deletedCount + " marker(s) and requested Maven project refresh.",
                deletedCount,
                null
        );
    }

    public RepairResult deleteAllLastUpdatedFiles() throws IOException {
        Path repositoryPath = repositoryPath();
        List<Path> markers = fileScanner.findLastUpdatedFiles(repositoryPath);
        int deletedCount = deleteLastUpdatedFiles(markers);
        return new RepairResult("Deleted " + deletedCount + " .lastUpdated marker(s).", deletedCount, null);
    }

    public RepairResult refreshLocalRepositoryIndex() {
        MavenIndicesManager.getInstance(project).scheduleUpdateLocalGavContent(true);
        return new RepairResult("Local Maven repository index refresh was scheduled.", 0, null);
    }

    public RepairResult rebuildAllIndexes() {
        MavenIndicesManager indicesManager = MavenIndicesManager.getInstance(project);
        indicesManager.scheduleUpdateIndicesListAndWait();
        indicesManager.scheduleUpdateContentAll(true);
        indicesManager.scheduleUpdateLocalGavContent(true);
        return new RepairResult("Maven index rebuild was scheduled.", 0, null);
    }

    public RepairResult safeResetIndexes() throws IOException {
        Path indexPath = indexPath();
        validateIndexPath(indexPath);
        Path backupPath = indexFileOperations.backupAndClear(indexPath, backupRoot());
        PropertiesComponent.getInstance(project).setValue(LATEST_BACKUP_PROPERTY, backupPath.toString());
        rebuildAllIndexes();
        return new RepairResult("Maven indexes were backed up, cleared, and scheduled for rebuild.", 0, backupPath);
    }

    public RepairResult restoreLatestBackup() throws IOException {
        String storedBackupPath = PropertiesComponent.getInstance(project).getValue(LATEST_BACKUP_PROPERTY);
        if (storedBackupPath == null || storedBackupPath.isBlank()) {
            throw new IOException("No Maven index backup has been recorded for this project");
        }

        Path indexPath = indexPath();
        validateIndexPath(indexPath);
        Path backupPath = Path.of(storedBackupPath).toAbsolutePath().normalize();
        indexFileOperations.restore(indexPath, backupRoot(), backupPath);
        rebuildAllIndexes();
        return new RepairResult("The Maven index backup was restored and index refresh was scheduled.", 0, backupPath);
    }

    @Nullable
    public Path latestBackupPath() {
        String storedBackupPath = PropertiesComponent.getInstance(project).getValue(LATEST_BACKUP_PROPERTY);
        if (storedBackupPath == null || storedBackupPath.isBlank()) {
            return null;
        }
        return Path.of(storedBackupPath).toAbsolutePath().normalize();
    }

    private int deleteLastUpdatedFiles(Collection<Path> markers) throws IOException {
        Path repositoryPath = repositoryPath();
        int deletedCount = 0;
        for (Path marker : markers) {
            Path normalizedMarker = marker.toAbsolutePath().normalize();
            if (!normalizedMarker.startsWith(repositoryPath)
                    || !normalizedMarker.getFileName().toString().endsWith(MavenFileScanner.LAST_UPDATED_SUFFIX)) {
                throw new IllegalArgumentException("Refusing to delete a non-Maven marker path: " + marker);
            }
            if (Files.deleteIfExists(normalizedMarker)) {
                deletedCount++;
            }
        }
        return deletedCount;
    }

    private Path repositoryPath() {
        return MavenProjectsManager.getInstance(project).getRepositoryPath().toAbsolutePath().normalize();
    }

    private Path indexPath() {
        return MavenUtil.getPluginSystemDir(INDEX_DIRECTORY).toAbsolutePath().normalize();
    }

    private Path backupRoot() {
        return MavenUtil.getPluginSystemDir(BACKUP_DIRECTORY).toAbsolutePath().normalize();
    }

    private void validateIndexPath(Path indexPath) {
        Path expectedPath = MavenUtil.getPluginSystemDir(INDEX_DIRECTORY).toAbsolutePath().normalize();
        if (!indexPath.equals(expectedPath)) {
            throw new IllegalArgumentException("Refusing to modify an unexpected Maven index path: " + indexPath);
        }
    }
}
