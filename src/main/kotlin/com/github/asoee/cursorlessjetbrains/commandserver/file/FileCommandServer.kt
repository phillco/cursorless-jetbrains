package com.github.asoee.cursorlessjetbrains.commandserver.file

import com.github.asoee.cursorlessjetbrains.javet.ExecutionResult
import com.github.asoee.cursorlessjetbrains.services.TalonProjectService
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText


class FileCommandServer {

    companion object {
        private const val PREFIX = "jetbrains"
        val logger = logger<FileCommandServer>()
    }

    private val commandServerDir: Path
    private val signalsDir: Path

    init {
        logger.info("FileCommandServer: FilePlatform prefix: $PREFIX")

        val suffix = getUserIdSuffix()

        val commandServerDir = Path(System.getProperty("java.io.tmpdir"))
            .resolve("${PREFIX}-command-server$suffix")
        logger.info("FileCommandServer: dir: $commandServerDir")
        commandServerDir.toFile().mkdirs()
        this.commandServerDir = commandServerDir
        this.signalsDir = commandServerDir.resolve("signals")
        this.signalsDir.toFile().mkdirs()
    }

    private fun getUserIdSuffix(): String {
        val selfPath = Paths.get(System.getProperty("user.home"))
        if (!selfPath.exists()) {
            return ""
        }
        try {
            val uid = Files.getAttribute(selfPath, "unix:uid")
            return "-$uid"
        } catch (e: UnsupportedOperationException) {
            logger.warn("Error getting home uid attribute (not supported on this platform) " + e.message)
        }
        try {
            val userName = System.getProperty("user.name")
            readProcessOutput(arrayOf("id", "-u", userName)).let {
                try {
                    Integer.parseInt(it)
                    return "-$it"
                } catch (e: NumberFormatException) {
                    logger.warn("Error parsing uid from id command output $it")
                }

            }
        } catch (e: IOException) {
            logger.warn("Error getting uid from id command " + e.message)
        }
        logger.warn("Fallback to no uid suffix")
        return ""
    }

    private fun readProcessOutput(command: Array<String>): String {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
        }
        process.waitFor()
        return output.toString()
    }

    fun checkAndHandleFileRquest(project: Project) {
        val requestPath = commandServerDir.resolve("request.json")
        if (requestPath.exists()) {
            readAndHandleFileRquest(requestPath, project)
        }
    }

    fun prePhraseVersion(): String? {
        val prePhrasePath = this.signalsDir.resolve("prePhrase")
        if (prePhrasePath.exists()) {
            val prePhraseVersion = prePhrasePath.toFile().lastModified().toString()
            return prePhraseVersion
        } else {
            return null
        }
    }

    private fun readAndHandleFileRquest(fullPath: Path?, project: Project) {
        fullPath?.readText().let {
            logger.debug("File content: $it")
            val request = Json.decodeFromString<CommandServerRequest>(it!!)
            val result = handleRequest(request, project)
            if (result.success) {
                val response = CommandServerResponse(
                    request.uuid,
                    emptyArray(),
                    null,
                    TalonCommandReponse(result.returnValue),
                )
                writeResponse(response)
            } else {
                // Show error notification in the IDE
                val fullError = result.error ?: "Unknown error occurred"
                val errorMessage = formatErrorMessage(fullError)
                showErrorNotification(project, "Cursorless Command Failed", errorMessage, fullError)
                
                val response = CommandServerResponse(
                    request.uuid,
                    emptyArray(),
                    result.error,
                    TalonCommandReponse(null),
                )

                writeResponse(response)
            }
        }
    }
    
    private fun formatErrorMessage(error: String): String {
        // Extract the most relevant part of the error message
        return when {
            error.contains("is not defined") -> {
                // JavaScript reference errors
                error.substringBefore(" at ").trim()
            }
            error.contains("Error:") -> {
                // Error with stack trace - get just the error message
                error.substringAfter("Error:").substringBefore("\n").trim()
            }
            error.contains("Exception:") -> {
                // Java exceptions
                error.substringAfter("Exception:").substringBefore("\n").trim()
            }
            else -> error.take(200) // Limit length for readability
        }
    }
    
    private fun showErrorNotification(project: Project, title: String, content: String, fullError: String) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("vc-idea")
            .createNotification(title, content, NotificationType.ERROR)
        
        // Add action to view full error details
        notification.addAction(object : com.intellij.notification.NotificationAction("View Full Error") {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent, notification: com.intellij.notification.Notification) {
                // Store the full error in a temporary file and open it
                showFullError(project, fullError)
                notification.expire()
            }
        })
        
        notification.notify(project)
    }
    
    private fun showFullError(project: Project, fullError: String) {
        try {
            // Extract command information if available from the request
            val commandInfo = try {
                val lastRequest = commandServerDir.resolve("request.json")
                if (lastRequest.exists()) {
                    "Last Command Request:\n${lastRequest.readText()}\n"
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
            
            val errorOutput = """
Cursorless Command Error Details
================================
Time: ${LocalDateTime.now()}

${commandInfo}Full Error:
-----------
$fullError

${if (fullError.contains("\n")) "" else """Stack Trace:
------------
No stack trace available. This error appears to be a simple error message.

Common causes:
- The command/feature is not yet implemented
- Invalid command parameters
- Missing dependencies or configuration
"""}

Note: This file contains the complete error information available.
You can share this with the plugin developers if needed.
            """.trimIndent()
            
            // Display in console using ConsoleViewContentType
            com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
            val consoleView = com.intellij.execution.impl.ConsoleViewImpl(project, true)
            consoleView.print(errorOutput, com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT)
            
            // Create a content descriptor to show in tool window
            val descriptor = com.intellij.execution.ui.RunContentDescriptor(
                consoleView,
                null,
                consoleView.component,
                "Cursorless Error Details"
            )
            
            // Show in Run tool window
            val executor = com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance()
            com.intellij.execution.ui.RunContentManager.getInstance(project).showRunContent(executor, descriptor)
            
        } catch (e: Exception) {
            logger.warn("Failed to show full error", e)
        }
    }

    private fun writeResponse(response: CommandServerResponse) {
        val responsePath = commandServerDir.resolve("response.json")
        val responseJson = Json.encodeToString(response)
        responsePath.writeText(responseJson + "\n")
        logger.info("'Wrote response'...$responseJson")
    }

    private fun handleRequest(request: CommandServerRequest, project: Project): ExecutionResult {
        logger.info("Handling request..." + request.commandId + " " + request.args + " " + request.uuid)
        val service = project.service<TalonProjectService>()
        val executionResult = service.jsDriver.execute(request.args)
        return executionResult
    }

}