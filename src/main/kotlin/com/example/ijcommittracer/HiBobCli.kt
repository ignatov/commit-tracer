package com.example.ijcommittracer

import com.example.ijcommittracer.services.EmployeeInfo
import com.example.ijcommittracer.util.SimpleEnvFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
                println("Usage: java -jar hibob-cli.jar [email@example.com] [https://api.hibob.com/v1] [token=XXX] [/path/to/.env]")
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
                    val employee = fetchEmployeeByEmail(email, token, baseUrl)
                    if (employee != null) {
                        println("\nEmployee information for $email:")
                        printEmployee(employee)
                    } else {
                        println("\nNo employee found with email $email")
                    }
                } else {
                    // Fetch all employees
                    println("\nFetching all employees...")
                    val employees = fetchAllEmployees(token, baseUrl)
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
                    }
                }
            } catch (e: Exception) {
                println("\nError fetching employee data: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Prints employee information in a formatted way
     */
    private fun printEmployee(employee: EmployeeInfo) {
        println("- Name: ${employee.name}")
        println("- Email: ${employee.email}")
        println("- Team: ${employee.team}")
        println("- Title: ${employee.title}")
        println("- Manager: ${employee.manager.ifEmpty { "None" }}")
    }
    
    /**
     * Fetches a single employee by email
     */
    private suspend fun fetchEmployeeByEmail(email: String, token: String, baseUrl: String): EmployeeInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/people?email=$email")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                println("API request failed with status: ${response.code}")
                return@withContext null
            }
            
            val responseBody = response.body?.string() ?: return@withContext null
            val jsonObject = JSONObject(responseBody)
            
            val employeesArray = jsonObject.optJSONArray("employees") ?: return@withContext null
            
            if (employeesArray.length() == 0) {
                return@withContext null
            }
            
            val employee = employeesArray.getJSONObject(0)
            
            return@withContext EmployeeInfo(
                email = email,
                name = employee.optString("displayName", ""),
                team = employee.optString("department", ""),
                title = employee.optString("title", ""),
                manager = employee.optString("managerEmail", "")
            )
        } catch (e: Exception) {
            println("Error fetching employee: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Fetches all employees from HiBob API
     */
    private suspend fun fetchAllEmployees(token: String, baseUrl: String): List<EmployeeInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/people")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                println("API request failed with status: ${response.code}")
                return@withContext emptyList()
            }
            
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            val jsonObject = JSONObject(responseBody)
            
            val employeesArray = jsonObject.optJSONArray("employees") ?: return@withContext emptyList()
            
            val result = mutableListOf<EmployeeInfo>()
            
            for (i in 0 until employeesArray.length()) {
                val employee = employeesArray.getJSONObject(i)
                val email = employee.optString("email", "")
                
                // Skip entries without email
                if (email.isBlank()) continue
                
                result.add(EmployeeInfo(
                    email = email,
                    name = employee.optString("displayName", ""),
                    team = employee.optString("department", ""),
                    title = employee.optString("title", ""),
                    manager = employee.optString("managerEmail", "")
                ))
            }
            
            return@withContext result
        } catch (e: Exception) {
            println("Error fetching employees: ${e.message}")
            return@withContext emptyList()
        }
    }
}