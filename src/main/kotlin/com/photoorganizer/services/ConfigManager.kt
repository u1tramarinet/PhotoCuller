package com.photoorganizer.services

import com.photoorganizer.models.ScanRuleSet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ConfigManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val configDir = File(System.getProperty("user.home"), ".photo_organizer")
    private val configFile = File(configDir, "rulesets.json")

    fun loadRuleSets(): List<ScanRuleSet> {
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                val loaded = json.decodeFromString<List<ScanRuleSet>>(content)
                if (loaded.isNotEmpty()) loaded else listOf(ScanRuleSet(name = "ルールセット 1"))
            } else {
                listOf(ScanRuleSet(name = "ルールセット 1"))
            }
        } catch (e: Exception) {
            listOf(ScanRuleSet(name = "ルールセット 1"))
        }
    }

    fun saveRuleSets(ruleSets: List<ScanRuleSet>) {
        try {
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            val content = json.encodeToString(ruleSets)
            configFile.writeText(content)
        } catch (e: Exception) {
            println("Failed to save rule sets: ${e.message}")
        }
    }
}
