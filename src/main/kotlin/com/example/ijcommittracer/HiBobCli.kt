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
            // Get employee data and extract department mappings
            val employeeData = apiClient.fetchEmployeeData(
                debugLogger = debugLogger,
                errorLogger = errorLogger
            )
            
            // Create mapping of department IDs to names
            val departmentMappings = employeeData.values
                .filter { it.departmentId != null && it.team.isNotBlank() }
                .associate { it.departmentId!! to it.team }
            
            // Look up department by ID
            val departmentName = departmentMappings[departmentId]
            
            if (departmentName != null) {
                println("\nFound department:")
                println("- ID: $departmentId")
                println("- Name: $departmentName")
                return@withContext
            }
            
            // If not found in mappings, look for employees with that department
            val matchingEmployees = employeeData.values.filter { 
                it.departmentId == departmentId
            }
            
            if (matchingEmployees.isNotEmpty()) {
                val employee = matchingEmployees.first()
                println("\nFound department from employee data:")
                println("- Department: ${employee.team}")
                println("- Employee: ${employee.name} (${employee.email})")
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
            // Get employee data and extract title mappings
            val employeeData = apiClient.fetchEmployeeData(
                debugLogger = debugLogger,
                errorLogger = errorLogger
            )
            
            // Create mapping of title IDs to names
            val titleMappings = employeeData.values
                .filter { it.titleId != null && it.title.isNotBlank() }
                .associate { it.titleId!! to it.title }
            
            // Look up title by ID
            val titleName = titleMappings[titleId]
            
            if (titleName != null) {
                println("\nFound title:")
                println("- ID: $titleId")
                println("- Name: $titleName")
                return@withContext
            }
            
            // If not found in mappings, look for employees with that title
            val matchingEmployees = employeeData.values.filter { 
                it.titleId == titleId
            }
            
            if (matchingEmployees.isNotEmpty()) {
                val employee = matchingEmployees.first()
                println("\nFound title from employee data:")
                println("- Title: ${employee.title}")
                println("- Employee: ${employee.name} (${employee.email})")
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
     */
    private fun printEmployee(employee: SimpleEmployeeInfo, apiClient: HiBobApiClient? = null) {
        // When we print a single employee, we don't need to look up mapping again
        // as the team and title values should already be mapped
        
        println("- ID: ${employee.id}")
        println("- Name: ${employee.name}")
        println("- Email: ${employee.email}")
        
        // Department info
        if (employee.departmentId != null) {
            println("- Department: ${employee.team} (ID: ${employee.departmentId})")
        } else {
            println("- Department: ${employee.team}")
        }
        
        // Title info
        if (employee.titleId != null) {
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
        
        // Fetch all employees as a map and retrieve the one we need
        println("Fetching employee data with enriched title and department info...")
        val employeeMap = apiClient.fetchEmployeeData(
            debugLogger = debugLogger,
            errorLogger = errorLogger
        )
        
        return@withContext employeeMap[email]
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
        
        // Fetch all employees as a map and convert to list
        println("Fetching all employees with enriched title and department data...")
        val employeeMap = apiClient.fetchEmployeeData(
            debugLogger = debugLogger,
            errorLogger = errorLogger
        )
        
        return@withContext employeeMap.values.toList()
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
        
        println("\nFetching employee data to extract title and department mappings...")
        
        // Get employee data and extract unique title and department mappings
        val employeeData = apiClient.fetchEmployeeData(
            debugLogger = debugLogger,
            errorLogger = errorLogger
        )
        
        // Create title and department mappings from the employee data
        val titleMappings = mutableMapOf<String, String>()
        val departmentMappings = mutableMapOf<String, String>()
        
        employeeData.values.forEach { employee ->
            // Add title mapping if available
            if (employee.titleId != null && employee.title.isNotBlank()) {
                titleMappings[employee.titleId] = employee.title
            }
            
            // Add department mapping if available
            if (employee.departmentId != null && employee.team.isNotBlank()) {
                departmentMappings[employee.departmentId] = employee.team
            }
        }
        
        // Filter based on specific list if requested
        when {
            specificListName?.equals("title", ignoreCase = true) == true -> {
                println("\nTitle Mappings (${titleMappings.size} entries):")
                titleMappings.forEach { (id, name) ->
                    println("- $name (ID: $id)")
                }
                
                // Find specific item if requested
                if (!findItemText.isNullOrBlank()) {
                    val matchingIds = titleMappings.filterValues { 
                        it.contains(findItemText, ignoreCase = true) 
                    }
                    
                    if (matchingIds.isEmpty()) {
                        println("\nNo title found containing '$findItemText'")
                    } else {
                        println("\nFound ${matchingIds.size} titles containing '$findItemText':")
                        matchingIds.forEach { (id, name) ->
                            println("- $name (ID: $id)")
                        }
                    }
                }
            }
            
            specificListName?.equals("department", ignoreCase = true) == true -> {
                println("\nDepartment Mappings (${departmentMappings.size} entries):")
                departmentMappings.forEach { (id, name) ->
                    println("- $name (ID: $id)")
                }
                
                // Find specific item if requested
                if (!findItemText.isNullOrBlank()) {
                    val matchingIds = departmentMappings.filterValues { 
                        it.contains(findItemText, ignoreCase = true) 
                    }
                    
                    if (matchingIds.isEmpty()) {
                        println("\nNo department found containing '$findItemText'")
                    } else {
                        println("\nFound ${matchingIds.size} departments containing '$findItemText':")
                        matchingIds.forEach { (id, name) ->
                            println("- $name (ID: $id)")
                        }
                    }
                }
            }
            
            else -> {
                // Display all mappings
                println("\nAvailable mappings:")
                
                println("\nTitle Mappings (${titleMappings.size} entries):")
                titleMappings.forEach { (id, name) ->
                    println("- $name (ID: $id)")
                }
                
                println("\nDepartment Mappings (${departmentMappings.size} entries):")
                departmentMappings.forEach { (id, name) ->
                    println("- $name (ID: $id)")
                }
                
                println("\nUse --list=title or --list=department to view specific lists")
                println("Use --list=title --find=word to search for titles")
                println("Use --title=ID or --department=ID to look up specific IDs")
            }
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