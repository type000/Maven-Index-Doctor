package io.github.mavenindexdoctor.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class MavenIndexDoctorToolWindowFactory implements ToolWindowFactory {

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        MavenIndexDoctorPanel panel = new MavenIndexDoctorPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, null, false);
        toolWindow.getContentManager().addContent(content);
    }
}
