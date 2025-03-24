package com.example.ijcommittracer.actions

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.services.HiBobApiService
import com.example.ijcommittracer.services.NotificationService
import com.example.ijcommittracer.services.TokenStorageService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * Action for clearing stored HiBob API token.
 */
class ClearHiBobTokenAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Confirm with the user before clearing the token
        val result = Messages.showYesNoDialog(
            project,
            CommitTracerBundle.message("hibob.token.clear.confirmation"),
            CommitTracerBundle.message("hibob.token.clear.title"),
            CommitTracerBundle.message("hibob.token.clear.yes"),
            CommitTracerBundle.message("hibob.token.clear.no"),
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            // Clear the token
            TokenStorageService.getInstance(project).clearHiBobToken()
            
            // Clear the cache
            HiBobApiService.getInstance(project).clearCache()
            
            // Notify the user
            NotificationService.showInfo(
                project,
                CommitTracerBundle.message("hibob.token.cleared"),
                "Commit Tracer"
            )
        }
    }
}