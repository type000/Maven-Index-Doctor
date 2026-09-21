package io.github.mavenindexdoctor.domain;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record DiagnosticIssue(
        DiagnosticIssueType type,
        DiagnosticSeverity severity,
        String title,
        String details,
        @Nullable Path path,
        String recommendedAction
) {
}
