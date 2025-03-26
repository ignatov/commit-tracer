package com.example.ijcommittracer.util

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class EmailMapperTest {
    private lateinit var tempDir: File
    private lateinit var mappingFile: File
    private lateinit var emailMapper: EmailMapper
    
    @Before
    fun setUp() {
        // Create a temporary directory for testing
        tempDir = createTempDir("emailmapper_test")
        mappingFile = File(tempDir, "test_email_mappings.json")
        
        // Create a sample mappings file
        val sampleJson = """
            {
                "personal@gmail.com": "work@company.com",
                "old@legacy.com": "new@company.com"
            }
        """.trimIndent()
        mappingFile.writeText(sampleJson)
        
        // Initialize the mapper
        emailMapper = EmailMapper(mappingFile.absolutePath)
    }
    
    @After
    fun tearDown() {
        // Clean up temp files
        tempDir.deleteRecursively()
    }
    
    @Test
    fun testLoadMappings() {
        // Get all mappings and verify they're loaded correctly
        val mappings = emailMapper.getAllMappings()
        assertEquals(2, mappings.size)
        assertEquals("work@company.com", mappings["personal@gmail.com"])
        assertEquals("new@company.com", mappings["old@legacy.com"])
    }
    
    @Test
    fun testMapEmail() {
        // Test mapping emails
        assertEquals("work@company.com", emailMapper.mapEmail("personal@gmail.com"))
        assertEquals("new@company.com", emailMapper.mapEmail("old@legacy.com"))
        
        // Test unmapped email returns the original
        val unmappedEmail = "unmapped@example.com"
        assertEquals(unmappedEmail, emailMapper.mapEmail(unmappedEmail))
    }
    
    @Test
    fun testAddMapping() {
        // Add a new mapping
        val from = "another@gmail.com"
        val to = "another@company.com"
        assertTrue(emailMapper.addMapping(from, to))
        
        // Verify it was added
        assertEquals(to, emailMapper.mapEmail(from))
        
        // Verify it was saved to the file
        val fileContent = mappingFile.readText()
        assertTrue(fileContent.contains(from))
        assertTrue(fileContent.contains(to))
    }
    
    @Test
    fun testRemoveMapping() {
        // Test removing an existing mapping
        val from = "personal@gmail.com"
        assertTrue(emailMapper.removeMapping(from))
        
        // Verify it was removed
        assertEquals(from, emailMapper.mapEmail(from))
        
        // Verify it was removed from the file
        val fileContent = mappingFile.readText()
        assertFalse(fileContent.contains("\"$from\""))
    }
    
    @Test
    fun testCreateDefaultMappingsFile() {
        // Create a new temp directory
        val newTempDir = createTempDir("emailmapper_default_test")
        try {
            // Create a default mappings file
            val created = EmailMapper.createDefaultMappingsFile(newTempDir.absolutePath)
            assertTrue(created)
            
            // Verify the file exists and contains expected content
            val defaultFile = File(newTempDir, "email_mappings.json")
            assertTrue(defaultFile.exists())
            
            val content = defaultFile.readText()
            assertTrue(content.contains("personal@gmail.com"))
            assertTrue(content.contains("work@company.com"))
        } finally {
            newTempDir.deleteRecursively()
        }
    }
    
    @Test
    fun testFileChangedReloading() {
        // Modify the file externally
        val newJson = """
            {
                "personal@gmail.com": "work@company.com",
                "old@legacy.com": "new@company.com",
                "added@example.com": "added@company.com"
            }
        """.trimIndent()
        mappingFile.writeText(newJson)
        
        // Force a file check
        assertEquals("added@company.com", emailMapper.mapEmail("added@example.com"))
        
        // Verify all mappings are updated
        val mappings = emailMapper.getAllMappings()
        assertEquals(3, mappings.size)
        assertTrue(mappings.containsKey("added@example.com"))
    }
}