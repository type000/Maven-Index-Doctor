package io.github.mavenindexdoctor.application;

import io.github.mavenindexdoctor.domain.DiagnosticIssue;
import io.github.mavenindexdoctor.domain.DiagnosticIssueType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class MavenSettingsScannerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void reportsCentralMirrorAndActiveProxy() throws Exception {
        Path settings = temporaryFolder.newFile("settings.xml").toPath();
        Files.writeString(settings, """
                <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
                  <mirrors>
                    <mirror>
                      <id>company</id>
                      <url>https://repo.example/maven</url>
                      <mirrorOf>*</mirrorOf>
                    </mirror>
                  </mirrors>
                  <proxies>
                    <proxy>
                      <active>true</active>
                      <host>proxy.example</host>
                      <port>8080</port>
                    </proxy>
                  </proxies>
                </settings>
                """);

        List<DiagnosticIssue> issues = new MavenSettingsScanner().scan(settings);

        assertTrue(issues.stream().anyMatch(
                issue -> issue.type() == DiagnosticIssueType.CENTRAL_MIRROR_CONFIGURED
        ));
        assertTrue(issues.stream().anyMatch(
                issue -> issue.type() == DiagnosticIssueType.ACTIVE_PROXY
        ));
    }

    @Test
    public void rejectsDoctypeDeclarations() throws Exception {
        Path settings = temporaryFolder.newFile("settings-with-doctype.xml").toPath();
        Files.writeString(settings, """
                <!DOCTYPE settings [<!ENTITY external SYSTEM "file:///secret">]>
                <settings><localRepository>&external;</localRepository></settings>
                """);

        List<DiagnosticIssue> issues = new MavenSettingsScanner().scan(settings);

        assertTrue(issues.stream().anyMatch(
                issue -> issue.type() == DiagnosticIssueType.SETTINGS_INVALID
        ));
    }
}
