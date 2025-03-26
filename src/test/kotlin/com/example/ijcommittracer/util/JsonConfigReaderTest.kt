package com.example.ijcommittracer.util

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class JsonConfigReaderTest {
    private lateinit var tempDir: File
    private lateinit var configFile: File
    private lateinit var configReader: JsonConfigReader
    
    @Before
    fun setUp() {
        // Create a temporary directory for testing
        tempDir = createTempDir("jsonconfig_test")
        configFile = File(tempDir, ".env.json")
        
        // Create a sample config file
        val sampleJson = """
            {
                "youtrackToken": "test-youtrack-token",
                "youtrackUrl": "https://youtrack.test.com/api",
                "hibobToken": "test-hibob-token",
                "hibobApiUrl": "https://api.hibob.test.com/v1",
                "emailMappings": {
                    "personal@gmail.com": "work@company.com",
                    "old@legacy.com": "new@company.com"
                }
            }
        """.trimIndent()
        configFile.writeText(sampleJson)
        
        // Initialize the config reader
        configReader = JsonConfigReader(configFile.absolutePath)
    }
    
    @After
    fun tearDown() {
        // Clean up temp files
        tempDir.deleteRecursively()
    }
    
    @Test
    fun testReadApiTokens() {
        // Verify YouTrack settings
        assertEquals("test-youtrack-token", configReader.getString(JsonConfigReader.YOUTRACK_TOKEN_KEY))
        assertEquals("https://youtrack.test.com/api", configReader.getString(JsonConfigReader.YOUTRACK_URL_KEY))
        
        // Verify HiBob settings
        assertEquals("test-hibob-token", configReader.getString(JsonConfigReader.HIBOB_TOKEN_KEY))
        assertEquals("https://api.hibob.test.com/v1", configReader.getString(JsonConfigReader.HIBOB_URL_KEY))
    }
    
    @Test
    fun testDefaultValues() {
        // Delete the config file to test defaults
        configFile.delete()
        
        // Create a new reader (this should use defaults)
        val newReader = JsonConfigReader(configFile.absolutePath)
        
        // Test default values
        assertEquals(JsonConfigReader.DEFAULT_YOUTRACK_URL, 
            newReader.getString(JsonConfigReader.YOUTRACK_URL_KEY, JsonConfigReader.DEFAULT_YOUTRACK_URL))
        assertEquals(JsonConfigReader.DEFAULT_HIBOB_URL, 
            newReader.getString(JsonConfigReader.HIBOB_URL_KEY, JsonConfigReader.DEFAULT_HIBOB_URL))
        
        // Verify default file creation
        assertTrue(configFile.exists())
    }
    
    @Test
    fun testEmailMappings() {
        // Get all mappings and verify they're loaded correctly
        val mappings = configReader.getAllEmailMappings()
        assertEquals(2, mappings.size)
        assertEquals("work@company.com", mappings["personal@gmail.com"])
        assertEquals("new@company.com", mappings["old@legacy.com"])
    }
    
    @Test
    fun testMapEmail() {
        // Test mapping emails
        assertEquals("work@company.com", configReader.mapEmail("personal@gmail.com"))
        assertEquals("new@company.com", configReader.mapEmail("old@legacy.com"))
        
        // Test unmapped email returns the original
        val unmappedEmail = "unmapped@example.com"
        assertEquals(unmappedEmail, configReader.mapEmail(unmappedEmail))
    }
    
    @Test
    fun testAddEmailMapping() {
        // Add a new mapping
        val from = "another@gmail.com"
        val to = "another@company.com"
        assertTrue(configReader.addEmailMapping(from, to))
        
        // Verify it was added
        assertEquals(to, configReader.mapEmail(from))
        
        // Verify it was saved to the file
        val fileContent = configFile.readText()
        assertTrue(fileContent.contains(from))
        assertTrue(fileContent.contains(to))
    }
    
    @Test
    fun testRemoveEmailMapping() {
        // Test removing an existing mapping
        val from = "personal@gmail.com"
        assertTrue(configReader.removeEmailMapping(from))
        
        // Verify it was removed
        assertEquals(from, configReader.mapEmail(from))
        
        // Verify it was removed from the file
        val fileContent = configFile.readText()
        assertFalse(fileContent.contains("\"$from\""))
    }
    
    @Test
    fun testUpdateValue() {
        // Update a value
        val newToken = "updated-youtrack-token"
        assertTrue(configReader.updateValue(JsonConfigReader.YOUTRACK_TOKEN_KEY, newToken))
        
        // Verify it was updated
        assertEquals(newToken, configReader.getString(JsonConfigReader.YOUTRACK_TOKEN_KEY))
        
        // Verify it was saved to the file
        val fileContent = configFile.readText()
        assertTrue(fileContent.contains(newToken))
    }
    
    @Test
    fun testFileChangedReloading() {
        // Modify the file externally
        val newJson = """
            {
                "youtrackToken": "test-youtrack-token",
                "youtrackUrl": "https://youtrack.test.com/api",
                "hibobToken": "test-hibob-token",
                "hibobApiUrl": "https://api.hibob.test.com/v1",
                "emailMappings": {
                    "personal@gmail.com": "work@company.com",
                    "old@legacy.com": "new@company.com",
                    "added@example.com": "added@company.com"
                }
            }
        """.trimIndent()
        configFile.writeText(newJson)
        
        // Force a file check
        assertEquals("added@company.com", configReader.mapEmail("added@example.com"))
        
        // Verify all mappings are updated
        val mappings = configReader.getAllEmailMappings()
        assertEquals(3, mappings.size)
        assertTrue(mappings.containsKey("added@example.com"))
    }
    
    @Test
    fun testCreateDefaultConfigFile() {
        // Create a new temp directory
        val newTempDir = createTempDir("jsonconfig_default_test")
        try {
            // Create a default config file
            val created = JsonConfigReader.createDefaultConfigFile(newTempDir.absolutePath)
            assertTrue(created)
            
            // Verify the file exists
            val defaultFile = File(newTempDir, JsonConfigReader.DEFAULT_CONFIG_FILENAME)
            assertTrue(defaultFile.exists())
            
            // Verify it contains expected content
            val content = defaultFile.readText()
            assertTrue(content.contains(JsonConfigReader.YOUTRACK_TOKEN_KEY))
            assertTrue(content.contains(JsonConfigReader.HIBOB_TOKEN_KEY))
            assertTrue(content.contains(JsonConfigReader.EMAIL_MAPPINGS_KEY))
        } finally {
            newTempDir.deleteRecursively()
        }
    }
}