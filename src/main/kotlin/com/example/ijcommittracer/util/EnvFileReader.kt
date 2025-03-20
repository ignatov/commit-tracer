package com.example.ijcommittracer.util

import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Utility class for reading environment variables from .env file.
 * This allows developers to store API tokens in a local file rather than 
 * having to enter them in the UI each time.
 * 
 * This implementation is thread-safe, with proper synchronization for
 * both instance creation and property access.
 */
class EnvFileReader(private val envFilePath: String) {
    private val LOG = logger<EnvFileReader>()
    private val envProperties = Properties()
    private var initialized = false
    private val lock = ReentrantReadWriteLock()
    private var lastModifiedTime: Long = 0
    
    companion object {
        // Use ConcurrentHashMap for thread-safe instance storage
        private val instances = ConcurrentHashMap<String, EnvFileReader>()
        
        /**
         * Gets an instance of EnvFileReader for the given file path.
         * This method is thread-safe and will always return the same instance
         * for the same file path.
         * 
         * @param filePath The absolute path to the .env file
         * @return An EnvFileReader instance for the file
         */
        fun getInstance(filePath: String): EnvFileReader {
            // computeIfAbsent is thread-safe and avoids race conditions
            return instances.computeIfAbsent(filePath) {
                EnvFileReader(filePath).apply { initialize() }
            }
        }
    }
    
    /**
     * Loads properties from .env file.
     * This method is synchronized to prevent multiple threads from 
     * initializing the properties simultaneously.
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
                LOG.info("Looking for .env file at: ${envFile.absolutePath}")
                
                if (envFile.exists()) {
                    loadPropertiesFromFile(envFile)
                } else {
                    LOG.info("No .env file found at ${envFile.absolutePath}, will use credential store")
                    // Try to create a sample .env file for the user
                    try {
                        val parentDir = envFile.parentFile
                        val sampleEnvFile = File(parentDir, ".env.sample")
                        if (!sampleEnvFile.exists()) {
                            sampleEnvFile.writeText("""
                                # Sample .env file for Commit Tracer
                                # Copy this file to .env and fill in your API tokens
                                
                                # YouTrack API Configuration
                                YOUTRACK_API_TOKEN=your_youtrack_token_here
                                YOUTRACK_API_URL=https://youtrack.jetbrains.com/api
                                
                                # HiBob API Configuration
                                HIBOB_API_TOKEN=your_hibob_token_here
                                HIBOB_API_URL=https://api.hibob.com/v1
                            """.trimIndent())
                            LOG.info("Created sample .env file at ${sampleEnvFile.absolutePath}")
                        }
                    } catch (e: Exception) {
                        LOG.debug("Failed to create sample .env file", e)
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to load .env file", e)
            } finally {
                initialized = true
            }
        }
    }
    
    /**
     * Loads properties from the specified file and updates the last modified time.
     */
    private fun loadPropertiesFromFile(file: File) {
        file.inputStream().use {
            envProperties.clear()
            envProperties.load(it)
        }
        lastModifiedTime = file.lastModified()
        LOG.info("Successfully loaded .env file with ${envProperties.size} properties")
        
        // Log the keys (but not the values) for debugging
        if (envProperties.isNotEmpty()) {
            LOG.info("Found properties: ${envProperties.keys.joinToString(", ")}")
        }
    }
    
    /**
     * Gets a property from the .env file.
     * Thread-safe implementation that ensures proper initialization and checks for file changes.
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
     * Thread-safe implementation.
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
     * Thread-safe implementation that ensures proper initialization.
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
                    LOG.info("Env file has changed, reloading properties")
                    loadPropertiesFromFile(file)
                }
            }
        }
    }
}