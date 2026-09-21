package io.github.mavenindexdoctor.domain;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record RepairResult(String message, int affectedFileCount, @Nullable Path backupPath) {
}
