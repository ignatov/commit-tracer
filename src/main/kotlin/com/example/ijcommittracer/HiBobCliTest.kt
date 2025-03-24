package com.example.ijcommittracer

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.ijcommittracer.util.SimpleEnvFileReader
import java.io.File

/**
 * Simple test class that makes a direct API call to HiBob
 * using the exact same code from your original snippet
 */
fun main() {
    println("Testing HiBob API with original code")
    println("------------------------------------")

    val token = SimpleEnvFileReader.getInstance(File("/Users/ignatov/src/intellij/.env").absolutePath)
        .getProperty("HIBOB_API_TOKEN")
    
    println("Token read from .env file (length: ${token?.length ?: 0})")

    val client = OkHttpClient()

    val mediaType = "application/json".toMediaType()
    val body = "{\"showInactive\":false}".toRequestBody(mediaType)
    val request = Request.Builder()
        .url("https://api.hibob.com/v1/people/search")
        .post(body)
        .addHeader("accept", "application/json")
        .addHeader("content-type", "application/json")
        .addHeader("authorization", "Basic $token")
        .build()

    println("Sending request...")
    val response = client.newCall(request).execute()
    println("Response status: ${response.code}")
    
    if (response.isSuccessful) {
        val responseBody = response.body?.string()
        println("Response body (first 300 chars): ${responseBody?.take(300)}...")
    } else {
        println("Error response: ${response.body?.string()}")
    }
}