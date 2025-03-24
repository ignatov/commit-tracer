package com.example.ijcommittracer

import com.example.ijcommittracer.api.HiBobApiClient
import com.example.ijcommittracer.models.SimpleEmployeeInfo
import com.example.ijcommittracer.services.EmployeeInfo
import com.example.ijcommittracer.util.SimpleEnvFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

/**
 * Command-line interface for HiBob API interactions.
 * This class provides a standalone utility for fetching and displaying
 * employee information from the HiBob API.
 */
object HiBobCli {
    private const val HIBOB_API_TOKEN_KEY = "HIBOB_API_TOKEN"
    private const val HIBOB_API_URL_KEY = "HIBOB_API_URL"
    private const val DEFAULT_HIBOB_API_URL = "https://api.hibob.com/v1"
    
    // Debug flag for verbose output
    private var isDebugMode = false

    /**
     * Main entry point for CLI application
     */
    @JvmStatic
    fun main(args: Array<String>) {
        println("HiBob CLI - ${LocalDateTime.now()}")
        println("-----------------------------------------------")

        // Process arguments
        val email = args.firstOrNull { it.contains("@") }
        val baseUrl = args.firstOrNull { it.startsWith("http") } ?: DEFAULT_HIBOB_API_URL
        val tokenArg = args.firstOrNull { it.startsWith("token=") }?.substringAfter("token=")
        val envFile = args.firstOrNull { it.endsWith(".env") }
        isDebugMode = args.any { it == "--debug" || it == "-d" }
        
        // Get token from arguments, .env file, or environment variables
        val token = when {
            !tokenArg.isNullOrBlank() -> {
                println("Using token from command line arguments")
                tokenArg
            }
            envFile != null -> {
                println("Reading token from $envFile")
                val reader = SimpleEnvFileReader.getInstance(File(envFile).absolutePath)
                reader.getProperty(HIBOB_API_TOKEN_KEY) ?: ""
            }
            System.getenv(HIBOB_API_TOKEN_KEY) != null -> {
                println("Using token from environment variables")
                System.getenv(HIBOB_API_TOKEN_KEY) ?: ""
            }
            else -> {
                println("No HiBob token found. Please provide one via command line (token=XXX), .env file, or environment variable.")
                println("Usage: java -jar hibob-cli.jar [email@example.com] [https://api.hibob.com/v1] [token=XXX] [/path/to/.env] [--debug|-d]")
                return
            }
        }
        
        // Validate that we have a non-empty token
        if (token.isBlank()) {
            println("Error: HiBob API token is empty or could not be read properly.")
            return
        }

        println("\nUsing HiBob API URL: $baseUrl")
        
        // Create an instance of our API client
        val apiClient = HiBobApiClient(baseUrl, token)
        
        // Run in a blocking coroutine to fetch data asynchronously
        runBlocking {
            try {
                if (email != null) {
                    // Fetch single employee by email
                    val employee = fetchEmployeeByEmail(email, apiClient)
                    if (employee != null) {
                        println("\nEmployee information for $email:")
                        printEmployee(employee)
                    } else {
                        println("\nNo employee found with email $email")
                    }
                } else {
                    // Fetch all employees
                    println("\nFetching all employees...")
                    val employees = fetchAllEmployees(apiClient)
                    println("Found ${employees.size} employees")
                    
                    if (employees.isNotEmpty()) {
                        println("\nFirst 10 employees (or fewer if less available):")
                        employees.take(10).forEachIndexed { index, employee -> 
                            println("\nEmployee #${index + 1}:")
                            printEmployee(employee)
                        }
                        
                        // Print statistics
                        println("\nEmployee Statistics:")
                        val teams = employees.groupBy { it.team }
                        println("- Teams (${teams.size}): ${teams.keys.sorted().joinToString(", ")}")
                        println("- Titles: ${employees.map { it.title }.distinct().size} unique titles")
                        println("- Managers: ${employees.map { it.manager }.filter { it.isNotEmpty() }.distinct().size} managers")
                        println("- Sites: ${employees.mapNotNull { it.site }.distinct().size} office locations")
                    }
                }
            } catch (e: Exception) {
                println("\nError fetching employee data: ${e.message}")
                if (isDebugMode) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * Prints employee information in a formatted way
     */
    private fun printEmployee(employee: SimpleEmployeeInfo) {
        println("- ID: ${employee.id}")
        println("- Name: ${employee.name}")
        println("- Email: ${employee.email}")
        println("- Team: ${employee.team}")
        println("- Title: ${employee.title}")
        println("- Site: ${employee.site ?: "Not specified"}")
        println("- Manager: ${employee.manager.ifEmpty { "None" }}")
        println("- Manager Email: ${employee.managerEmail ?: "None"}")
        println("- Start Date: ${employee.startDate ?: "Not specified"}")
        println("- Tenure: ${employee.tenure ?: "Not specified"}")
        println("- Is Manager: ${employee.isManager}")
    }
    
    /**
     * Fetches a single employee by email using the shared API client
     */
    private suspend fun fetchEmployeeByEmail(
        email: String,
        apiClient: HiBobApiClient
    ): SimpleEmployeeInfo? = withContext(Dispatchers.IO) {
        val debugLogger: (String) -> Unit = { message ->
            if (isDebugMode) println("[DEBUG] $message")
        }
        
        val errorLogger: (String, Throwable?) -> Unit = { message, error ->
            println(message)
            if (isDebugMode && error != null) {
                error.printStackTrace()
            }
        }
        
        val employee = apiClient.fetchEmployeeByEmail(email, debugLogger, errorLogger)
        return@withContext employee?.let { apiClient.convertToSimpleEmployeeInfo(it) }
    }
    
    /**
     * Fetches all employees using the shared API client
     */
    private suspend fun fetchAllEmployees(
        apiClient: HiBobApiClient
    ): List<SimpleEmployeeInfo> = withContext(Dispatchers.IO) {
        val debugLogger: (String) -> Unit = { message ->
            if (isDebugMode) println("[DEBUG] $message")
        }
        
        val errorLogger: (String, Throwable?) -> Unit = { message, error ->
            println(message)
            if (isDebugMode && error != null) {
                error.printStackTrace()
            }
        }
        
        val employees = apiClient.fetchAllEmployees(debugLogger, errorLogger)
        return@withContext employees.map { apiClient.convertToSimpleEmployeeInfo(it) }
    }
}

/**
 * Simple main function to run the HiBobCli
 */
fun main(args: Array<String>) {
    // Add debug flag automatically for this session
    val argsWithDebug = args.toMutableList()
    if (!argsWithDebug.contains("--debug") && !argsWithDebug.contains("-d")) {
        argsWithDebug.add("--debug")
    }
    HiBobCli.main(argsWithDebug.toTypedArray())
}