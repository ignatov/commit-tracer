package com.example.ijcommittracer

import com.example.ijcommittracer.api.HiBobApiClient
import com.example.ijcommittracer.models.SimpleEmployeeInfo
import com.example.ijcommittracer.services.EmployeeInfo
import com.example.ijcommittracer.util.SimpleJsonConfigReader
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
        val baseUrl = args.firstOrNull { it.startsWith("http") } 
            ?: SimpleJsonConfigReader.DEFAULT_HIBOB_URL
        val tokenArg = args.firstOrNull { it.startsWith("token=") }?.substringAfter("token=")
        val configFile = args.firstOrNull { it.endsWith(".json") || it.endsWith(".env.json") }
        isDebugMode = args.any { it == "--debug" || it == "-d" }
        val listCommand = args.any { it == "--lists" || it == "-l" }
        val listName = args.firstOrNull { it.startsWith("--list=") }?.substringAfter("--list=")
        val findItem = args.firstOrNull { it.startsWith("--find=") }?.substringAfter("--find=")
        val lookupDepartment = args.firstOrNull { it.startsWith("--department=") }?.substringAfter("--department=")
        val lookupTitle = args.firstOrNull { it.startsWith("--title=") }?.substringAfter("--title=")
        val listMappings = args.any { it == "--list-mappings" }
        val addMapping = args.firstOrNull { it.startsWith("--add-mapping=") }?.substringAfter("--add-mapping=")
        val removeMapping = args.firstOrNull { it.startsWith("--remove-mapping=") }?.substringAfter("--remove-mapping=")
        
        // Handle email mapping commands if present
        if (listMappings || addMapping != null || removeMapping != null) {
            handleMappingCommands(configFile, listMappings, addMapping, removeMapping)
            return
        }
        
        // Get token from arguments, config file, or environment variables
        val (token, configReader) = getToken(tokenArg, configFile)
        
        // Validate that we have a non-empty token
        if (token.isBlank()) {
            println("Error: HiBob API token is empty or could not be read properly.")
            println("Usage: java -jar hibob-cli.jar [email@example.com] [https://api.hibob.com/v1] [token=XXX] [/path/to/.env.json] [--debug|-d] [--lists] [--list=LIST_NAME] [--find=ITEM_NAME] [--department=ID] [--title=ID] [--list-mappings] [--add-mapping=FROM:TO] [--remove-mapping=EMAIL]")
            return
        }

        println("\nUsing HiBob API URL: $baseUrl")
        
        // Show email mappings if available
        val mappings = configReader?.getAllEmailMappings() ?: emptyMap()
        if (mappings.isNotEmpty()) {
            println("Loaded ${mappings.size} email mappings from configuration")
            if (email != null && mappings.containsKey(email)) {
                println("Mapping email: $email → ${mappings[email]}")
            }
        }
        
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
                    // Map email if needed and fetch single employee
                    val mappedEmail = configReader?.mapEmail(email) ?: email
                    if (mappedEmail != email) {
                        println("Using mapped email for lookup: $mappedEmail")
                    }
                    
                    // Fetch single employee by email
                    val employee = fetchEmployeeByEmail(mappedEmail, apiClient)
                    if (employee != null) {
                        println("\nEmployee information for $mappedEmail:")
                        printEmployee(employee, apiClient)
                    } else {
                        println("\nNo employee found with email $mappedEmail")
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
     * Handles commands related to email mappings
     */
    private fun handleMappingCommands(
        configFile: String?,
        listMappings: Boolean,
        addMapping: String?,
        removeMapping: String?
    ) {
        val configFilePath = if (configFile != null) {
            File(configFile).absolutePath
        } else {
            val userHome = System.getProperty("user.home")
            File(userHome, ".commitmapper/${SimpleJsonConfigReader.DEFAULT_CONFIG_FILENAME}").absolutePath
        }
        
        val configReader = SimpleJsonConfigReader.getInstance(configFilePath)
        
        println("Using configuration file: $configFilePath")
        
        when {
            listMappings -> {
                val mappings = configReader.getAllEmailMappings()
                println("\nEmail Mappings (${mappings.size} entries):")
                if (mappings.isEmpty()) {
                    println("No mappings found")
                } else {
                    mappings.forEach { (from, to) ->
                        println("  $from → $to")
                    }
                }
            }
            
            addMapping != null -> {
                val parts = addMapping.split(":", limit = 2)
                if (parts.size != 2) {
                    println("Error: Invalid mapping format. Use --add-mapping=FROM:TO")
                    return
                }
                
                val fromEmail = parts[0].trim()
                val toEmail = parts[1].trim()
                
                if (fromEmail.isBlank() || toEmail.isBlank()) {
                    println("Error: Both FROM and TO emails must be specified")
                    return
                }
                
                // To update mappings, we need to read the current config,
                // modify the emailMappings section, and save the updated config
                
                // Get current configuration
                val config = configReader.getAllConfig().toMutableMap()
                
                // Get current mappings
                @Suppress("UNCHECKED_CAST")
                val currentMappings = (config[SimpleJsonConfigReader.EMAIL_MAPPINGS_KEY] as? Map<String, String>)?.toMutableMap()
                    ?: mutableMapOf()
                
                // Add the new mapping
                currentMappings[fromEmail] = toEmail
                
                // Update config with new mappings
                config[SimpleJsonConfigReader.EMAIL_MAPPINGS_KEY] = currentMappings
                
                // Save to file
                val file = File(configFilePath)
                try {
                    file.parentFile?.mkdirs()
                    val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    val json = gson.toJson(config)
                    file.writeText(json)
                    println("Added mapping: $fromEmail → $toEmail")
                } catch (e: Exception) {
                    println("Error saving mapping: ${e.message}")
                    if (isDebugMode) {
                        e.printStackTrace()
                    }
                }
            }
            
            removeMapping != null -> {
                val emailToRemove = removeMapping.trim()
                
                if (emailToRemove.isBlank()) {
                    println("Error: Email to remove must be specified")
                    return
                }
                
                // Get current configuration
                val config = configReader.getAllConfig().toMutableMap()
                
                // Get current mappings
                @Suppress("UNCHECKED_CAST")
                val currentMappings = (config[SimpleJsonConfigReader.EMAIL_MAPPINGS_KEY] as? Map<String, String>)?.toMutableMap()
                    ?: mutableMapOf()
                
                if (!currentMappings.containsKey(emailToRemove)) {
                    println("No mapping found for email: $emailToRemove")
                    return
                }
                
                // Remove the mapping
                val removedTo = currentMappings.remove(emailToRemove)
                
                // Update config with new mappings
                config[SimpleJsonConfigReader.EMAIL_MAPPINGS_KEY] = currentMappings
                
                // Save to file
                val file = File(configFilePath)
                try {
                    val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    val json = gson.toJson(config)
                    file.writeText(json)
                    println("Removed mapping: $emailToRemove → $removedTo")
                } catch (e: Exception) {
                    println("Error removing mapping: ${e.message}")
                    if (isDebugMode) {
                        e.printStackTrace()
                    }
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