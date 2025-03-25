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
        val listCommand = args.any { it == "--lists" || it == "-l" }
        val listName = args.firstOrNull { it.startsWith("--list=") }?.substringAfter("--list=")
        val findItem = args.firstOrNull { it.startsWith("--find=") }?.substringAfter("--find=")
        val lookupDepartment = args.firstOrNull { it.startsWith("--department=") }?.substringAfter("--department=")
        val lookupTitle = args.firstOrNull { it.startsWith("--title=") }?.substringAfter("--title=")
        
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
                println("Usage: java -jar hibob-cli.jar [email@example.com] [https://api.hibob.com/v1] [token=XXX] [/path/to/.env] [--debug|-d] [--lists] [--list=LIST_NAME] [--find=ITEM_NAME] [--department=ID] [--title=ID]")
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
                // Handle lookup commands for specific IDs
                if (lookupDepartment != null) {
                    lookupDepartmentById(apiClient, lookupDepartment)
                    return@runBlocking
                }
                
                if (lookupTitle != null) {
                    lookupTitleById(apiClient, lookupTitle)
                    return@runBlocking
                }
                
                // Handle named lists command
                if (listCommand || listName != null) {
                    fetchAndDisplayNamedLists(apiClient, listName, findItem)
                    return@runBlocking
                }
                
                if (email != null) {
                    // Fetch single employee by email
                    val employee = fetchEmployeeByEmail(email, apiClient)
                    if (employee != null) {
                        println("\nEmployee information for $email:")
                        printEmployee(employee, apiClient)
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
                            printEmployee(employee, apiClient)
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
     * Look up a department by its ID
     */
    private suspend fun lookupDepartmentById(
        apiClient: HiBobApiClient,
        departmentId: String
    ) = withContext(Dispatchers.IO) {
        val debugLogger: (String) -> Unit = { message ->
            if (isDebugMode) println("[DEBUG] $message")
        }
        
        val errorLogger: (String, Throwable?) -> Unit = { message, error ->
            println(message)
            if (isDebugMode && error != null) {
                error.printStackTrace()
            }
        }
        
        println("\nLooking up department with ID: $departmentId")
        
        try {
            // Try to fetch all employees to find one with the given department ID
            val employees = apiClient.fetchAllEmployees(debugLogger, errorLogger)
            
            // Get department mappings directly
            println("Fetching department mappings directly...")
            val departmentMappings = try {
                // Access fetchDepartmentMappings through reflection since it's private
                val method = HiBobApiClient::class.java.getDeclaredMethod(
                    "fetchDepartmentMappings",
                    Function1::class.java,
                    Function2::class.java
                )
                method.isAccessible = true
                
                @Suppress("UNCHECKED_CAST")
                method.invoke(apiClient, debugLogger, errorLogger) as Map<String, String>
            } catch (e: Exception) {
                debugLogger("Error accessing department mappings: ${e.message}")
                emptyMap()
            }
                
            // Check if we found a department name
            val departmentName = departmentMappings[departmentId]
            
            if (departmentName != null) {
                println("\nFound department:")
                println("- ID: $departmentId")
                println("- Name: $departmentName")
                return@withContext
            }
            // If direct lookup fails, try to find an employee with that department ID
            val matchingEmployees = employees.filter { 
                (it.work?.department != null && 
                 it.work.department.equals(departmentId, ignoreCase = true)) ||
                it.work?.reportsTo?.id == departmentId
            }
            
            if (matchingEmployees.isNotEmpty()) {
                val employee = matchingEmployees.first()
                println("\nFound department from employee data:")
                println("- Department: ${employee.work?.department}")
                println("- Employee: ${employee.displayName} (${employee.email})")
                return@withContext
            }
            
            println("No department found with ID: $departmentId")
        } catch (e: Exception) {
            println("Error looking up department: ${e.message}")
            if (isDebugMode) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Look up a title by its ID
     */
    private suspend fun lookupTitleById(
        apiClient: HiBobApiClient,
        titleId: String
    ) = withContext(Dispatchers.IO) {
        val debugLogger: (String) -> Unit = { message ->
            if (isDebugMode) println("[DEBUG] $message")
        }
        
        val errorLogger: (String, Throwable?) -> Unit = { message, error ->
            println(message)
            if (isDebugMode && error != null) {
                error.printStackTrace()
            }
        }
        
        println("\nLooking up title with ID: $titleId")
        
        try {
            // Try to fetch all employees to find one with the given title ID
            val employees = apiClient.fetchAllEmployees(debugLogger, errorLogger)
            
            // Get title mappings directly
            println("Fetching title mappings directly...")
            val titleMappings = try {
                // Access fetchTitleMappings through reflection since it's private
                val method = HiBobApiClient::class.java.getDeclaredMethod(
                    "fetchTitleMappings",
                    Function1::class.java,
                    Function2::class.java
                )
                method.isAccessible = true
                
                @Suppress("UNCHECKED_CAST")
                method.invoke(apiClient, debugLogger, errorLogger) as Map<String, String>
            } catch (e: Exception) {
                debugLogger("Error accessing title mappings: ${e.message}")
                emptyMap()
            }
                
            // Check if we found a title name
            val titleName = titleMappings[titleId]
            
            if (titleName != null) {
                println("\nFound title:")
                println("- ID: $titleId")
                println("- Name: $titleName")
                return@withContext
            }
            // If direct lookup fails, try to find an employee with that title ID
            val matchingEmployees = employees.filter { 
                (it.work?.title != null && 
                 it.work.title.equals(titleId, ignoreCase = true))
            }
            
            if (matchingEmployees.isNotEmpty()) {
                val employee = matchingEmployees.first()
                println("\nFound title from employee data:")
                println("- Title: ${employee.work?.title}")
                println("- Employee: ${employee.displayName} (${employee.email})")
                return@withContext
            }
            
            println("No title found with ID: $titleId")
        } catch (e: Exception) {
            println("Error looking up title: ${e.message}")
            if (isDebugMode) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Helper function to find an item by ID in a list of items and their children
     */
    private fun findItemById(items: List<com.example.ijcommittracer.models.NamedList.Item>, id: String): com.example.ijcommittracer.models.NamedList.Item? {
        for (item in items) {
            if (item.id == id) {
                return item
            }
            
            if (item.children.isNotEmpty()) {
                val foundInChildren = findItemById(item.children, id)
                if (foundInChildren != null) {
                    return foundInChildren
                }
            }
        }
        
        return null
    }
    
    /**
     * Prints employee information in a formatted way
     * Uses direct mapping lookups for departments and titles
     */
    private fun printEmployee(employee: SimpleEmployeeInfo, apiClient: HiBobApiClient? = null) {
        // Get mappings directly if API client is available
        val departmentMappings = apiClient?.let {
            try {
                // Access fetchDepartmentMappings through reflection since it's private
                val method = HiBobApiClient::class.java.getDeclaredMethod(
                    "fetchDepartmentMappings",
                    Function1::class.java,
                    Function2::class.java
                )
                method.isAccessible = true
                
                val debugLogger: (String) -> Unit = { if (isDebugMode) println("[DEBUG] $it") }
                
                @Suppress("UNCHECKED_CAST")
                method.invoke(it, debugLogger, null) as Map<String, String>
            } catch (e: Exception) {
                if (isDebugMode) println("[DEBUG] Error accessing department mappings: ${e.message}")
                emptyMap()
            }
        } ?: emptyMap()
        
        val titleMappings = apiClient?.let {
            try {
                // Access fetchTitleMappings through reflection since it's private
                val method = HiBobApiClient::class.java.getDeclaredMethod(
                    "fetchTitleMappings",
                    Function1::class.java,
                    Function2::class.java
                )
                method.isAccessible = true
                
                val debugLogger: (String) -> Unit = { if (isDebugMode) println("[DEBUG] $it") }
                
                @Suppress("UNCHECKED_CAST")
                method.invoke(it, debugLogger, null) as Map<String, String>
            } catch (e: Exception) {
                if (isDebugMode) println("[DEBUG] Error accessing title mappings: ${e.message}")
                emptyMap()
            }
        } ?: emptyMap()
        
        println("- ID: ${employee.id}")
        println("- Name: ${employee.name}")
        println("- Email: ${employee.email}")
        
        // Show department name and ID using direct mapping lookup
        val mappedDepartment = employee.departmentId?.let { departmentMappings[it] }
        if (mappedDepartment != null) {
            println("- Department: $mappedDepartment (ID: ${employee.departmentId})")
        } else if (employee.departmentId != null) {
            println("- Department: ${employee.team} (ID: ${employee.departmentId})")
        } else {
            println("- Department: ${employee.team}")
        }
        
        // Show title name and ID using direct mapping lookup
        val mappedTitle = employee.titleId?.let { titleMappings[it] }
        if (mappedTitle != null) {
            println("- Title: $mappedTitle (ID: ${employee.titleId})")
        } else if (employee.titleId != null) {
            println("- Title: ${employee.title} (ID: ${employee.titleId})")
        } else {
            println("- Title: ${employee.title}")
        }
        
        println("- Site: ${employee.site ?: "Not specified"}${employee.siteId?.let { " (ID: $it)" } ?: ""}")
        println("- Manager: ${employee.manager.ifEmpty { "None" }}")
        println("- Manager Email: ${employee.managerEmail ?: "None"}")
        println("- Start Date: ${employee.startDate ?: "Not specified"}")
        println("- Tenure: ${employee.tenure ?: "Not specified"}")
        println("- Is Manager: ${employee.isManager}")
    }
    
    /**
     * Fetches a single employee by email using the shared API client
     * Uses the simplified API client method that handles all enrichment in one call
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
        
        // Use the simplified method to fetch employee with enriched data
        println("Fetching employee with enriched title and department data...")
        return@withContext apiClient.fetchEmployeeByEmailWithEnrichedData(
            email = email,
            debugLogger = debugLogger,
            errorLogger = errorLogger
        )
    }
    
    /**
     * Fetches all employees using the shared API client
     * Uses the simplified API client method that handles all enrichment in one call
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
        
        // Use the simplified method to fetch all employees with enriched data
        println("Fetching all employees with enriched title and department data...")
        return@withContext apiClient.fetchAllEmployeesWithEnrichedData(
            debugLogger = debugLogger,
            errorLogger = errorLogger
        )
    }
    
    /**
     * Fetches and displays named lists from HiBob API
     */
    private suspend fun fetchAndDisplayNamedLists(
        apiClient: HiBobApiClient,
        specificListName: String?,
        findItemText: String?
    ) = withContext(Dispatchers.IO) {
        val debugLogger: (String) -> Unit = { message ->
            if (isDebugMode) println("[DEBUG] $message")
        }
        
        val errorLogger: (String, Throwable?) -> Unit = { message, error ->
            println(message)
            if (isDebugMode && error != null) {
                error.printStackTrace()
            }
        }
        
        println("\nNamed lists functionality has been simplified in the new API client.")
        println("Current implementation focuses on direct title and department mappings.")
        
        // Print available mappings
        val titleMappings = try {
            // Access fetchTitleMappings through reflection since it's private
            val method = HiBobApiClient::class.java.getDeclaredMethod(
                "fetchTitleMappings",
                Function1::class.java,
                Function2::class.java
            )
            method.isAccessible = true
            
            @Suppress("UNCHECKED_CAST")
            method.invoke(apiClient, debugLogger, errorLogger) as Map<String, String>
        } catch (e: Exception) {
            errorLogger("Error accessing title mappings: ${e.message}", e)
            emptyMap()
        }
        
        val departmentMappings = try {
            // Access fetchDepartmentMappings through reflection since it's private
            val method = HiBobApiClient::class.java.getDeclaredMethod(
                "fetchDepartmentMappings",
                Function1::class.java,
                Function2::class.java
            )
            method.isAccessible = true
            
            @Suppress("UNCHECKED_CAST")
            method.invoke(apiClient, debugLogger, errorLogger) as Map<String, String>
        } catch (e: Exception) {
            errorLogger("Error accessing department mappings: ${e.message}", e)
            emptyMap()
        }
        
        println("\nAvailable mappings:")
        println("\nTitle Mappings (${titleMappings.size} entries):")
        titleMappings.forEach { (id, name) ->
            println("- $name (ID: $id)")
        }
        
        println("\nDepartment Mappings (${departmentMappings.size} entries):")
        departmentMappings.forEach { (id, name) ->
            println("- $name (ID: $id)")
        }
        
        println("\nUse --title=ID or --department=ID to look up specific IDs")
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