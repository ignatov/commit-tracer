package com.example.ijcommittracer

import com.example.ijcommittracer.models.HiBobResponse
import com.example.ijcommittracer.models.HiBobSearchRequest
import com.example.ijcommittracer.models.SimpleEmployeeInfo
import com.example.ijcommittracer.services.EmployeeInfo
import com.example.ijcommittracer.util.SimpleEnvFileReader
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Command-line interface for HiBob API interactions.
 * This class provides a standalone utility for fetching and displaying
 * employee information from the HiBob API.
 */
object HiBobCli {
    private const val HIBOB_API_TOKEN_KEY = "HIBOB_API_TOKEN"
    private const val HIBOB_API_URL_KEY = "HIBOB_API_URL"
    private const val DEFAULT_HIBOB_API_URL = "https://api.hibob.com/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
        
    // Create a lenient JSON parser that can handle malformed JSON
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

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
        val debug = args.any { it == "--debug" || it == "-d" }
        
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
        
        // Run in a blocking coroutine to fetch data asynchronously
        runBlocking {
            try {
                if (email != null) {
                    // Fetch single employee by email
                    val employee = fetchEmployeeByEmail(email, token, baseUrl, debug)
                    if (employee != null) {
                        println("\nEmployee information for $email:")
                        printEmployee(employee)
                    } else {
                        println("\nNo employee found with email $email")
                    }
                } else {
                    // Fetch all employees
                    println("\nFetching all employees...")
                    val employees = fetchAllEmployees(token, baseUrl, debug)
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
                if (debug) {
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
     * Fetches a single employee by email using POST search endpoint
     */
    private suspend fun fetchEmployeeByEmail(
        email: String, 
        token: String, 
        baseUrl: String,
        debug: Boolean = false
    ): SimpleEmployeeInfo? = withContext(Dispatchers.IO) {
        try {
            // Create search request object and convert to JSON
            val searchRequest = HiBobSearchRequest(showInactive = false, email = email)
            val requestJson = json.encodeToString(HiBobSearchRequest.serializer(), searchRequest)
            
            if (debug) {
                println("Request payload: $requestJson")
            }
            
            val requestBody = requestJson.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$baseUrl/people/search")
                .post(requestBody)
                .addHeader("authorization", "Basic $token")
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .build()
            
            println("Fetching employee with email $email...")
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                println("API request failed with status: ${response.code}")
                if (response.body != null) {
                    println("Error response: ${response.body?.string()}")
                }
                return@withContext null
            }
            
            val responseBody = response.body?.string() ?: return@withContext null
            
            if (debug) {
                println("Response body: $responseBody")
            }
            
            // Parse the response using kotlinx.serialization
            val hibobResponse = json.decodeFromString(HiBobResponse.serializer(), responseBody)
            
            if (hibobResponse.employees.isEmpty()) {
                println("No employee found with email $email")
                return@withContext null
            }
            
            // Convert to simplified model
            return@withContext SimpleEmployeeInfo.fromHiBobEmployee(hibobResponse.employees.first())
        } catch (e: Exception) {
            println("Error fetching employee: ${e.message}")
            if (debug) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }
    
    /**
     * Fetches all employees from HiBob API using the POST search endpoint
     */
    private suspend fun fetchAllEmployees(
        token: String, 
        baseUrl: String,
        debug: Boolean = false
    ): List<SimpleEmployeeInfo> = withContext(Dispatchers.IO) {
        try {
            // Create search request object and convert to JSON
            val searchRequest = HiBobSearchRequest(showInactive = false)
            val requestJson = json.encodeToString(HiBobSearchRequest.serializer(), searchRequest)
            
            if (debug) {
                println("Request payload: $requestJson")
            }
            
            val requestBody = requestJson.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$baseUrl/people/search")
                .post(requestBody)
                .addHeader("authorization", "Basic $token")
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .build()
            
            println("Fetching employees from $baseUrl/people/search...")
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                println("API request failed with status: ${response.code}")
                if (response.body != null) {
                    println("Error response: ${response.body?.string()}")
                }
                return@withContext emptyList()
            }
            
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            
            if (debug) {
                println("Response body: $responseBody")
            }
            
            // Parse the response using kotlinx.serialization
            val hibobResponse = json.decodeFromString(HiBobResponse.serializer(), responseBody)
            
            // Convert to simplified models
            return@withContext hibobResponse.employees.map { employee ->
                SimpleEmployeeInfo.fromHiBobEmployee(employee)
            }
        } catch (e: Exception) {
            println("Error fetching employees: ${e.message}")
            if (debug) {
                e.printStackTrace()
            }
            return@withContext emptyList()
        }
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