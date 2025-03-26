package com.example.ijcommittracer.util

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * JsonConfigReader provides a unified configuration file reader for the Commit Tracer plugin.
 * It reads a single JSON file that contains all necessary configuration:
 * - API tokens (YouTrack, HiBob)
 * - API URLs
 * - Email mappings
 * 
 * The configuration file (.env.json) looks like:
 * {
 *   "youtrackToken": "your-token-here",
 *   "youtrackUrl": "https://youtrack.example.com/api",
 *   "hibobToken": "your-hibob-token-here",
 *   "hibobApiUrl": "https://api.hibob.com/v1",
 *   "emailMappings": {
 *     "personal@gmail.com": "work@company.com",
 *     "other@hotmail.com": "other@company.com"
 *   }
 * }
 */
class JsonConfigReader(private val configFilePath: String, private val debugLogger: ((String) -> Unit)? = null) {
    private val LOG = try {
        logger<JsonConfigReader>()
    } catch (e: Exception) {
        null // Support for non-IntelliJ environments
    }
    
    private val config = ConcurrentHashMap<String, Any>()
    private var initialized = false
    private val lock = ReentrantReadWriteLock()
    private var lastModifiedTime: Long = 0
    private val gson = Gson()
    
    companion object {
        // Use ConcurrentHashMap for thread-safe instance storage
        private val instances = ConcurrentHashMap<String, JsonConfigReader>()
        
        // Config keys
        const val YOUTRACK_TOKEN_KEY = "youtrackToken"
        const val YOUTRACK_URL_KEY = "youtrackUrl"
        const val HIBOB_TOKEN_KEY = "hibobToken"
        const val HIBOB_URL_KEY = "hibobApiUrl"
        const val EMAIL_MAPPINGS_KEY = "emailMappings"
        
        // Default values
        const val DEFAULT_YOUTRACK_URL = "https://youtrack.jetbrains.com/api"
        const val DEFAULT_HIBOB_URL = "https://api.hibob.com/v1"
        const val DEFAULT_CONFIG_FILENAME = ".env.json"
        
        /**
         * Gets an instance of JsonConfigReader for the given file path.
         * This method is thread-safe and will always return the same instance
         * for the same file path.
         * 
         * @param filePath The absolute path to the config file
         * @param debugLogger Optional function for logging (used in CLI)
         * @return A JsonConfigReader instance for the file
         */
        fun getInstance(filePath: String, debugLogger: ((String) -> Unit)? = null): JsonConfigReader {
            // computeIfAbsent is thread-safe and avoids race conditions
            return instances.computeIfAbsent(filePath) {
                JsonConfigReader(filePath, debugLogger).apply { initialize() }
            }
        }
        
        /**
         * Creates a default configuration file in the specified directory
         * 
         * @param directory The directory to create the file in
         * @param fileName The name of the file (default: .env.json)
         * @return True if the file was created, false otherwise
         */
        fun createDefaultConfigFile(
            directory: String,
            fileName: String = DEFAULT_CONFIG_FILENAME
        ): Boolean {
            val file = File(directory, fileName)
            if (file.exists()) return false
            
            val defaultConfig = mapOf(
                YOUTRACK_TOKEN_KEY to "your-youtrack-token-here",
                YOUTRACK_URL_KEY to DEFAULT_YOUTRACK_URL,
                HIBOB_TOKEN_KEY to "your-hibob-token-here",
                HIBOB_URL_KEY to DEFAULT_HIBOB_URL,
                EMAIL_MAPPINGS_KEY to mapOf(
                    "personal@gmail.com" to "work@company.com",
                    "old@legacy.com" to "new@company.com"
                )
            )
            
            try {
                val json = Gson().toJson(defaultConfig)
                file.writeText(json)
                return true
            } catch (e: Exception) {
                return false
            }
        }
    }
    
