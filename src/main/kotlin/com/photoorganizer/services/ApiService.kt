package com.photoorganizer.services

import com.photoorganizer.models.DuplicateGroup
import com.photoorganizer.models.Photo
import com.photoorganizer.models.ScanProgress
import com.photoorganizer.models.ScanRuleSet
import com.photoorganizer.models.TrashRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ApiService {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets)
    }

    private const val BASE_URL = "http://127.0.0.1:8000"
    private const val WS_URL = "ws://127.0.0.1:8000"

    suspend fun fetchPhotos(
        folderPath: String? = null,
        extension: String? = null,
        minSize: Long? = null,
        maxSize: Long? = null,
        sortBy: String = "taken_at",
        order: String = "DESC",
        limit: Int = 500,
        offset: Int = 0
    ): List<Photo> {
        return try {
            client.get("$BASE_URL/photos") {
                parameter("sort_by", sortBy)
                parameter("order", order)
                parameter("limit", limit)
                parameter("offset", offset)
                if (!folderPath.isNull_or_empty_str()) {
                    parameter("folder_path", folderPath)
                }
                if (!extension.isNull_or_empty_str()) {
                    parameter("extension", extension)
                }
                if (minSize != null) {
                    parameter("min_size", minSize)
                }
                if (maxSize != null) {
                    parameter("max_size", maxSize)
                }
            }.body()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun String?.isNull_or_empty_str(): Boolean = this == null || this.trim().isEmpty()

    suspend fun fetchExactDuplicates(): List<DuplicateGroup> {
        return try {
            client.get("$BASE_URL/duplicates/exact").body()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchBlurryPhotos(threshold: Double = 100.0): List<Photo> {
        return try {
            client.get("$BASE_URL/photos/blur") {
                parameter("threshold", threshold)
            }.body()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun trashPhoto(filePath: String): Boolean {
        return try {
            val response = client.post("$BASE_URL/photos/trash") {
                contentType(ContentType.Application.Json)
                setBody(TrashRequest(filePath))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    fun startScan(ruleSets: List<ScanRuleSet>): Flow<ScanProgress> = flow {
        try {
            client.webSocket("$WS_URL/ws/scan") {
                val payload = json.encodeToString(mapOf("rule_sets" to ruleSets))
                send(Frame.Text(payload))

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val progress = json.decodeFromString<ScanProgress>(text)
                        emit(progress)
                    }
                }
            }
        } catch (e: Exception) {
            emit(ScanProgress(type = "error", message = e.message ?: "Connection error"))
        }
    }
}
