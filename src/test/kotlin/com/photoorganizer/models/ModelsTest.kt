package com.photoorganizer.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testPhotoSerialization() {
        val photo = Photo(
            id = 1L,
            filePath = "/path/to/img.jpg",
            folderPath = "/path/to",
            fileSize = 2048L,
            modifiedAt = 1600000000L,
            blurScore = 150.5
        )

        val encoded = json.encodeToString(photo)
        val decoded = json.decodeFromString<Photo>(encoded)

        assertEquals(photo.filePath, decoded.filePath)
        assertEquals(photo.fileSize, decoded.fileSize)
        assertEquals(photo.blurScore, decoded.blurScore)
    }

    @Test
    fun testScanProgressSerialization() {
        val progress = ScanProgress(type = "progress", current = 10, total = 100, message = "Scanning")
        val encoded = json.encodeToString(progress)
        val decoded = json.decodeFromString<ScanProgress>(encoded)

        assertEquals(10, decoded.current)
        assertEquals(100, decoded.total)
        assertEquals("Scanning", decoded.message)
    }

    @Test
    fun testScanRuleSetSerialization() {
        val ruleSet = ScanRuleSet(
            name = "テストルール",
            folderPaths = listOf("/photos1", "/photos2"),
            fileFilterType = "CUSTOM_EXT",
            customExtensions = listOf("jpg", "png"),
            includeSubfolders = false
        )
        val encoded = json.encodeToString(ruleSet)
        val decoded = json.decodeFromString<ScanRuleSet>(encoded)

        assertEquals("テストルール", decoded.name)
        assertEquals(listOf("/photos1", "/photos2"), decoded.folderPaths)
        assertEquals("CUSTOM_EXT", decoded.fileFilterType)
        assertEquals(listOf("jpg", "png"), decoded.customExtensions)
        assertEquals(false, decoded.includeSubfolders)
    }
}
