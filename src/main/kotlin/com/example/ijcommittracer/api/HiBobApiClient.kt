package com.example.ijcommittracer.api

import com.example.ijcommittracer.models.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Shared utility class for making HiBob API requests.
 * Provides a single public method to fetch employees with enriched data.
 */
class HiBobApiClient(private val baseUrl: String, private val token: String) {

    // Create a lenient JSON parser that can handle malformed JSON
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Data structure for the named list API response
     */
    @kotlinx.serialization.Serializable
    private data class NamedListResponse(
        val name: String,
        val values: List<NamedList.Item> = emptyList()
    )

    /**
     * Main public function: Fetches all employees with enriched department and title data
     * and returns them as a map with email as key
     *
     * @param debugLogger Optional function to log debug information
     * @param errorLogger Optional function to log errors
     * @return Map of enriched employee information with email as key
     */
    fun fetchEmployeeData(
        debugLogger: ((String) -> Unit)? = null,
        errorLogger: ((String, Throwable?) -> Unit)? = null
    ): Map<String, SimpleEmployeeInfo> {
        debugLogger?.invoke("Fetching all employees with enriched data")

        // Step 1: Fetch title and department mappings
        val titleMappings = fetchTitleMappings(debugLogger, errorLogger)
        val departmentMappings = fetchDepartmentMappings(debugLogger, errorLogger)

        debugLogger?.invoke("Fetched ${titleMappings.size} titles and ${departmentMappings.size} departments")

        // Step 2: Fetch all employees
        val allEmployees = fetchAllEmployees(debugLogger, errorLogger)
        debugLogger?.invoke("Fetched ${allEmployees.size} employees")

        // Step 3: Convert and enrich employees with the mappings, create map with email as key
        return allEmployees
            .mapNotNull { employee ->
                val email = employee.email

                // Skip entries without email
                if (email.isBlank()) return@mapNotNull null

                // Start with basic info
                val basicInfo = SimpleEmployeeInfo.fromHiBobEmployee(employee)

                // Create enriched copy with department and title IDs mapped to names
                val enriched = basicInfo.copy(
                    // If we have a department in our mappings, use it
                    team = employee.work?.department?.let { departmentId ->
                        departmentMappings[departmentId] ?: basicInfo.team
                    } ?: basicInfo.team,

                    // If we have a title in our mappings, use it
                    title = employee.work?.title?.let { titleId ->
                        titleMappings[titleId] ?: basicInfo.title
                    } ?: basicInfo.title,

                    // Keep the IDs for reference
                    departmentId = employee.work?.department,
                    titleId = employee.work?.title
                )

                email to enriched
            }
            .toMap()
    }

    /**
     * Fetches title ID to name mappings
     */
    private fun fetchTitleMappings(
        debugLogger: ((String) -> Unit)? = null,
        errorLogger: ((String, Throwable?) -> Unit)? = null
    ): Map<String, String> {
        return fetchNamedListMappings("title", "title", debugLogger, errorLogger)
    }

    /**
     * Fetches department ID to name mappings
     */
    private fun fetchDepartmentMappings(
        debugLogger: ((String) -> Unit)? = null,
        errorLogger: ((String, Throwable?) -> Unit)? = null
    ): Map<String, String> {
        return fetchNamedListMappings("department", "department", debugLogger, errorLogger)
    }
    
    /**
     * Generic method to fetch named list mappings
     * 
     * @param listType The type of named list to fetch (e.g., "title", "department")
     * @param logName The name to use in log messages
     * @param debugLogger Optional function to log debug information
     * @param errorLogger Optional function to log errors
     * @return Map of ID to name mappings for the requested named list
     */
    private fun fetchNamedListMappings(
        listType: String,
        logName: String,
        debugLogger: ((String) -> Unit)? = null,
        errorLogger: ((String, Throwable?) -> Unit)? = null
    ): Map<String, String> {
        try {
            val url = "$baseUrl/company/named-lists/$listType?includeArchived=false"
            debugLogger?.invoke("Fetching $logName mappings from $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("authorization", "Basic $token")
                .addHeader("accept", "application/json")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                errorLogger?.invoke("API request failed with status: ${response.code}", null)
                return emptyMap()
            }

            val responseBody = response.body?.string() ?: return emptyMap()

            // Try to parse as NamedListResponse
            val namedList = json.decodeFromString<NamedListResponse>(responseBody)
            return namedList.values.associate { item -> item.id to item.name }
        } catch (e: Exception) {
            errorLogger?.invoke("Error fetching $logName mappings: ${e.message}", e)
            return emptyMap()
        }
    }

    /**
     * Fetches all employees from the HiBob API
     */
    internal fun fetchAllEmployees(
        debugLogger: ((String) -> Unit)? = null,
        errorLogger: ((String, Throwable?) -> Unit)? = null
    ): List<HiBobEmployee> {
        try {
            // Create search request for all employees
            val searchRequest = HiBobSearchRequest(showInactive = false)
            val requestJson = json.encodeToString(HiBobSearchRequest.serializer(), searchRequest)

            val requestBody = requestJson.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/people/search")
                .post(requestBody)
                .addHeader("authorization", "Basic $token")
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .build()

            debugLogger?.invoke("Fetching all employees from HiBob API")
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                errorLogger?.invoke("API request failed with status: ${response.code}", null)
                return emptyList()
            }

            val responseBody = response.body?.string() ?: return emptyList()

            // Parse the response using kotlinx.serialization
            val hibobResponse = json.decodeFromString(HiBobResponse.serializer(), responseBody)
            debugLogger?.invoke("Successfully fetched ${hibobResponse.employees.size} employees from HiBob API")

            return hibobResponse.employees
        } catch (e: Exception) {
            errorLogger?.invoke("Error fetching employees: ${e.message}", e)
            return emptyList()
        }
    }
}