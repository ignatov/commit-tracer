package com.example.ijcommittracer.services

import com.example.ijcommittracer.util.EnvFileReader
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Configuration service that reads settings from .env file and manages email mappings:
 * - YouTrack credentials
 * - HiBob API tokens
 * - Email mappings
 */
@Service(Service.Level.PROJECT)
class ConfigurationService(private val project: Project) {
    // Constants
    private val EMAIL_MAPPINGS_FILENAME = "email_mappings.json"
    
    // Environment variable keys
    private val YOUTRACK_TOKEN_KEY = "YOUTRACK_API_TOKEN"
    private val YOUTRACK_URL_KEY = "YOUTRACK_API_URL"
    private val HIBOB_TOKEN_KEY = "HIBOB_API_TOKEN"
    private val HIBOB_URL_KEY = "HIBOB_API_URL" 
    
    // Default values
    private val DEFAULT_YOUTRACK_URL = "https://youtrack.jetbrains.com/api"
    private val DEFAULT_HIBOB_URL = "https://api.hibob.com/v1"
    
    private val LOG = logger<ConfigurationService>()
    
    // Thread-safe email mappings
    private val emailMappings = ConcurrentHashMap<String, String>()
    private val lock = ReentrantReadWriteLock()
    private var lastModifiedTime: Long = 0
    
