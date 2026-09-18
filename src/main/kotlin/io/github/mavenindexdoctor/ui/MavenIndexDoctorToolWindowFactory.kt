package io.github.mavenindexdoctor.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import io.github.mavenindexdoctor.application.MavenDiagnosticsService
import io.github.mavenindexdoctor.domain.MavenDiagnosticReport
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class MavenIndexDoctorToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MavenIndexDoctorPanel()
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }
}

private class MavenIndexDoctorPanel : JBPanel<MavenIndexDoctorPanel>(BorderLayout()) {
    private val statusLabel = JBLabel("Ready to scan")
    private val resultArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }

    init {
        val scanButton = JButton("Scan Maven indexes")
        scanButton.addActionListener {
            scanButton.isEnabled = false
            statusLabel.text = "Scanning..."
            ApplicationManager.getApplication().executeOnPooledThread {
                val result = try {
                    Result.success(MavenDiagnosticsService().scan())
                } catch (exception: Exception) {
                    Result.failure(exception)
                }
                ApplicationManager.getApplication().invokeLater {
                    result.fold(::render, ::renderError)
                    scanButton.isEnabled = true
                }
            }
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(scanButton)
            add(statusLabel)
        }
        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(resultArea), BorderLayout.CENTER)
    }

    private fun render(report: MavenDiagnosticReport) {
        statusLabel.text = "Scan completed: ${report.issues.size} issue(s)"
        resultArea.text = buildString {
            appendLine("Repository: ${report.repositoryPath}")
            appendLine("Indexes: ${report.indexPath}")
            appendLine()
            if (report.issues.isEmpty()) {
                appendLine("No issues detected.")
            } else {
                report.issues.forEach { issue ->
                    appendLine("[${issue.severity}] ${issue.title}")
                    appendLine(issue.details)
                    issue.path?.let { appendLine("Path: $it") }
                    appendLine()
                }
            }
        }
    }

    private fun renderError(exception: Throwable) {
        statusLabel.text = "Scan failed"
        resultArea.text = "Unable to scan Maven data: ${exception.message ?: exception::class.simpleName}"
    }
}
