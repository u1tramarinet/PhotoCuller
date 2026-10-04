package com.photoorganizer.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Photo(
    val id: Long? = null,
    @SerialName("file_path") val filePath: String,
    @SerialName("folder_path") val folderPath: String,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("modified_at") val modifiedAt: Long,
    @SerialName("taken_at") val takenAt: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("thumbnail_path") val thumbnailPath: String? = null,
    @SerialName("exact_hash") val exactHash: String? = null,
    val phash: String? = null,
    @SerialName("blur_score") val blurScore: Double? = null,
    @SerialName("group_id") val groupId: Long? = null,
    val status: String = "ACTIVE"
)

@Serializable
data class DuplicateGroup(
    val hash: String,
    val photos: List<Photo>
)

@Serializable
data class ScanProgress(
    val type: String,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = ""
)

@Serializable
data class FolderConfig(
    val path: String,
    @SerialName("file_filter_type") val fileFilterType: String = "ALL", // "ALL", "CUSTOM_EXT", "NAME_CONTAINS"
    @SerialName("custom_extensions") val customExtensions: List<String> = emptyList(), // e.g. [".jpg", ".png"]
    @SerialName("name_substring") val nameSubstring: String = "",
    @SerialName("subfolder_mode") val subfolderMode: String = "ALL_SUBFOLDERS", // "ALL_SUBFOLDERS", "TOP_ONLY", "SELECT_SUBFOLDERS"
    @SerialName("selected_subfolders") val selectedSubfolders: List<String> = emptyList()
)

@Serializable
data class ScanRequest(
    val folders: List<FolderConfig>
)

@Serializable
data class TrashRequest(
    @SerialName("file_path") val filePath: String
)
