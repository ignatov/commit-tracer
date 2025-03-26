package com.example.ijcommittracer.services

import com.example.ijcommittracer.util.JsonConfigReader
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Configuration service that reads all settings from a single .env.json file:
 * - YouTrack credentials
 * - HiBob API tokens
 * - Email mappings
 */
@Service(Service.Level.PROJECT)
class ConfigurationService(private val project: Project) {
    private val LOG = logger<ConfigurationService>()
    
    // Config reader for accessing all configuration
    private lateinit var configReader: JsonConfigReader

    init {
        // Initialize config reader
        initConfigReader()
    }
    
    /**
     * Initialize the configuration reader
     */
    private fun initConfigReader() {
        val configFilePath = getConfigFilePath()
        configReader = JsonConfigReader.getInstance(configFilePath)
        LOG.info("Initialized configuration reader with path: $configFilePath")
    }
    
    //
    // YouTrack Methods
    //
    
    fun getYouTrackToken(): String? {
        return configReader.getString(JsonConfigReader.YOUTRACK_TOKEN_KEY)
    }
    
    fun getYouTrackUrl(): String {
        return configReader.getString(
            JsonConfigReader.YOUTRACK_URL_KEY, 
            JsonConfigReader.DEFAULT_YOUTRACK_URL
        )
    }
    
    //
    // HiBob Methods
    //
    
    fun getHiBobToken(): String? {
        return configReader.getString(JsonConfigReader.HIBOB_TOKEN_KEY)
    }
    
    fun getHiBobBaseUrl(): String {
        return configReader.getString(
            JsonConfigReader.HIBOB_URL_KEY, 
            JsonConfigReader.DEFAULT_HIBOB_URL
        )
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
        return configReader.mapEmail(email)
    }
    
    /**
     * Gets all email mappings as a Map
     * 
     * @return A copy of the email mappings
     */
    fun getAllEmailMappings(): Map<String, String> {
        return configReader.getAllEmailMappings()
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
        return configReader.addEmailMapping(fromEmail, toEmail, save)
    }
    
    /**
     * Removes an email mapping
     * 
     * @param email The non-standard email to remove the mapping for
     * @param save Whether to save the changes to the file
     * @return True if the mapping was removed successfully
     */
    fun removeEmailMapping(email: String, save: Boolean = true): Boolean {
        return configReader.removeEmailMapping(email, save)
    }
    
    /**
     * Updates a configuration value
     *
     * @param key The configuration key to update
     * @param value The new value
     * @param save Whether to save the change to the file
     * @return True if the update was successful
     */
    fun updateValue(key: String, value: Any, save: Boolean = true): Boolean {
        return configReader.updateValue(key, value, save)
    }
    
    /**
     * Gets the path to the configuration file
     * The file is stored in the project directory or user home as fallback
     */
    fun getConfigFilePath(): String {
        val projectPath = project.basePath
        return if (projectPath != null) {
            File(projectPath, JsonConfigReader.DEFAULT_CONFIG_FILENAME).absolutePath
        } else {
            // Fallback to user home directory if project path is not available
            val userHome = System.getProperty("user.home")
            File(userHome, ".commitmapper/${JsonConfigReader.DEFAULT_CONFIG_FILENAME}").absolutePath
        }
    }
    
    companion object {
        @JvmStatic
        fun getInstance(project: Project): ConfigurationService = project.service()
    }
}