package io.github.mavenindexdoctor.monitor;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import io.github.mavenindexdoctor.application.MavenDiagnosticsService;
import io.github.mavenindexdoctor.application.MavenFileScanner;
import io.github.mavenindexdoctor.application.MavenRepairService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MavenHealthMonitor {

    private static final Logger LOG = Logger.getInstance(MavenHealthMonitor.class);
    private static final String NOTIFICATION_GROUP = "Maven Index Doctor";
    private static final String INDEX_DIRECTORY = "Indices";

    private MavenHealthMonitor() {
    }

    public static void start(Project project) {
        Path repositoryPath = MavenProjectsManager.getInstance(project)
                .getRepositoryPath()
                .toAbsolutePath()
                .normalize();
        Path indexPath = MavenUtil.getPluginSystemDir(INDEX_DIRECTORY).toAbsolutePath().normalize();
        Path settingsPath = new MavenDiagnosticsService(project).resolveSettingsPath();

        project.getMessageBus().connect(project).subscribe(
                VirtualFileManager.VFS_CHANGES,
                new MavenFileChangeListener(project, repositoryPath, indexPath, settingsPath)
        );
    }

    private static final class MavenFileChangeListener implements BulkFileListener {

        private final Project project;
        private final Path repositoryPath;
        private final Path indexPath;
        private final Path settingsPath;
        private long lastIndexSize;

        private MavenFileChangeListener(
                Project project,
                Path repositoryPath,
                Path indexPath,
                Path settingsPath
        ) {
            this.project = project;
            this.repositoryPath = repositoryPath;
            this.indexPath = indexPath;
            this.settingsPath = settingsPath;
            this.lastIndexSize = directorySize(indexPath);
        }

        @Override
        public void after(@NotNull List<? extends VFileEvent> events) {
            Set<Path> failedDownloads = new HashSet<>();
            boolean settingsChanged = false;
            boolean indexDirectoryRemoved = false;
            boolean indexChanged = false;
            Set<Path> emptyIndexFiles = new HashSet<>();

            for (VFileEvent event : events) {
                Path changedPath = toPath(event.getPath());
                if (changedPath == null) {
                    continue;
                }
                if (changedPath.startsWith(repositoryPath)
                        && changedPath.getFileName().toString().endsWith(MavenFileScanner.LAST_UPDATED_SUFFIX)
                        && Files.isRegularFile(changedPath)) {
                    failedDownloads.add(changedPath);
                }
                if (changedPath.equals(settingsPath)) {
                    settingsChanged = true;
                }
                if (changedPath.equals(indexPath) && Files.notExists(indexPath)) {
                    indexDirectoryRemoved = true;
                } else if (changedPath.startsWith(indexPath) && isEmptyRegularFile(changedPath)) {
                    emptyIndexFiles.add(changedPath);
                }
                if (changedPath.startsWith(indexPath)) {
                    indexChanged = true;
                }
            }

            for (Path marker : failedDownloads) {
                notifyFailedDownload(marker);
            }
            if (settingsChanged) {
                notifySettingsChanged();
            }
            if (indexDirectoryRemoved) {
                notifyIndexProblem("IDEA Maven index directory was removed", indexPath);
            }
            for (Path emptyIndexFile : emptyIndexFiles) {
                notifyIndexProblem("IDEA wrote an empty Maven index file", emptyIndexFile);
            }
            if (indexChanged && !indexDirectoryRemoved) {
                if (!Files.isWritable(indexPath)) {
                    notifyIndexProblem("IDEA Maven index directory is not writable", indexPath);
                }
                detectIndexSizeDecrease();
            }
        }

        private void notifyFailedDownload(Path marker) {
            Notification notification = notification(
                    "Maven dependency download failed",
                    "Maven created " + marker.getFileName() + " in " + marker.getParent() + '.',
                    NotificationType.WARNING
            );
            notification.addAction(NotificationAction.createSimpleExpiring(
                    "Retry download",
                    () -> ApplicationManager.getApplication().executeOnPooledThread(() -> retryDownload(marker))
            ));
            notification.notify(project);
        }

        private void retryDownload(Path marker) {
            try {
                new MavenRepairService(project).retryDownloads(List.of(marker));
                notification(
                        "Maven download retry requested",
                        "The failure marker was removed and Maven project refresh was requested.",
                        NotificationType.INFORMATION
                ).notify(project);
            } catch (IOException | IllegalArgumentException exception) {
                LOG.warn("Unable to retry Maven download for " + marker, exception);
                notification(
                        "Maven download retry failed",
                        errorMessage(exception),
                        NotificationType.ERROR
                ).notify(project);
            }
        }

        private void notifySettingsChanged() {
            Notification notification = notification(
                    "Maven settings.xml changed",
                    "Mirror or proxy changes can make the current IDEA Maven index stale.",
                    NotificationType.WARNING
            );
            notification.addAction(NotificationAction.createSimpleExpiring(
                    "Refresh indexes",
                    () -> ApplicationManager.getApplication().executeOnPooledThread(
                            () -> new MavenRepairService(project).rebuildAllIndexes()
                    )
            ));
            notification.notify(project);
        }

        private void notifyIndexProblem(String title, Path path) {
            notifyIndexProblem(title, path.toString());
        }

        private void notifyIndexProblem(String title, String details) {
            Notification notification = notification(title, details, NotificationType.WARNING);
            notification.addAction(NotificationAction.createSimpleExpiring(
                    "Open Maven Index Doctor",
                    () -> {
                        var toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                                .getToolWindow("MavenIndexDoctor");
                        if (toolWindow != null) {
                            toolWindow.show();
                        }
                    }
            ));
            notification.notify(project);
        }

        private Notification notification(String title, String content, NotificationType type) {
            return NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification(title, content, type);
        }

        private Path toPath(String path) {
            try {
                return Path.of(path).toAbsolutePath().normalize();
            } catch (InvalidPathException exception) {
                LOG.debug("Ignoring a non-local VFS path: " + path, exception);
                return null;
            }
        }

        private boolean isEmptyRegularFile(Path path) {
            try {
                return Files.isRegularFile(path) && Files.size(path) == 0L;
            } catch (IOException exception) {
                LOG.warn("Unable to inspect changed Maven index file " + path, exception);
                return false;
            }
        }

        private void detectIndexSizeDecrease() {
            long currentSize = directorySize(indexPath);
            if (lastIndexSize >= 0L && currentSize >= 0L && currentSize < lastIndexSize) {
                notifyIndexProblem(
                        "IDEA Maven index size decreased",
                        lastIndexSize + " bytes -> " + currentSize + " bytes"
                );
            }
            lastIndexSize = currentSize;
        }

        private long directorySize(Path directory) {
            if (Files.notExists(directory)) {
                return -1L;
            }
            try (var paths = Files.walk(directory)) {
                long totalSize = 0L;
                var iterator = paths.iterator();
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (Files.isRegularFile(path)) {
                        totalSize += Files.size(path);
                    }
                }
                return totalSize;
            } catch (IOException exception) {
                LOG.warn("Unable to calculate Maven index directory size " + directory, exception);
                return -1L;
            }
        }

        private String errorMessage(Exception exception) {
            String message = exception.getMessage();
            return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
        }

    }
}
