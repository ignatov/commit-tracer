package com.example.ijcommittracer.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * SimpleJsonConfigReader provides configuration reading capabilities 
 * for standalone tools without IntelliJ dependencies.
 * 
 * It reads a single JSON file that contains all necessary configuration:
 * - API tokens (YouTrack, HiBob)
 * - API URLs 
 * - Email mappings
 */
class SimpleJsonConfigReader(private val configFilePath: String) : Serializable {
    private val config = ConcurrentHashMap<String, Any>()
    private var initialized = false
    private val lock = ReentrantReadWriteLock()
    private var lastModifiedTime: Long = 0
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val debug = System.getProperty("debug.env", "false").equals("true", ignoreCase = true)
    
    companion object {
        private const val serialVersionUID = 1L
        
        // Use ConcurrentHashMap for thread-safe instance storage
        private val instances = ConcurrentHashMap<String, SimpleJsonConfigReader>()
        
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
         * Gets an instance of SimpleJsonConfigReader for the given file path.
         * 
         * @param filePath The absolute path to the config file
         * @return A SimpleJsonConfigReader instance for the file
         */
        fun getInstance(filePath: String): SimpleJsonConfigReader {
            // computeIfAbsent is thread-safe and avoids race conditions
            return instances.computeIfAbsent(filePath) {
                SimpleJsonConfigReader(filePath).apply { initialize() }
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
                val json = GsonBuilder().setPrettyPrinting().create().toJson(defaultConfig)
                file.writeText(json)
                return true
            } catch (e: Exception) {
                return false
            }
        }
    }
    
    /**
     * Loads configuration from JSON file.
     */
    private fun initialize() {
        // Fast check to avoid acquiring the lock unnecessarily
        if (initialized) return
        
        // Acquire write lock for initialization
        lock.write {
            // Double-check inside the lock
            if (initialized) return
            
            try {
                val configFile = File(configFilePath)
                if (debug) println("[DEBUG] Looking for config file at: ${configFile.absolutePath}")
                
                if (configFile.exists()) {
                    loadConfigFromFile(configFile)
                } else {
                    if (debug) println("[DEBUG] No config file found at ${configFile.absolutePath}")
                    
                    // Try to create a default config file
                    try {
                        val parentDir = configFile.parentFile
                        if (parentDir != null) {
                            parentDir.mkdirs()
                            createDefaultConfigFile(parentDir.absolutePath, configFile.name)
                            if (debug) println("[DEBUG] Created default config file at ${configFile.absolutePath}")
                            loadConfigFromFile(configFile)
                        }
                    } catch (e: Exception) {
                        if (debug) println("[DEBUG] Failed to create default config file: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                if (debug) {
                    println("[DEBUG] Failed to load config file: ${e.message}")
                    e.printStackTrace()
                }
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
            
            if (debug) {
                println("[DEBUG] Successfully loaded config file with ${config.size} entries")
                println("[DEBUG] Found config keys: ${config.keys.joinToString(", ")}")
            }
            
        } catch (e: JsonSyntaxException) {
            if (debug) println("[DEBUG] Invalid JSON in config file: ${e.message}")
        } catch (e: Exception) {
            if (debug) println("[DEBUG] Error loading config file: ${e.message}")
        }
    }
    
    /**
     * Gets a string value from the configuration
     * 
     * @param key The configuration key to look up
     * @return The value as a String or null if not found or wrong type
     */
    fun getString(key: String): String? {
        // Initialize if needed (will acquire write lock internally)
        if (!initialized) {
            initialize()
        }
        
        // Use read lock for property access
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
     * Checks if the config file has been modified since it was last loaded.
     * If it has, reload the configuration.
     */
    private fun checkFileChanged() {
        val file = File(configFilePath)
        if (file.exists() && file.lastModified() > lastModifiedTime) {
            // Release read lock and acquire write lock to reload properties
            lock.write {
                // Check again inside write lock to avoid race conditions
                if (file.lastModified() > lastModifiedTime) {
                    if (debug) println("[DEBUG] Config file has changed, reloading configuration")
                    loadConfigFromFile(file)
                }
            }
        }
    }
}