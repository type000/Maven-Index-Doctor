package io.github.mavenindexdoctor.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class MavenIndexFileOperations {

    public Path backupAndClear(Path indexPath, Path backupRoot) throws IOException {
        Path normalizedIndexPath = requireAbsolutePath(indexPath, "indexPath");
        Path normalizedBackupRoot = requireAbsolutePath(backupRoot, "backupRoot");
        Files.createDirectories(normalizedBackupRoot);

        Path backupPath = normalizedBackupRoot.resolve("backup-" + UUID.randomUUID());
        if (Files.exists(normalizedIndexPath)) {
            copyDirectory(normalizedIndexPath, backupPath);
            deleteContents(normalizedIndexPath);
        } else {
            Files.createDirectories(backupPath);
        }
        Files.createDirectories(normalizedIndexPath);
        return backupPath;
    }

    public void restore(Path indexPath, Path backupRoot, Path backupPath) throws IOException {
        Path normalizedIndexPath = requireAbsolutePath(indexPath, "indexPath");
        Path normalizedBackupRoot = requireAbsolutePath(backupRoot, "backupRoot");
        Path normalizedBackupPath = requireAbsolutePath(backupPath, "backupPath");
        if (!normalizedBackupPath.startsWith(normalizedBackupRoot)) {
            throw new IllegalArgumentException("Backup path is outside the Maven Index Doctor backup directory");
        }
        if (!Files.isDirectory(normalizedBackupPath)) {
            throw new IOException("Maven index backup does not exist: " + normalizedBackupPath);
        }

        Files.createDirectories(normalizedIndexPath);
        deleteContents(normalizedIndexPath);
        copyDirectory(normalizedBackupPath, normalizedIndexPath);
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relativePath = source.relativize(path);
                Path targetPath = target.resolve(relativePath);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(
                            path,
                            targetPath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES
                    );
                }
            }
        }
    }

    private void deleteContents(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(root)) {
            paths = stream
                    .filter(path -> !path.equals(root))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }
        for (Path path : paths) {
            if (!path.normalize().startsWith(root)) {
                throw new IOException("Refusing to delete a path outside the index directory: " + path);
            }
            Files.delete(path);
        }
    }

    private Path requireAbsolutePath(Path path, String parameterName) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(parameterName + " must be absolute");
        }
        return path.normalize();
    }
}
