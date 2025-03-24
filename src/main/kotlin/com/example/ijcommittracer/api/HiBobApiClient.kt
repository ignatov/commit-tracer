package com.example.ijcommittracer.api

import com.example.ijcommittracer.models.HiBobEmployee
import com.example.ijcommittracer.models.HiBobResponse
import com.example.ijcommittracer.models.HiBobSearchRequest
import com.example.ijcommittracer.models.SimpleEmployeeInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Shared utility class for making HiBob API requests.
 * Used by both the plugin service and CLI components.
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
     * Fetches a single employee by email using the search endpoint.
     * 
     * @param email The email of the employee to fetch
     * @param debugLogger Optional function to log debug information
     * @param errorLogger Optional function to log errors
     * @return The fetched employee data or null if not found
     */
    fun fetchEmployeeByEmail(
        email: String,
        debugLogger: ((String) -> Unit)? = null,
        errorLogger: ((String, Throwable?) -> Unit)? = null
    ): HiBobEmployee? {
        try {
            // Create search request with email filter
            val searchRequest = HiBobSearchRequest(showInactive = false, email = email)
            val requestJson = json.encodeToString(HiBobSearchRequest.serializer(), searchRequest)
            debugLogger?.invoke("Request payload: $requestJson")
            
            val requestBody = requestJson.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$baseUrl/people/search")
                .post(requestBody)
                .addHeader("authorization", "Basic $token")
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .build()
            
            debugLogger?.invoke("Fetching employee with email $email...")
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorMessage = "API request failed with status: ${response.code}"
                errorLogger?.invoke(errorMessage, null)
                response.body?.string()?.let { debugLogger?.invoke("Error response: $it") }
                return null
            }
            
            val responseBody = response.body?.string() ?: return null
            debugLogger?.invoke("Response received from HiBob API")
            
            // Parse the response using kotlinx.serialization
            val hibobResponse = json.decodeFromString(HiBobResponse.serializer(), responseBody)
            
            if (hibobResponse.employees.isEmpty()) {
                debugLogger?.invoke("No employee found with email $email")
                return null
            }
            
            return hibobResponse.employees.first()
        } catch (e: Exception) {
            errorLogger?.invoke("Error fetching/parsing employee from HiBob API: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Fetches all employees from the HiBob API using the search endpoint.
     * 
     * @param debugLogger Optional function to log debug information
     * @param errorLogger Optional function to log errors
     * @return The list of fetched employees or empty list if the API call fails
     */
    fun fetchAllEmployees(
        debugLogger: ((String) -> Unit)? = null,
        errorLogger: ((String, Throwable?) -> Unit)? = null
    ): List<HiBobEmployee> {
        try {
            // Create search request for all employees
            val searchRequest = HiBobSearchRequest(showInactive = false)
            val requestJson = json.encodeToString(HiBobSearchRequest.serializer(), searchRequest)
            debugLogger?.invoke("Request payload: $requestJson")
            
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
                val errorMessage = "API request failed with status: ${response.code}"
                errorLogger?.invoke(errorMessage, null)
                response.body?.string()?.let { debugLogger?.invoke("Error response: $it") }
                return emptyList()
            }
            
            val responseBody = response.body?.string() ?: return emptyList()
            debugLogger?.invoke("Response received from HiBob API")
            
            // Parse the response using kotlinx.serialization
            val hibobResponse = json.decodeFromString(HiBobResponse.serializer(), responseBody)
            debugLogger?.invoke("Successfully fetched ${hibobResponse.employees.size} employees from HiBob API")
            
            return hibobResponse.employees
        } catch (e: Exception) {
            errorLogger?.invoke("Error fetching/parsing employees from HiBob API: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Utility method to convert a HiBobEmployee to a SimpleEmployeeInfo
     */
    fun convertToSimpleEmployeeInfo(employee: HiBobEmployee): SimpleEmployeeInfo {
        return SimpleEmployeeInfo.fromHiBobEmployee(employee)
    }
}