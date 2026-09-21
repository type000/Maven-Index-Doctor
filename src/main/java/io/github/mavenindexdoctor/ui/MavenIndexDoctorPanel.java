package io.github.mavenindexdoctor.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import io.github.mavenindexdoctor.application.MavenDiagnosticsService;
import io.github.mavenindexdoctor.application.MavenRepairService;
import io.github.mavenindexdoctor.domain.DiagnosticIssue;
import io.github.mavenindexdoctor.domain.DiagnosticIssueType;
import io.github.mavenindexdoctor.domain.MavenDiagnosticReport;
import io.github.mavenindexdoctor.domain.RepairResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class MavenIndexDoctorPanel extends JBPanel<MavenIndexDoctorPanel> {

    private static final Logger LOG = Logger.getInstance(MavenIndexDoctorPanel.class);

    private final Project project;
    private final MavenDiagnosticsService diagnosticsService;
    private final MavenRepairService repairService;
    private final DiagnosticIssueTableModel tableModel;
    private final JBTable issueTable;
    private final JBLabel statusLabel;
    private final JBLabel summaryLabel;
    private final JBLabel backupLabel;
    private final List<JButton> actionButtons;

    MavenIndexDoctorPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.diagnosticsService = new MavenDiagnosticsService(project);
        this.repairService = new MavenRepairService(project);
        this.tableModel = new DiagnosticIssueTableModel();
        this.issueTable = new JBTable(tableModel);
        this.statusLabel = new JBLabel("Ready to scan");
        this.summaryLabel = new JBLabel("No diagnostic report yet");
        this.backupLabel = new JBLabel();
        this.actionButtons = new ArrayList<>();
        initializeUi();
        updateBackupLabel();
    }

    private void initializeUi() {
        issueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        issueTable.setAutoCreateRowSorter(true);
        issueTable.setFillsViewportHeight(true);

        JPanel scanToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        scanToolbar.add(createButton("Scan", this::runScan));
        scanToolbar.add(statusLabel);

        JPanel repairToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        repairToolbar.add(createButton("Retry selected download", this::retrySelectedDownload));
        repairToolbar.add(createButton("Delete all .lastUpdated", this::deleteAllMarkers));
        repairToolbar.add(createButton("Refresh local index", this::refreshLocalIndex));
        repairToolbar.add(createButton("Rebuild all indexes", this::rebuildAllIndexes));
        repairToolbar.add(createButton("Safe reset", this::safeReset));
        repairToolbar.add(createButton("Restore backup", this::restoreBackup));

        JPanel header = new JPanel(new BorderLayout());
        header.add(scanToolbar, BorderLayout.NORTH);
        header.add(repairToolbar, BorderLayout.CENTER);
        JPanel reportSummary = new JPanel(new FlowLayout(FlowLayout.LEFT));
        reportSummary.add(summaryLabel);
        reportSummary.add(backupLabel);
        header.add(reportSummary, BorderLayout.SOUTH);

        JBScrollPane scrollPane = new JBScrollPane(issueTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(header, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JButton createButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        actionButtons.add(button);
        return button;
    }

    private void runScan() {
        setBusy("Scanning Maven repository and indexes...");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                MavenDiagnosticReport report = diagnosticsService.scan();
                invokeLater(() -> renderReport(report));
            } catch (Exception exception) {
                reportFailure("Maven diagnostic scan failed", exception);
            }
        });
    }

    private void retrySelectedDownload() {
        DiagnosticIssue selectedIssue = selectedIssue();
        if (selectedIssue == null
                || selectedIssue.type() != DiagnosticIssueType.LAST_UPDATED
                || selectedIssue.path() == null) {
            Messages.showInfoMessage(
                    project,
                    "Select a .lastUpdated diagnostic row first.",
                    "Maven Index Doctor"
            );
            return;
        }
        runRepair("Retrying Maven download...", () -> repairService.retryDownloads(List.of(selectedIssue.path())));
    }

    private void deleteAllMarkers() {
        if (!confirmed("Delete every .lastUpdated file in the configured local Maven repository?")) {
            return;
        }
        runRepair("Deleting .lastUpdated markers...", repairService::deleteAllLastUpdatedFiles);
    }

    private void refreshLocalIndex() {
        runRepair("Scheduling local repository index refresh...", repairService::refreshLocalRepositoryIndex);
    }

    private void rebuildAllIndexes() {
        runRepair("Scheduling Maven index rebuild...", repairService::rebuildAllIndexes);
    }

    private void safeReset() {
        if (!confirmed("Back up and clear IDEA Maven indexes, then schedule a rebuild?")) {
            return;
        }
        runRepair("Backing up and resetting Maven indexes...", repairService::safeResetIndexes);
    }

    private void restoreBackup() {
        if (!confirmed("Replace current IDEA Maven indexes with the latest Maven Index Doctor backup?")) {
            return;
        }
        runRepair("Restoring Maven index backup...", repairService::restoreLatestBackup);
    }

    private void runRepair(String runningMessage, RepairOperation operation) {
        setBusy(runningMessage);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                RepairResult result = operation.execute();
                invokeLater(() -> {
                    statusLabel.setText(result.message());
                    updateBackupLabel();
                    setButtonsEnabled(true);
                });
            } catch (Exception exception) {
                reportFailure("Maven repair failed", exception);
            }
        });
    }

    private void renderReport(MavenDiagnosticReport report) {
        tableModel.setIssues(report.issues());
        statusLabel.setText("Scan completed: " + report.issues().size() + " issue(s)");
        summaryLabel.setText(
                "Repository artifacts: " + report.localArtifactCount()
                        + " | Missing from index: " + report.missingFromIndexCount()
                        + " | Settings: " + report.settingsPath()
        );
        setButtonsEnabled(true);
    }

    private void reportFailure(String title, Exception exception) {
        LOG.warn(title, exception);
        invokeLater(() -> {
            statusLabel.setText(title + ": " + errorMessage(exception));
            setButtonsEnabled(true);
        });
    }

    private DiagnosticIssue selectedIssue() {
        int selectedRow = issueTable.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        return tableModel.getIssue(issueTable.convertRowIndexToModel(selectedRow));
    }

    private boolean confirmed(String message) {
        return Messages.showYesNoDialog(
                project,
                message,
                "Maven Index Doctor",
                Messages.getWarningIcon()
        ) == Messages.YES;
    }

    private void setBusy(String message) {
        statusLabel.setText(message);
        setButtonsEnabled(false);
    }

    private void setButtonsEnabled(boolean enabled) {
        for (JButton button : actionButtons) {
            button.setEnabled(enabled);
        }
    }

    private void updateBackupLabel() {
        Path backupPath = repairService.latestBackupPath();
        backupLabel.setText(backupPath == null ? "No backup recorded" : "Latest backup: " + backupPath);
    }

    private void invokeLater(Runnable action) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                action.run();
            }
        });
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    private interface RepairOperation {
        RepairResult execute() throws Exception;
    }
}
