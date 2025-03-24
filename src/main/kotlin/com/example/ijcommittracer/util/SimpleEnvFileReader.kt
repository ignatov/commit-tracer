package com.example.ijcommittracer.util

import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Simplified utility class for reading environment variables from .env file
 * without any IntelliJ dependencies. Suitable for use in standalone tools.
 */
class SimpleEnvFileReader(private val envFilePath: String) {
    private val envProperties = Properties()
    private var initialized = false
    private val lock = ReentrantReadWriteLock()
    private var lastModifiedTime: Long = 0
    private val debug = System.getProperty("debug.env", "false").equals("true", ignoreCase = true)
    
    companion object {
        // Use ConcurrentHashMap for thread-safe instance storage
        private val instances = ConcurrentHashMap<String, SimpleEnvFileReader>()
        
        /**
         * Gets an instance of SimpleEnvFileReader for the given file path.
         * 
         * @param filePath The absolute path to the .env file
         * @return A SimpleEnvFileReader instance for the file
         */
        fun getInstance(filePath: String): SimpleEnvFileReader {
            // computeIfAbsent is thread-safe and avoids race conditions
            return instances.computeIfAbsent(filePath) {
                SimpleEnvFileReader(filePath).apply { initialize() }
            }
        }
    }
    
    /**
     * Loads properties from .env file.
     */
    private fun initialize() {
        // Fast check to avoid acquiring the lock unnecessarily
        if (initialized) return
        
        // Acquire write lock for initialization
        lock.write {
            // Double-check inside the lock
            if (initialized) return
            
            try {
                val envFile = File(envFilePath)
                if (debug) println("[DEBUG] Looking for .env file at: ${envFile.absolutePath}")
                
                if (envFile.exists()) {
                    loadPropertiesFromFile(envFile)
                } else {
                    if (debug) println("[DEBUG] No .env file found at ${envFile.absolutePath}")
                }
            } catch (e: Exception) {
                if (debug) {
                    println("[DEBUG] Failed to load .env file: ${e.message}")
                    e.printStackTrace()
                }
            } finally {
                initialized = true
            }
        }
    }
    
    /**
     * Loads properties from the specified file and updates the last modified time.
     */
    private fun loadPropertiesFromFile(file: File) {
        envProperties.clear()
        
        // Load properties line by line to handle various formats
        file.useLines { lines ->
            lines.forEach { line ->
                // Skip comments and empty lines
                if (line.isBlank() || line.trim().startsWith("#")) {
                    return@forEach
                }
                
                // Handle both KEY=VALUE and KEY="VALUE" formats
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    var value = parts[1].trim()
                    
                    // Remove quotes if present
                    if ((value.startsWith("\"") && value.endsWith("\"")) || 
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length - 1)
                    }
                    
                    envProperties[key] = value
                }
            }
        }
        
        lastModifiedTime = file.lastModified()
        
        if (debug) {
            println("[DEBUG] Successfully loaded .env file with ${envProperties.size} properties")
            // Log the keys (but not the values) for debugging
            if (envProperties.isNotEmpty()) {
                println("[DEBUG] Found properties: ${envProperties.keys.joinToString(", ")}")
            }
        }
    }
    
    /**
     * Gets a property from the .env file.
     * 
     * @param key The property key to look up
     * @return The property value or null if not found
     */
    fun getProperty(key: String): String? {
        // Initialize if needed (will acquire write lock internally)
        if (!initialized) {
            initialize()
        }
        
        // Use read lock for property access
        return lock.read {
            checkFileChanged()
            envProperties.getProperty(key)
        }
    }
    
    /**
     * Gets a property from the .env file with a default value.
     * 
     * @param key The property key to look up
     * @param defaultValue The default value to return if the property is not found
     * @return The property value or the default value if not found
     */
    fun getProperty(key: String, defaultValue: String): String {
        return getProperty(key) ?: defaultValue
    }
    
    /**
     * Checks if a property exists in the .env file.
     * 
     * @param key The property key to check
     * @return true if the property exists, false otherwise
     */
    fun hasProperty(key: String): Boolean {
        // Initialize if needed (will acquire write lock internally)
        if (!initialized) {
            initialize()
        }
        
        // Use read lock for property access
        return lock.read {
            checkFileChanged()
            envProperties.containsKey(key)
        }
    }
    
    /**
     * Checks if the .env file has been modified since it was last loaded.
     * If it has, reload the properties.
     */
    private fun checkFileChanged() {
        val file = File(envFilePath)
        if (file.exists() && file.lastModified() > lastModifiedTime) {
            // Release read lock and acquire write lock to reload properties
            lock.write {
                // Check again inside write lock to avoid race conditions
                if (file.lastModified() > lastModifiedTime) {
                    if (debug) println("[DEBUG] Env file has changed, reloading properties")
                    loadPropertiesFromFile(file)
                }
            }
        }
    }
}