    /**
     * Initialize by loading configuration from the JSON file
     */
    private fun initialize() {
        if (initialized) return
        
        lock.write {
            if (initialized) return@write
            
            try {
                val configFile = File(configFilePath)
                val logMsg = "Looking for config file at: ${configFile.absolutePath}"
                LOG?.info(logMsg) ?: debugLogger?.invoke(logMsg)
                
                if (configFile.exists()) {
                    loadConfigFromFile(configFile)
                } else {
                    val notFoundMsg = "No config file found at ${configFile.absolutePath}"
                    LOG?.info(notFoundMsg) ?: debugLogger?.invoke(notFoundMsg)
                    
                    // Create parent directories if they don't exist
                    configFile.parentFile?.mkdirs()
                    
                    // Try to create a default config file
                    try {
                        val parentDir = configFile.parentFile
                        createDefaultConfigFile(parentDir.absolutePath, configFile.name)
                        val createdMsg = "Created default config file at ${configFile.absolutePath}"
                        LOG?.info(createdMsg) ?: debugLogger?.invoke(createdMsg)
                    } catch (e: Exception) {
                        val errorMsg = "Failed to create default config file: ${e.message}"
                        LOG?.warn(errorMsg, e) ?: debugLogger?.invoke(errorMsg)
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "Failed to load config file: ${e.message}"
                LOG?.warn(errorMsg, e) ?: debugLogger?.invoke(errorMsg)
            } finally {
                initialized = true
            }
        }
    }
    
    /**
     * Loads configuration from the specified JSON file
     */
    private fun loadConfigFromFile(file: File) {
        try {
            val json = file.readText()
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            
            @Suppress("UNCHECKED_CAST")
            val loadedConfig = gson.fromJson<Map<String, Any>>(json, mapType)
            
            config.clear()
            
            // Process email mappings separately to ensure correct typing
            val emailMappingsMap = loadedConfig[EMAIL_MAPPINGS_KEY] as? Map<*, *>
            if (emailMappingsMap != null) {
                val typedMappings = mutableMapOf<String, String>()
                emailMappingsMap.forEach { (key, value) ->
                    if (key is String && value is String) {
                        typedMappings[key] = value
                    }
                }
                config[EMAIL_MAPPINGS_KEY] = typedMappings
            } else {
                config[EMAIL_MAPPINGS_KEY] = emptyMap<String, String>()
            }
            
            // Add all other configuration values
            loadedConfig.forEach { (key, value) ->
                if (key != EMAIL_MAPPINGS_KEY) {
                    config[key] = value
                }
            }
            
            lastModifiedTime = file.lastModified()
            val loadSuccessMsg = "Successfully loaded config file with ${config.size} entries"
            LOG?.info(loadSuccessMsg) ?: debugLogger?.invoke(loadSuccessMsg)
        } catch (e: JsonSyntaxException) {
            val syntaxErrorMsg = "Invalid JSON in config file: ${e.message}"
            LOG?.error(syntaxErrorMsg) ?: debugLogger?.invoke(syntaxErrorMsg)
        } catch (e: Exception) {
            val errorMsg = "Error loading config file: ${e.message}"
            LOG?.error(errorMsg) ?: debugLogger?.invoke(errorMsg)
        }
    }
    
    /**
     * Gets a string value from the configuration
     * 
     * @param key The configuration key to look up
     * @return The value as a String or null if not found or wrong type
     */
    fun getString(key: String): String? {
        if (!initialized) {
            initialize()
        }
        
        return lock.read {
            checkFileChanged()
            config[key]?.toString()
        }
    }
    
    /**
     * Gets a string configuration with a default value
     * 
     * @param key The configuration key to look up
     * @param defaultValue The default value to return if not found
     * @return The value as a String or the default value
     */
    fun getString(key: String, defaultValue: String): String {
        return getString(key) ?: defaultValue
    }
    
    /**
     * Maps a non-standard email to its standard counterpart if a mapping exists
     * 
     * @param email The email address to map
     * @return The mapped email address or the original if no mapping exists
     */
    fun mapEmail(email: String): String {
        if (!initialized) {
            initialize()
        }
        
        return lock.read {
            checkFileChanged()
            
            @Suppress("UNCHECKED_CAST")
            val mappings = config[EMAIL_MAPPINGS_KEY] as? Map<String, String> ?: emptyMap()
            mappings[email] ?: email
        }
    }
    
    /**
     * Gets all email mappings
     * 
     * @return A copy of the email mappings
     */
    fun getAllEmailMappings(): Map<String, String> {
        if (!initialized) {
            initialize()
        }
        
        return lock.read {
            checkFileChanged()
            
            @Suppress("UNCHECKED_CAST")
            val mappings = config[EMAIL_MAPPINGS_KEY] as? Map<String, String> ?: emptyMap()
            HashMap(mappings)
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
        if (!initialized) {
            initialize()
        }
        
        lock.write {
            checkFileChanged()
            
            @Suppress("UNCHECKED_CAST")
            val currentMappings = config[EMAIL_MAPPINGS_KEY] as? MutableMap<String, String>
                ?: config[EMAIL_MAPPINGS_KEY] as? Map<String, String>
                ?: HashMap<String, String>()
            
            // Create a new mutable map with the current mappings
            val updatedMappings = HashMap(currentMappings)
            updatedMappings[fromEmail] = toEmail
            
            // Update the config
            config[EMAIL_MAPPINGS_KEY] = updatedMappings
            
            if (save) {
                return saveConfig()
            }
        }
        
        return true
    }
    
    /**
     * Removes an email mapping
     * 
     * @param email The non-standard email to remove mapping for
     * @param save Whether to save the change to the file
     * @return True if the mapping was removed successfully
     */
    fun removeEmailMapping(email: String, save: Boolean = true): Boolean {
        if (!initialized) {
            initialize()
        }
        
        lock.write {
            checkFileChanged()
            
            @Suppress("UNCHECKED_CAST")
            val currentMappings = config[EMAIL_MAPPINGS_KEY] as? MutableMap<String, String>
                ?: config[EMAIL_MAPPINGS_KEY] as? Map<String, String>
                ?: HashMap<String, String>()
            
            // Create a new mutable map with the current mappings
            val updatedMappings = HashMap(currentMappings)
            val removed = updatedMappings.remove(email) != null
            
            // Update the config
            config[EMAIL_MAPPINGS_KEY] = updatedMappings
            
            if (removed && save) {
                return saveConfig()
            }
            
            return removed
        }
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
        if (!initialized) {
            initialize()
        }
        
        lock.write {
            checkFileChanged()
            config[key] = value
            
            if (save) {
                return saveConfig()
            }
        }
        
        return true
    }
    
    /**
     * Saves the current configuration to the JSON file
     * 
     * @return True if the configuration was saved successfully
     */
    fun saveConfig(): Boolean {
        val file = File(configFilePath)
        
        return try {
            // Ensure parent directories exist
            file.parentFile?.mkdirs()
            
            // Write JSON with pretty printing
            val json = gson.toJson(config)
            file.writeText(json)
            
            // Update last modified time
            lastModifiedTime = file.lastModified()
            val savedMsg = "Successfully saved configuration to file"
            LOG?.info(savedMsg) ?: debugLogger?.invoke(savedMsg)
            true
        } catch (e: Exception) {
            val errorMsg = "Failed to save configuration: ${e.message}"
            LOG?.error(errorMsg) ?: debugLogger?.invoke(errorMsg)
            false
        }
    }
    
    /**
     * Gets the entire configuration as a Map
     * 
     * @return A copy of the configuration
     */
    fun getAllConfig(): Map<String, Any> {
        if (!initialized) {
            initialize()
        }
        
        return lock.read {
            checkFileChanged()
            HashMap(config)
        }
    }
    
    /**
     * Checks if the configuration file has changed and reloads if necessary
     */
    private fun checkFileChanged() {
        val file = File(configFilePath)
        if (file.exists() && file.lastModified() > lastModifiedTime) {
            lock.write {
                if (file.lastModified() > lastModifiedTime) {
                    val reloadMsg = "Config file has changed, reloading configuration"
                    LOG?.info(reloadMsg) ?: debugLogger?.invoke(reloadMsg)
                    loadConfigFromFile(file)
                }
            }
        }
    }
}