    // JSON serializer/deserializer
    private val json = Json { 
        prettyPrint = true 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Env file reader
    private lateinit var envReader: EnvFileReader

    init {
        // Initialize env file reader
        initEnvReader()
        
        // Load email mappings from file
        loadEmailMappings()
    }
    
    /**
     * Initialize the environment file reader
     */
    private fun initEnvReader() {
        val projectPath = project.basePath
        val envFilePath = if (projectPath != null) {
            File(projectPath, ".env").absolutePath
        } else {
            // Fallback to user home directory if project path is not available
            val userHome = System.getProperty("user.home")
            File(userHome, ".commitmapper/.env").absolutePath
        }
        
        envReader = EnvFileReader.getInstance(envFilePath)
        LOG.info("Initialized env reader with path: $envFilePath")
    }
    
    /**
     * Email mappings data class for serialization
     */
    @Serializable
    data class EmailMappings(
        val mappings: Map<String, String> = emptyMap()
    )
    
    //
    // YouTrack Methods
    //
    
    fun getYouTrackToken(): String? {
        return envReader.getProperty(YOUTRACK_TOKEN_KEY)
    }
    
    fun getYouTrackUrl(): String {
        return envReader.getProperty(YOUTRACK_URL_KEY, DEFAULT_YOUTRACK_URL)
    }
    
    //
    // HiBob Methods
    //
    
    fun getHiBobToken(): String? {
        return envReader.getProperty(HIBOB_TOKEN_KEY)
    }
    
    fun getHiBobBaseUrl(): String {
        return envReader.getProperty(HIBOB_URL_KEY, DEFAULT_HIBOB_URL)
    }
    
    //
    // Email Mapping Methods
    //
    
    /**
     * Maps a non-standard email to its standard counterpart if a mapping exists
     * 
     * @param email The email address to map
     * @return The mapped email address or the original if no mapping exists
     */
    fun mapEmail(email: String): String {
        return lock.read {
            checkFileChanged()
            emailMappings[email] ?: email
        }
    }
    
    /**
     * Gets all email mappings as a Map
     * 
     * @return A copy of the email mappings
     */
    fun getAllEmailMappings(): Map<String, String> {
        return lock.read {
            checkFileChanged()
            HashMap(emailMappings)
        }
    }
    
    /**
     * Adds a new email mapping
     * 
     * @param fromEmail The non-standard email
     * @param toEmail The standard email
     * @param save Whether to save the mapping to the file
     * @return True if the mapping was added successfully
     */
    fun addEmailMapping(fromEmail: String, toEmail: String, save: Boolean = true): Boolean {
        lock.write {
            checkFileChanged()
            emailMappings[fromEmail] = toEmail
            
            if (save) {
                return saveEmailMappings()
            }
        }
        
        return true
    }
    
    /**
     * Removes an email mapping
     * 
     * @param email The non-standard email to remove the mapping for
     * @param save Whether to save the changes to the file
     * @return True if the mapping was removed successfully
     */
    fun removeEmailMapping(email: String, save: Boolean = true): Boolean {
        lock.write {
            checkFileChanged()
            val removed = emailMappings.remove(email) != null
            
            if (removed && save) {
                return saveEmailMappings()
            }
            
            return removed
        }
    }
    
    /**
     * Loads email mappings from the JSON file
     */
    private fun loadEmailMappings() {
        val mappingsFile = File(getEmailMappingsFilePath())
        
        lock.write {
            try {
                LOG.info("Looking for email mappings file at: ${mappingsFile.absolutePath}")
                
                if (mappingsFile.exists()) {
                    val jsonString = mappingsFile.readText()
                    
                    try {
                        // Try to parse as our EmailMappings class
                        val loadedMappings = json.decodeFromString<EmailMappings>(jsonString)
                        
                        // Update email mappings
                        emailMappings.clear()
                        emailMappings.putAll(loadedMappings.mappings)
                        
                        lastModifiedTime = mappingsFile.lastModified()
                        LOG.info("Successfully loaded email mappings file with ${emailMappings.size} mappings")
                    } catch (e: Exception) {
                        LOG.error("Failed to parse email mappings file: ${e.message}", e)
                        // Create default mappings if parsing fails
                        createDefaultMappings(mappingsFile)
                    }
                } else {
                    LOG.info("No email mappings file found at ${mappingsFile.absolutePath}")
                    // Create parent directories if they don't exist
                    mappingsFile.parentFile?.mkdirs()
                    
                    // Create a default mappings file
                    createDefaultMappings(mappingsFile)
                }
            } catch (e: Exception) {
                LOG.warn("Failed to load email mappings file", e)
            }
        }
    }
    
    /**
     * Creates a default mappings file
     */
    private fun createDefaultMappings(mappingsFile: File) {
        val defaultMappings = EmailMappings(
            mappings = mapOf(
                "personal@gmail.com" to "developer@company.com",
                "old.email@hotmail.com" to "jane.smith@company.com"
            )
        )
        
        try {
            val mappingsJson = json.encodeToString(defaultMappings)
            mappingsFile.writeText(mappingsJson)
            
            // Update in-memory state
            emailMappings.clear()
            emailMappings.putAll(defaultMappings.mappings)
            
            lastModifiedTime = mappingsFile.lastModified()
            LOG.info("Created default email mappings file at ${mappingsFile.absolutePath}")
        } catch (e: Exception) {
            LOG.warn("Failed to create default mappings file", e)
        }
    }
    
    /**
     * Saves the current email mappings to the JSON file
     * 
     * @return True if the mappings were successfully saved
     */
    private fun saveEmailMappings(): Boolean {
        val mappingsFile = File(getEmailMappingsFilePath())
        
        return try {
            // Ensure parent directories exist
            mappingsFile.parentFile?.mkdirs()
            
            // Create an EmailMappings object from our current mappings
            val mappingsToSave = EmailMappings(
                mappings = HashMap(emailMappings)
            )
            
            // Write JSON with pretty printing
            val jsonString = json.encodeToString(mappingsToSave)
            mappingsFile.writeText(jsonString)
            
            // Update last modified time
            lastModifiedTime = mappingsFile.lastModified()
            LOG.info("Successfully saved email mappings to file")
            true
        } catch (e: Exception) {
            LOG.error("Failed to save email mappings: ${e.message}", e)
            false
        }
    }
    
    /**
     * Checks if the email mappings file has changed and reloads if necessary
     */
    private fun checkFileChanged() {
        val mappingsFile = File(getEmailMappingsFilePath())
        if (mappingsFile.exists() && mappingsFile.lastModified() > lastModifiedTime) {
            lock.write {
                if (mappingsFile.lastModified() > lastModifiedTime) {
                    LOG.info("Email mappings file has changed, reloading")
                    try {
                        val jsonString = mappingsFile.readText()
                        val loadedMappings = json.decodeFromString<EmailMappings>(jsonString)
                        
                        // Update email mappings
                        emailMappings.clear()
                        emailMappings.putAll(loadedMappings.mappings)
                        
                        lastModifiedTime = mappingsFile.lastModified()
                        LOG.info("Successfully reloaded email mappings file")
                    } catch (e: Exception) {
                        LOG.error("Failed to reload email mappings: ${e.message}", e)
                    }
                }
            }
        }
    }
    
    /**
     * Gets the path to the email mappings file
     * The file is stored in the project directory
     */
    fun getEmailMappingsFilePath(): String {
        val projectPath = project.basePath
        return if (projectPath != null) {
            File(projectPath, EMAIL_MAPPINGS_FILENAME).absolutePath
        } else {
            // Fallback to user home directory if project path is not available
            val userHome = System.getProperty("user.home")
            File(userHome, ".commitmapper/$EMAIL_MAPPINGS_FILENAME").absolutePath
        }
    }
    
    /**
     * Gets the path to the project's .env file
     */
    fun getEnvFilePath(): String {
        val projectPath = project.basePath
        return if (projectPath != null) {
            File(projectPath, ".env").absolutePath
        } else {
            val userHome = System.getProperty("user.home")
            File(userHome, ".commitmapper/.env").absolutePath
        }
    }
    
    companion object {
        @JvmStatic
        fun getInstance(project: Project): ConfigurationService = project.service()
    }
}