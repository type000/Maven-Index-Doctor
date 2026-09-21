package io.github.mavenindexdoctor.ui;

import io.github.mavenindexdoctor.domain.DiagnosticIssue;

import javax.swing.table.AbstractTableModel;
import java.util.List;

final class DiagnosticIssueTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {"Severity", "Problem", "Location", "Recommended action"};

    private List<DiagnosticIssue> issues = List.of();

    public void setIssues(List<DiagnosticIssue> issues) {
        this.issues = List.copyOf(issues);
        fireTableDataChanged();
    }

    public DiagnosticIssue getIssue(int rowIndex) {
        return issues.get(rowIndex);
    }

    @Override
    public int getRowCount() {
        return issues.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        DiagnosticIssue issue = issues.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> issue.severity();
            case 1 -> issue.title() + " — " + issue.details();
            case 2 -> issue.path() == null ? "" : issue.path().toString();
            case 3 -> issue.recommendedAction();
            default -> throw new IllegalArgumentException("Unknown column index: " + columnIndex);
        };
    }
}
