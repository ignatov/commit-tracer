package com.example.ijcommittracer.services

import com.example.ijcommittracer.util.EnvFileReader
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with HiBob API to fetch employee information.
 * Uses coroutines for async operations and implements efficient caching.
 */
@Service(Service.Level.PROJECT)
class HiBobApiService(private val project: Project) {
    
    // Constants for .env file properties
    private val HIBOB_API_TOKEN_KEY = "HIBOB_API_TOKEN"
    private val HIBOB_API_URL_KEY = "HIBOB_API_URL"
    private val DEFAULT_HIBOB_API_URL = "https://api.hibob.com/v1"
    
    private val cache = ConcurrentHashMap<String, CachedEmployeeInfo>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val LOG = logger<HiBobApiService>()
    private val tokenStorage = TokenStorageService.getInstance(project)
    
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
     * Uses cache if available and not expired.
     */
    fun getEmployeeByEmail(email: String): EmployeeInfo? {
        // Return from cache if available and fresh (less than 24 hours old)
        cache[email]?.let { cachedInfo ->
            if (cachedInfo.isValid()) {
                return cachedInfo.info
            }
        }
        
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
            tokenStorage.getHiBobToken() ?: return null
        }
        
        if (token.isBlank()) {
            return null
        }
        
        return try {
            fetchEmployeeFromApi(email, token)?.also { employeeInfo ->
                // Store in cache with timestamp
                cache[email] = CachedEmployeeInfo(
                    info = employeeInfo,
                    timestamp = LocalDateTime.now()
                )
            }
        } catch (e: Exception) {
            LOG.warn("Error fetching employee info from HiBob API", e)
            null
        }
    }
    
    /**
     * Get employee information asynchronously using coroutines.
     * Preferred method for UI contexts to avoid blocking.
     */
    suspend fun getEmployeeByEmailAsync(email: String): EmployeeInfo? = withContext(Dispatchers.IO) {
        getEmployeeByEmail(email)
    }
    
    /**
     * Clear the cache.
     */
    fun clearCache() {
        cache.clear()
    }
    
    /**
     * Fetch employee information from HiBob API.
     */
    private fun fetchEmployeeFromApi(email: String, token: String): EmployeeInfo? {
        // Get path to .env file
        val projectPath = project.basePath
        val envFilePath = if (projectPath != null) {
            File(projectPath, ".env").absolutePath
        } else {
            ""
        }
        
        // Try to get base URL from .env file first
        val baseUrl = EnvFileReader.getInstance(envFilePath).getProperty(HIBOB_API_URL_KEY) ?: tokenStorage.getHiBobBaseUrl()
        
        val request = Request.Builder()
            .url("$baseUrl/people?email=$email")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            LOG.warn("HiBob API request failed with status: ${response.code}")
            return null
        }
        
        val responseBody = response.body?.string() ?: return null
        val jsonObject = JSONObject(responseBody)
        
        // Parse the response - HiBob API might return an array or a single object
        val employeesArray = jsonObject.optJSONArray("employees") ?: return null
        
        if (employeesArray.length() == 0) {
            return null
        }
        
        val employee = employeesArray.getJSONObject(0)
        
        return EmployeeInfo(
            email = email,
            name = employee.optString("displayName", ""),
            team = employee.optString("department", ""),
            title = employee.optString("title", ""),
            manager = employee.optString("managerEmail", "")
        )
    }
    
    companion object {
        // Cache validity duration - 24 hours
        private val CACHE_VALIDITY_DURATION = Duration.ofHours(24)
        
        @JvmStatic
        fun getInstance(project: Project): HiBobApiService = project.service()
    }
    
    /**
     * Private class for cached employee info with timestamp.
     */
    private data class CachedEmployeeInfo(
        val info: EmployeeInfo,
        val timestamp: LocalDateTime
    ) {
        fun isValid(): Boolean {
            val now = LocalDateTime.now()
            return Duration.between(timestamp, now) < CACHE_VALIDITY_DURATION
        }
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
    val manager: String
)