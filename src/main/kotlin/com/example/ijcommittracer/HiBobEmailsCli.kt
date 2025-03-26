package com.example.ijcommittracer

import com.example.ijcommittracer.api.HiBobApiClient
import com.example.ijcommittracer.util.SimpleJsonConfigReader
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime

/**
 * A specialized HiBob CLI tool that lists all user emails and writes them to a file.
 */
object HiBobEmailsCli {
    // Debug flag for verbose output
    private var isDebugMode = false

    @JvmStatic
    fun main(args: Array<String>) {
        println("HiBob Emails CLI - ${LocalDateTime.now()}")
        println("-----------------------------------------------")
        
        // Process arguments
        val configFile = args.firstOrNull { it.endsWith(".json") || it.endsWith(".env.json") }
        val outputFile = args.firstOrNull { it.startsWith("--output=") }?.substringAfter("--output=") ?: "emails.txt"
        isDebugMode = args.any { it == "--debug" || it == "-d" }
        val tokenArg = args.firstOrNull { it.startsWith("token=") }?.substringAfter("token=")
        val baseUrl = args.firstOrNull { it.startsWith("http") } 
            ?: SimpleJsonConfigReader.DEFAULT_HIBOB_URL
        
        println("Output will be saved to: $outputFile")
        
        // Get token from arguments, config file, or environment variables
        val (token, configReader) = getToken(tokenArg, configFile)
        
        // Validate that we have a non-empty token
        if (token.isBlank()) {
            println("Error: HiBob API token is empty or could not be read properly.")
            println("Usage: java -jar hibob-emails-cli.jar [/path/to/.env.json] [--output=emails.txt] [token=XXX]")
            return
        }
        
        println("\nUsing HiBob API URL: $baseUrl")
        
        // Show email mappings if available
        val mappings = configReader?.getAllEmailMappings() ?: emptyMap()
        if (mappings.isNotEmpty()) {
            println("Loaded ${mappings.size} email mappings from configuration")
        }
        
        // Create API client
        val apiClient = HiBobApiClient(baseUrl, token)
        
        // Fetch and export emails
        runBlocking {
            try {
                println("\nFetching all employees...")
                
                val debugLogger: (String) -> Unit = { message ->
                    if (isDebugMode) println("[DEBUG] $message")
                }
                
                val errorLogger: (String, Throwable?) -> Unit = { message, error ->
                    println(message)
                    if (isDebugMode && error != null) {
                        error.printStackTrace()
                    }
                }
                
                // Fetch all employees
                val employeeData = apiClient.fetchEmployeeData(
                    debugLogger = debugLogger,
                    errorLogger = errorLogger
                )
                
                if (employeeData.isEmpty()) {
                    println("No employees found. Check your API token and connection.")
                    return@runBlocking
                }
                
                println("Found ${employeeData.size} employees")
                
                // Extract emails and sort them
                val emails = employeeData.keys.sorted()
                
                // Create output file and write emails
                val outputPath = Paths.get(outputFile).toAbsolutePath()
                File(outputPath.toString()).apply {
                    parentFile?.mkdirs() // Create parent directories if they don't exist
                    writeText(emails.joinToString("\n"))
                }
                
                println("\nSuccessfully exported ${emails.size} email addresses to $outputPath")
                println("\nFirst 10 emails (sample):")
                emails.take(10).forEach { println("- $it") }
                
            } catch (e: Exception) {
                println("\nError fetching employee data: ${e.message}")
                if (isDebugMode) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * Gets the HiBob API token from the specified sources
     * Returns a pair of the token and the config reader used (if any)
     */
    private fun getToken(tokenArg: String?, configFile: String?): Pair<String, SimpleJsonConfigReader?> {
        if (!tokenArg.isNullOrBlank()) {
            println("Using token from command line arguments")
            return Pair(tokenArg, null)
        }
        
        if (configFile != null) {
            println("Reading token from $configFile")
            val reader = SimpleJsonConfigReader.getInstance(File(configFile).absolutePath)
            return Pair(reader.getString(SimpleJsonConfigReader.HIBOB_TOKEN_KEY, ""), reader)
        }
        
        if (System.getenv(SimpleJsonConfigReader.HIBOB_TOKEN_KEY) != null) {
            println("Using token from environment variables")
            return Pair(System.getenv(SimpleJsonConfigReader.HIBOB_TOKEN_KEY) ?: "", null)
        }
        
        // Try to find config in default locations
        val userHome = System.getProperty("user.home")
        val defaultConfigPath = File(userHome, ".commitmapper/${SimpleJsonConfigReader.DEFAULT_CONFIG_FILENAME}").absolutePath
        val currentDirConfigPath = File(SimpleJsonConfigReader.DEFAULT_CONFIG_FILENAME).absolutePath
        
        // Try home directory first
        var reader = SimpleJsonConfigReader.getInstance(defaultConfigPath)
        var token = reader.getString(SimpleJsonConfigReader.HIBOB_TOKEN_KEY)
        
        // If not found, try current directory
        if (token.isNullOrBlank()) {
            reader = SimpleJsonConfigReader.getInstance(currentDirConfigPath)
            token = reader.getString(SimpleJsonConfigReader.HIBOB_TOKEN_KEY)
        }
        
        if (!token.isNullOrBlank()) {
            println("Using token from configuration file")
            return Pair(token, reader)
        } else {
            return Pair("", null)
        }
    }
}

/**
 * Simple main function to run the HiBobEmailsCli
 */
fun main(args: Array<String>) {
    // Add debug flag automatically for this session if not already provided
    val argsWithDebug = args.toMutableList()
    if (!argsWithDebug.contains("--debug") && !argsWithDebug.contains("-d")) {
        argsWithDebug.add("--debug")
    }
    HiBobEmailsCli.main(argsWithDebug.toTypedArray())
}