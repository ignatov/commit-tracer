package com.example.ijcommittracer.util

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Maps non-standard email addresses to their standard counterparts
 * Uses a JSON configuration file to define mappings
 * 
 * Example of email_mappings.json:
 * {
 *   "abc@gmail.com": "abc@work.com",
 *   "xyz@hotmail.com": "xyz@work.com"
 * }
 */
class EmailMapper(private val mappingFilePath: String) {
    private val LOG = logger<EmailMapper>()
    private val emailMappings = ConcurrentHashMap<String, String>()
    private var initialized = false
    private val lock = ReentrantReadWriteLock()
    private var lastModifiedTime: Long = 0
    private val gson = Gson()
    
    companion object {
        private val instances = ConcurrentHashMap<String, EmailMapper>()
        
        /**
         * Gets an instance of EmailMapper for the given file path.
         * 
         * @param filePath The absolute path to the mapping file
         * @return An EmailMapper instance for the file
         */
        fun getInstance(filePath: String): EmailMapper {
            return instances.computeIfAbsent(filePath) {
                EmailMapper(filePath).apply { initialize() }
            }
        }
        
        /**
         * Creates a default mappings file in the specified directory
         * 
         * @param directory The directory to create the file in
         * @param fileName The name of the file (default: email_mappings.json)
         * @return True if the file was created, false otherwise
         */
        fun createDefaultMappingsFile(
            directory: String, 
            fileName: String = "email_mappings.json"
        ): Boolean {
            val file = File(directory, fileName)
            if (file.exists()) return false
            
            val defaultMappings = mapOf(
                "personal@gmail.com" to "work@company.com",
                "old@legacy.com" to "new@company.com"
            )
            
            try {
                val json = Gson().toJson(defaultMappings)
                file.writeText(json)
                return true
            } catch (e: Exception) {
                return false
            }
        }
    }
    
    /**
     * Initialize by loading mappings from the JSON file
     */
    private fun initialize() {
        if (initialized) return
        
        lock.write {
            if (initialized) return@write
            
            try {
                val mappingFile = File(mappingFilePath)
                LOG.info("Looking for email mappings file at: ${mappingFile.absolutePath}")
                
                if (mappingFile.exists()) {
                    loadMappingsFromFile(mappingFile)
                } else {
                    LOG.info("No email mappings file found at ${mappingFile.absolutePath}")
                    // Create parent directories if they don't exist
                    mappingFile.parentFile?.mkdirs()
                    
                    // Try to create a sample mapping file
                    try {
                        val parentDir = mappingFile.parentFile
                        createDefaultMappingsFile(parentDir.absolutePath, mappingFile.name)
                        LOG.info("Created default email mappings file at ${mappingFile.absolutePath}")
                    } catch (e: Exception) {
                        LOG.warn("Failed to create default mappings file", e)
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to load email mappings file", e)
            } finally {
                initialized = true
            }
        }
    }
    
    /**
     * Loads email mappings from the specified JSON file
     */
    private fun loadMappingsFromFile(file: File) {
        try {
            val json = file.readText()
            val mapType = object : TypeToken<Map<String, String>>() {}.type
            val loadedMappings: Map<String, String> = gson.fromJson(json, mapType)
            
            emailMappings.clear()
            emailMappings.putAll(loadedMappings)
            
            lastModifiedTime = file.lastModified()
            LOG.info("Successfully loaded email mappings file with ${emailMappings.size} entries")
        } catch (e: JsonSyntaxException) {
            LOG.error("Invalid JSON in email mappings file: ${e.message}")
        } catch (e: Exception) {
            LOG.error("Error loading email mappings file: ${e.message}")
        }
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
            emailMappings[email] ?: email
        }
    }
    
    /**
     * Gets all email mappings as a Map
     * 
     * @return A copy of the email mappings
     */
    fun getAllMappings(): Map<String, String> {
        if (!initialized) {
            initialize()
        }
        
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
    fun addMapping(fromEmail: String, toEmail: String, save: Boolean = true): Boolean {
        if (!initialized) {
            initialize()
        }
        
        lock.write {
            checkFileChanged()
            emailMappings[fromEmail] = toEmail
            
            if (save) {
                return saveMappings()
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
    fun removeMapping(email: String, save: Boolean = true): Boolean {
        if (!initialized) {
            initialize()
        }
        
        lock.write {
            checkFileChanged()
            val removed = emailMappings.remove(email) != null
            
            if (removed && save) {
                return saveMappings()
            }
            
            return removed
        }
    }
    
    /**
     * Saves the current mappings to the JSON file
     * 
     * @return True if the mappings were saved successfully
     */
    fun saveMappings(): Boolean {
        val file = File(mappingFilePath)
        
        return try {
            // Ensure parent directories exist
            file.parentFile?.mkdirs()
            
            // Write JSON with pretty printing
            val json = gson.toJson(emailMappings)
            file.writeText(json)
            
            // Update last modified time
            lastModifiedTime = file.lastModified()
            LOG.info("Successfully saved email mappings to file")
            true
        } catch (e: Exception) {
            LOG.error("Failed to save email mappings: ${e.message}")
            false
        }
    }
    
    /**
     * Checks if the mapping file has changed and reloads if necessary
     */
    private fun checkFileChanged() {
        val file = File(mappingFilePath)
        if (file.exists() && file.lastModified() > lastModifiedTime) {
            lock.write {
                if (file.lastModified() > lastModifiedTime) {
                    LOG.info("Email mappings file has changed, reloading mappings")
                    loadMappingsFromFile(file)
                }
            }
        }
    }
}