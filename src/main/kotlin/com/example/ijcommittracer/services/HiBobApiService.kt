package com.example.ijcommittracer.services

import com.example.ijcommittracer.api.HiBobApiClient
import com.example.ijcommittracer.util.EnvFileReader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

@Service(Service.Level.PROJECT)
class HiBobApiService(private val project: Project) {
    // Constants for .env file properties
    private val HIBOB_API_TOKEN_KEY = "HIBOB_API_TOKEN"
    private val HIBOB_API_URL_KEY = "HIBOB_API_URL"
    private val DEFAULT_HIBOB_API_URL = "https://api.hibob.com/v1"
    
    private val inMemoryCache = ConcurrentHashMap<String, CachedEmployeeInfo>()
    private val LOG = logger<HiBobApiService>()
    private val tokenStorage = TokenStorageService.getInstance(project)
    private val configService = ConfigurationService.getInstance(project)

    // State object for persistent storage
    private var myState = HiBobState()
    
    // Thread-safe variables for cache state
    private val lastFullCacheRefresh = AtomicReference<LocalDateTime>(LocalDateTime.now().minusDays(2))
    @Volatile
    private var isFullCacheLoaded = false
    
    // Flag to prevent concurrent refresh operations
    private val isRefreshing = AtomicBoolean(false)
    
    /**
     * State class to persist employee cache
     */
    data class HiBobState(
        var employees: Map<String, SerializableEmployeeInfo> = emptyMap(),
        var lastCacheUpdate: String = LocalDateTime.now().toString()
    )
    
    /**
     * Update the persistent state from the in-memory cache
     */
    private fun updateStateFromCache() {
        val serializedCache = inMemoryCache.mapValues { (_, cachedInfo) ->
            SerializableEmployeeInfo(
                email = cachedInfo.info.email,
                name = cachedInfo.info.name,
                team = cachedInfo.info.team,
                title = cachedInfo.info.title,
                manager = cachedInfo.info.manager,
                timestamp = cachedInfo.timestamp.toString(),
                departmentId = cachedInfo.info.departmentId,
                titleId = cachedInfo.info.titleId,
                siteId = cachedInfo.info.siteId,
                teamId = cachedInfo.info.teamId
            )
        }
        
        myState = HiBobState(
            employees = serializedCache,
            lastCacheUpdate = LocalDateTime.now().toString()
        )
    }
    
    /**
     * Set the API credentials.
     */
    fun setApiCredentials(token: String, baseUrl: String = "https://api.hibob.com/v1") {
        tokenStorage.setHiBobToken(token)
        tokenStorage.setHiBobBaseUrl(baseUrl)
        // Clear cache when credentials change
        clearCache()
    }
    
    /**
     * Get employee information by email.
     * Uses cache if available. Returns null if not found.
     * 
     * Thread-safe implementation that avoids race conditions.
     * Maps non-standard emails to standard emails using the ConfigurationService.
     */
    fun getEmployeeByEmail(email: String): EmployeeInfo? {
        // Map the email before looking up
        val mappedEmail = configService.mapEmail(email)
        
        // Log if email was mapped
        if (mappedEmail != email) {
            LOG.info("Mapped email from $email to $mappedEmail")
        }
        
        // First quick check without synchronization
        inMemoryCache[mappedEmail]?.let { cachedInfo ->
            LOG.debug("Returning employee info for $mappedEmail from cache (quick path)")
            return cachedInfo.info
        }
        
        // If not found, check if we need to refresh cache
        synchronized(this) {
            // Check cache again in case another thread refreshed while we were waiting
            inMemoryCache[mappedEmail]?.let { cachedInfo ->
                LOG.debug("Returning employee info for $mappedEmail from cache (after sync)")
                return cachedInfo.info
            }
            
            // Check if cache needs to be refreshed
            checkAndRefreshCacheIfNeeded()
            
            // One more check after potential refresh
            inMemoryCache[mappedEmail]?.let { cachedInfo ->
                LOG.debug("Returning employee info for $mappedEmail from cache (after refresh)")
                return cachedInfo.info
            }
            
            // If still not found, try with the original email as fallback
            if (mappedEmail != email) {
                inMemoryCache[email]?.let { cachedInfo ->
                    LOG.debug("Mapped email not found, but original email $email found in cache")
                    return cachedInfo.info
                }
            }
            
            // If still not found, return null
            // We don't do individual lookups - all data should be loaded in batch
            LOG.debug("Employee $mappedEmail not found in cache after refresh check")
            return null
        }
    }
    
