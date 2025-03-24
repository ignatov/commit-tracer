package com.example.ijcommittracer.actions

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.services.HiBobApiService
import com.example.ijcommittracer.services.NotificationService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPasswordField

/**
 * Action for configuring HiBob API token.
 */
class ConfigureHiBobTokenAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = HiBobTokenDialog(project)
        if (dialog.showAndGet()) {
            val token = dialog.getToken()
            val baseUrl = dialog.getBaseUrl()
            
            // Save token and base URL
            HiBobApiService.getInstance(project).setApiCredentials(token, baseUrl)
            
            // Validate token by attempting to fetch a user
            validateToken(project, token, baseUrl)
        } else {
            NotificationService.showInfo(
                project,
                CommitTracerBundle.message("hibob.token.canceled"),
                "Commit Tracer"
            )
        }
    }
    
    private fun validateToken(project: Project, @Suppress("UNUSED_PARAMETER") token: String, @Suppress("UNUSED_PARAMETER") baseUrl: String) {
        NotificationService.showInfo(
            project,
            CommitTracerBundle.message("hibob.token.validating"),
            "Commit Tracer"
        )
        
        // Use a coroutine to validate the token asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Trigger a refresh of the HiBob cache to validate the token 
                // (the token is already saved and will be used by the service)
                val service = HiBobApiService.getInstance(project)
                service.refreshFullCache()
                
                // If we reach here without exception, token is valid
                withContext(Dispatchers.Main) {
                    NotificationService.showInfo(
                        project,
                        CommitTracerBundle.message("hibob.token.stored"),
                        "Commit Tracer"
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    NotificationService.showError(
                        project,
                        CommitTracerBundle.message("hibob.token.invalid"),
                        "Commit Tracer"
                    )
                }
            }
        }
    }
    
    /**
     * Dialog for entering HiBob API token.
     */
    private class HiBobTokenDialog(project: Project) : DialogWrapper(project) {
        private val tokenField = JPasswordField(40)
        private val baseUrlField = JBTextField("https://api.hibob.com/v1", 40)
        
        init {
            title = CommitTracerBundle.message("hibob.token.dialog.title")
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            val panel = JPanel(GridBagLayout())
            panel.border = JBUI.Borders.empty(10)
            
            val constraints = GridBagConstraints()
            constraints.fill = GridBagConstraints.HORIZONTAL
            constraints.anchor = GridBagConstraints.WEST
            
            // Add token prompt label
            val promptText = CommitTracerBundle.message("hibob.token.prompt")
            val promptLabel = JBLabel("<html>${promptText.replace("\n", "<br>")}</html>")
            constraints.gridx = 0
            constraints.gridy = 0
            constraints.gridwidth = 2
            constraints.weightx = 1.0
            constraints.insets = JBUI.insets(0, 0, 10, 0)
            panel.add(promptLabel, constraints)
            
            // Add token label
            val tokenLabel = JBLabel(CommitTracerBundle.message("hibob.token.label"))
            constraints.gridx = 0
            constraints.gridy = 1
            constraints.gridwidth = 1
            constraints.weightx = 0.0
            constraints.insets = JBUI.insets(5, 0, 5, 10)
            panel.add(tokenLabel, constraints)
            
            // Add token field
            constraints.gridx = 1
            constraints.gridy = 1
            constraints.gridwidth = 1
            constraints.weightx = 1.0
            constraints.insets = JBUI.insets(5, 0, 5, 0)
            panel.add(tokenField, constraints)
            
            // Add base URL label
            val baseUrlLabel = JBLabel(CommitTracerBundle.message("hibob.api.url.label"))
            constraints.gridx = 0
            constraints.gridy = 2
            constraints.gridwidth = 1
            constraints.weightx = 0.0
            constraints.insets = JBUI.insets(5, 0, 5, 10)
            panel.add(baseUrlLabel, constraints)
            
            // Add base URL field
            constraints.gridx = 1
            constraints.gridy = 2
            constraints.gridwidth = 1
            constraints.weightx = 1.0
            constraints.insets = JBUI.insets(5, 0, 5, 0)
            panel.add(baseUrlField, constraints)
            
            return panel
        }
        
        override fun doValidate(): ValidationInfo? {
            if (String(tokenField.password).isBlank()) {
                return ValidationInfo(CommitTracerBundle.message("hibob.token.required"), tokenField)
            }
            
            if (baseUrlField.text.isBlank()) {
                return ValidationInfo(CommitTracerBundle.message("hibob.api.url.required"), baseUrlField)
            }
            
            // Simple URL validation
            if (!baseUrlField.text.startsWith("http://") && !baseUrlField.text.startsWith("https://")) {
                return ValidationInfo(CommitTracerBundle.message("hibob.api.url.invalid"), baseUrlField)
            }
            
            return null
        }
        
        fun getToken(): String = String(tokenField.password)
        
        fun getBaseUrl(): String = baseUrlField.text
    }
}