package io.github.mavenindexdoctor.application;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MavenIndexFileOperationsTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void backsUpClearsAndRestoresIndexDirectory() throws Exception {
        Path root = temporaryFolder.newFolder("system").toPath();
        Path indexDirectory = root.resolve("Maven/Indices");
        Path backupRoot = root.resolve("Maven/MavenIndexDoctorBackups");
        Path indexFile = indexDirectory.resolve("repository/index.data");
        Files.createDirectories(indexFile.getParent());
        Files.writeString(indexFile, "healthy-index");

        MavenIndexFileOperations operations = new MavenIndexFileOperations();
        Path backupPath = operations.backupAndClear(indexDirectory, backupRoot);

        assertTrue(Files.isDirectory(backupPath));
        assertFalse(Files.exists(indexFile));
        assertEquals("healthy-index", Files.readString(backupPath.resolve("repository/index.data")));

        operations.restore(indexDirectory, backupRoot, backupPath);

        assertEquals("healthy-index", Files.readString(indexFile));
    }
}