    /**
     * Check if the cache needs to be refreshed (once per day)
     * and trigger a refresh if needed in a background thread
     * 
     * This method is synchronized through its caller.
     */
    private fun checkAndRefreshCacheIfNeeded() {
        val now = LocalDateTime.now()
        val lastRefresh = lastFullCacheRefresh.get()
        
        // Refresh if the cache is older than 24 hours and not already refreshing
        if (Duration.between(lastRefresh, now).toHours() >= 24 && !isRefreshing.get()) {
            // Check if we're on the EDT to avoid UI freezes
            if (SwingUtilities.isEventDispatchThread()) {
                LOG.debug("Refreshing cache from background thread to avoid EDT freezes")
                // Run cache refresh in a background thread
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        refreshFullCache()
                    } catch (e: Exception) {
                        LOG.error("Error refreshing cache in background: ${e.message}", e)
                    }
                }
            } else {
                // Already on a background thread, can refresh directly
                refreshFullCache()
            }
        }
    }
    
    /**
     * Refresh the full cache by fetching all employees
     * 
     * Uses atomic flag to prevent concurrent refreshes.
     */
    @Synchronized
    fun refreshFullCache() {
        // Use atomic flag to prevent concurrent refreshes
        if (!isRefreshing.compareAndSet(false, true)) {
            LOG.debug("Cache refresh already in progress, skipping")
            return
        }
        
        try {
            val token = getToken()
            if (token.isNullOrBlank()) {
                LOG.warn("Unable to refresh cache: HiBob API token is missing")
                return
            }
            
            LOG.info("Refreshing full HiBob employee cache")
            
            // Fetch all employees using the dedicated method
            val allEmployees = fetchAllEmployeesFromApi(token)
            
            if (allEmployees.isNotEmpty()) {
                synchronized(this) {
                    // Clear existing cache
                    inMemoryCache.clear()
                    
                    // Update cache with all employees
                    val now = LocalDateTime.now()
                    allEmployees.forEach { employeeInfo ->
                        if (employeeInfo.email.isNotBlank()) {
                            inMemoryCache[employeeInfo.email] = CachedEmployeeInfo(
                                info = employeeInfo,
                                timestamp = now
                            )
                        }
                    }
                    
                    // Update timestamp and flag atomically
                    lastFullCacheRefresh.set(now)
                    isFullCacheLoaded = true
                    
                    // Update persistent state
                    updateStateFromCache()
                }
                
                LOG.info("HiBob cache refreshed with ${allEmployees.size} employees")
            } else {
                LOG.warn("HiBob API returned no employees")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to refresh HiBob employee cache", e)
        } finally {
            // Always reset the refresh flag
            isRefreshing.set(false)
        }
    }
    
    /**
     * Start cache initialization in parallel with other operations
     */
    fun initializeCache() {
        // Background thread to load the cache
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // Already on a background thread, so checkAndRefreshCacheIfNeeded will run directly
                checkAndRefreshCacheIfNeeded()
            } catch (e: Exception) {
                LOG.warn("Failed to initialize HiBob cache", e)
            }
        }
    }
    
    /**
     * Clear the cache.
     * Thread-safe implementation.
     */
    @Synchronized
    fun clearCache() {
        // Wait for any ongoing refresh to complete
        if (isRefreshing.get()) {
            LOG.debug("Waiting for refresh to complete before clearing cache")
            
            // Try to acquire the refresh lock to ensure any refresh has completed
            while (isRefreshing.get()) {
                try {
                    Thread.sleep(50) // Small delay to avoid CPU spinning
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        
        synchronized(this) {
            inMemoryCache.clear()
            lastFullCacheRefresh.set(LocalDateTime.now().minusDays(2))
            isFullCacheLoaded = false
            updateStateFromCache()
            LOG.debug("Cache cleared")
        }
    }
    
    /**
     * Get the API token from either .env file or credentials store
     */
    private fun getToken(): String? {
        // Get path to .env file
        val projectPath = project.basePath
        val envFilePath = if (projectPath != null) {
            File(projectPath, ".env").absolutePath
        } else {
            ""
        }
        
        // Try to get token from .env file first
        val envToken = EnvFileReader.getInstance(envFilePath).getProperty(HIBOB_API_TOKEN_KEY)
        val token = if (!envToken.isNullOrBlank()) {
            LOG.info("Using HiBob API token from .env file")
            envToken
        } else {
            // Fall back to tokenStorage
            tokenStorage.getHiBobToken()
        }
        
        return if (token.isNullOrBlank()) null else token
    }
    
    /**
     * Get the API base URL from either .env file or settings
     */
    private fun getBaseUrl(): String {
        val projectPath = project.basePath
        val envFilePath = if (projectPath != null) {
            File(projectPath, ".env").absolutePath
        } else {
            ""
        }
        
        // Try to get base URL from .env file first
        return EnvFileReader.getInstance(envFilePath).getProperty(HIBOB_API_URL_KEY) 
            ?: tokenStorage.getHiBobBaseUrl()
    }
    
    
    /**
     * Fetch all employees from HiBob API with enriched title and department data.
     * Uses the simplified API client method that handles all steps in one call.
     */
    private fun fetchAllEmployeesFromApi(token: String): List<EmployeeInfo> {
        try {
            // Re-initialize the API client with the provided token
            val client = HiBobApiClient(getBaseUrl(), token)
            
            LOG.info("Fetching all employees with enriched data from HiBob API")
            
            // Use the single client method to fetch all employee data
            val employeeDataMap = client.fetchEmployeeData(
                debugLogger = { message -> LOG.debug(message) },
                errorLogger = { message, error -> LOG.warn(message, error) }
            )
            
            // Convert to our internal EmployeeInfo model
            val result = employeeDataMap.map { (email, simpleInfo) ->
                EmployeeInfo(
                    email = email,
                    name = simpleInfo.name,
                    team = simpleInfo.team,
                    title = simpleInfo.title,
                    manager = simpleInfo.manager,
                    departmentId = simpleInfo.departmentId,
                    titleId = simpleInfo.titleId,
                    siteId = simpleInfo.siteId,
                    teamId = simpleInfo.teamId
                )
            }
            
            LOG.info("Successfully fetched ${result.size} employees with enriched data from HiBob API")
            return result
        } catch (e: Exception) {
            LOG.error("Failed to fetch enriched employee data from HiBob API", e)
            return emptyList()
        }
    }
    
    
    
    companion object {
        @JvmStatic
        fun getInstance(project: Project): HiBobApiService = project.service()
    }
    
    /**
     * Private class for cached employee info with timestamp.
     */
    private data class CachedEmployeeInfo(
        val info: EmployeeInfo,
        val timestamp: LocalDateTime
    )
    
    /**
     * Serializable version of employee info for persistent state.
     */
    data class SerializableEmployeeInfo(
        val email: String,
        val name: String,
        val team: String,
        val title: String,
        val manager: String,
        val timestamp: String,
        val departmentId: String? = null,
        val titleId: String? = null,
        val siteId: String? = null,
        val teamId: String? = null
    ) {
    }
}

/**
 * Data class to store employee information.
 */
data class EmployeeInfo(
    val email: String,
    val name: String,
    val team: String,
    val title: String,
    val manager: String,
    val departmentId: String? = null,
    val titleId: String? = null,
    val siteId: String? = null,
    val teamId: String? = null
)