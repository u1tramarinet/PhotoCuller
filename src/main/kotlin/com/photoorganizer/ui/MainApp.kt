package com.photoorganizer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoorganizer.models.DuplicateGroup
import com.photoorganizer.models.Photo
import com.photoorganizer.models.ScanRuleSet
import com.photoorganizer.services.ApiService
import com.photoorganizer.services.ConfigManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO
import javax.swing.JFileChooser

enum class NavItem(val title: String) {
    FOLDERS("ルール設定"),
    PHOTOS("写真ギャラリー"),
    DUPLICATES("重複写真"),
    BLUR("ピンボケ・ブレ検出")
}

enum class SortField(val displayName: String, val key: String) {
    TAKEN_AT("撮影日時", "taken_at"),
    FILE_NAME("ファイル名", "file_name"),
    FILE_SIZE("ファイルサイズ", "file_size"),
    BLUR_SCORE("ブレスコア", "blur_score")
}

enum class SortOrder(val displayName: String, val key: String) {
    DESC("降順 ▼", "DESC"),
    ASC("昇順 ▲", "ASC")
}

enum class GroupByOption(val displayName: String) {
    NONE("なし (フラット表示)"),
    FOLDER("フォルダごと"),
    EXTENSION("拡張子ごと"),
    SIZE_CATEGORY("ファイルサイズごと")
}

enum class SizeFilterOption(val displayName: String, val minBytes: Long?, val maxBytes: Long?) {
    ALL("すべて", null, null),
    SMALL("小 (<1MB)", null, 1024 * 1024 - 1),
    MEDIUM("中 (1MB-5MB)", 1024 * 1024, 5 * 1024 * 1024 - 1),
    LARGE("大 (5MB-20MB)", 5 * 1024 * 1024, 20 * 1024 * 1024 - 1),
    EXTRA_LARGE("特大 (>20MB)", 20 * 1024 * 1024, null);

    companion object {
        fun categorize(bytes: Long): String {
            return when {
                bytes < 1024 * 1024 -> "小 (<1MB)"
                bytes < 5 * 1024 * 1024 -> "中 (1MB-5MB)"
                bytes < 20 * 1024 * 1024 -> "大 (5MB-20MB)"
                else -> "特大 (>20MB)"
            }
        }
    }
}

fun getRuleSetValidationErrors(ruleSet: ScanRuleSet): List<String> {
    val errors = mutableListOf<String>()
    if (ruleSet.folderPaths.isEmpty()) {
        errors.add("対象フォルダが選択されていません。")
    }
    if (ruleSet.fileFilterType == "CUSTOM_EXT" && ruleSet.customExtensions.isEmpty()) {
        errors.add("所定の拡張子が指定されていません。")
    }
    if (ruleSet.fileFilterType == "NAME_CONTAINS" && ruleSet.nameSubstring.isBlank()) {
        errors.add("検索文字列が入力されていません。")
    }
    return errors
}

@Composable
fun MainApp() {
    var currentNav by remember { mutableStateOf(NavItem.PHOTOS) }
    var ruleSets by remember { mutableStateOf(ConfigManager.loadRuleSets()) }
    var photos by remember { mutableStateOf(listOf<Photo>()) }
    var duplicates by remember { mutableStateOf(listOf<DuplicateGroup>()) }
    var blurryPhotos by remember { mutableStateOf(listOf<Photo>()) }
    var selectedPhoto by remember { mutableStateOf<Photo?>(null) }

    // Filtering, Sorting, Grouping State for Photo Gallery
    var selectedFolderFilter by remember { mutableStateOf<String?>(null) }
    var selectedExtensionFilter by remember { mutableStateOf<String?>(null) }
    var selectedSizeFilter by remember { mutableStateOf(SizeFilterOption.ALL) }
    var selectedSortField by remember { mutableStateOf(SortField.TAKEN_AT) }
    var selectedSortOrder by remember { mutableStateOf(SortOrder.DESC) }
    var selectedGroupBy by remember { mutableStateOf(GroupByOption.NONE) }

    // Toast State
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Scan State
    var isScanning by remember { mutableStateOf(false) }
    var isBannerVisible by remember { mutableStateOf(false) }
    var scanProgressCurrent by remember { mutableStateOf(0) }
    var scanProgressTotal by remember { mutableStateOf(0) }
    var scanProgressMessage by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun showToast(msg: String) {
        toastMessage = msg
        coroutineScope.launch {
            delay(2500)
            if (toastMessage == msg) {
                toastMessage = null
            }
        }
    }

    fun updateAndSaveRuleSets(newRuleSets: List<ScanRuleSet>, showToastNotification: Boolean = true) {
        ruleSets = newRuleSets
        ConfigManager.saveRuleSets(newRuleSets)
        if (showToastNotification) {
            showToast("設定を更新・保存しました")
        }
    }

    fun refreshPhotos() {
        coroutineScope.launch {
            photos = ApiService.fetchPhotos(
                folderPath = selectedFolderFilter,
                extension = selectedExtensionFilter,
                minSize = selectedSizeFilter.minBytes,
                maxSize = selectedSizeFilter.maxBytes,
                sortBy = selectedSortField.key,
                order = selectedSortOrder.key
            )
            duplicates = ApiService.fetchExactDuplicates()
            blurryPhotos = ApiService.fetchBlurryPhotos()
        }
    }

    LaunchedEffect(
        selectedFolderFilter,
        selectedExtensionFilter,
        selectedSizeFilter,
        selectedSortField,
        selectedSortOrder
    ) {
        refreshPhotos()
    }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusManager.clearFocus()
                    }
                },
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Navigation Sidebar / Rail
                    NavigationRail(
                        modifier = Modifier.width(200.dp).fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight().fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "フォト管",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                            )

                            NavigationRailItem(
                                selected = currentNav == NavItem.FOLDERS,
                                onClick = { currentNav = NavItem.FOLDERS },
                                icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                label = { Text(NavItem.FOLDERS.title, textAlign = TextAlign.Center) },
                                alwaysShowLabel = true,
                                modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            NavigationRailItem(
                                selected = currentNav == NavItem.PHOTOS,
                                onClick = { currentNav = NavItem.PHOTOS; refreshPhotos() },
                                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                label = { Text(NavItem.PHOTOS.title, textAlign = TextAlign.Center) },
                                alwaysShowLabel = true,
                                modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            NavigationRailItem(
                                selected = currentNav == NavItem.DUPLICATES,
                                onClick = { currentNav = NavItem.DUPLICATES; refreshPhotos() },
                                icon = { Icon(Icons.Default.CopyAll, contentDescription = null) },
                                label = { Text(NavItem.DUPLICATES.title, textAlign = TextAlign.Center) },
                                alwaysShowLabel = true,
                                modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            NavigationRailItem(
                                selected = currentNav == NavItem.BLUR,
                                onClick = { currentNav = NavItem.BLUR; refreshPhotos() },
                                icon = { Icon(Icons.Default.BlurOn, contentDescription = null) },
                                label = { Text(NavItem.BLUR.title, textAlign = TextAlign.Center) },
                                alwaysShowLabel = true,
                                modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Main Content Workspace
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Scan Progress Indicator Bar
                            if (isBannerVisible) {
                                ScanProgressBanner(
                                    isScanning = isScanning,
                                    current = scanProgressCurrent,
                                    total = scanProgressTotal,
                                    message = scanProgressMessage,
                                    onClose = { isBannerVisible = false }
                                )
                            }

                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    when (currentNav) {
                                        NavItem.FOLDERS -> FolderManagementView(
                                            ruleSets = ruleSets,
                                            onAddRuleSet = {
                                                val nextIndex = ruleSets.size + 1
                                                updateAndSaveRuleSets(ruleSets + ScanRuleSet(name = "ルールセット $nextIndex"))
                                            },
                                            onUpdateRuleSet = { updatedRs ->
                                                updateAndSaveRuleSets(ruleSets.map { if (it.id == updatedRs.id) updatedRs else it })
                                            },
                                            onRemoveRuleSet = { id ->
                                                updateAndSaveRuleSets(ruleSets.filter { it.id != id })
                                            },
                                            onStartScan = {
                                                isScanning = true
                                                isBannerVisible = true
                                                coroutineScope.launch {
                                                    ApiService.startScan(ruleSets).collect { progress ->
                                                        scanProgressCurrent = progress.current
                                                        scanProgressTotal = progress.total
                                                        scanProgressMessage = progress.message
                                                        if (progress.type == "complete" || progress.type == "error") {
                                                            isScanning = false
                                                            refreshPhotos()
                                                        }
                                                    }
                                                }
                                            },
                                            isScanning = isScanning
                                        )

                                        NavItem.PHOTOS -> PhotoGridView(
                                            photos = photos,
                                            selectedPhoto = selectedPhoto,
                                            onSelectPhoto = { selectedPhoto = it },
                                            ruleSets = ruleSets,
                                            selectedFolderFilter = selectedFolderFilter,
                                            onFolderFilterChange = { selectedFolderFilter = it },
                                            selectedExtensionFilter = selectedExtensionFilter,
                                            onExtensionFilterChange = { selectedExtensionFilter = it },
                                            selectedSizeFilter = selectedSizeFilter,
                                            onSizeFilterChange = { selectedSizeFilter = it },
                                            selectedSortField = selectedSortField,
                                            onSortFieldChange = { selectedSortField = it },
                                            selectedSortOrder = selectedSortOrder,
                                            onSortOrderChange = { selectedSortOrder = it },
                                            selectedGroupBy = selectedGroupBy,
                                            onGroupByChange = { selectedGroupBy = it }
                                        )

                                        NavItem.DUPLICATES -> DuplicatesView(
                                            duplicates = duplicates,
                                            onSelectPhoto = { selectedPhoto = it },
                                            onTrashPhoto = { photo ->
                                                coroutineScope.launch {
                                                    ApiService.trashPhoto(photo.filePath)
                                                    refreshPhotos()
                                                }
                                            }
                                        )

                                        NavItem.BLUR -> BlurView(
                                            blurryPhotos = blurryPhotos,
                                            onSelectPhoto = { selectedPhoto = it },
                                            onTrashPhoto = { photo ->
                                                coroutineScope.launch {
                                                    ApiService.trashPhoto(photo.filePath)
                                                    refreshPhotos()
                                                }
                                            }
                                        )
                                    }
                                }

                                // Photo Detail Inspector Pane
                                if (selectedPhoto != null) {
                                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    PhotoDetailInspector(
                                        photo = selectedPhoto!!,
                                        onClose = { selectedPhoto = null },
                                        onTrash = { photo ->
                                            coroutineScope.launch {
                                                ApiService.trashPhoto(photo.filePath)
                                                selectedPhoto = null
                                                refreshPhotos()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Settings Update Toast Overlay
                AnimatedVisibility(
                    visible = toastMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = toastMessage ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanProgressBanner(
    isScanning: Boolean,
    current: Int,
    total: Int,
    message: String,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isScanning) "スキャン処理中..." else "スキャン完了",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$message ${if (total > 0) "($current / $total)" else ""}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (total > 0 && isScanning) {
                LinearProgressIndicator(
                    progress = { current.toFloat() / total.toFloat() },
                    modifier = Modifier.width(150.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(onClick = onClose, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Default.Close, contentDescription = "閉じる")
            }
        }
    }
}

private fun openDirectoryPickerDialog(): String? {
    return try {
        System.setProperty("apple.awt.UIElement", "true")
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = "スキャン対象フォルダを選択"
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
            chooser.selectedFile.absolutePath
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

fun getExtensionsInDirectories(folderPaths: List<String>): List<String> {
    val extensions = mutableSetOf<String>()
    folderPaths.forEach { path ->
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            dir.walkTopDown().maxDepth(3).forEach { file ->
                if (file.isFile && file.extension.isNotBlank()) {
                    extensions.add(file.extension.lowercase())
                }
            }
        }
    }
    return extensions.sorted()
}

@Composable
fun FolderManagementView(
    ruleSets: List<ScanRuleSet>,
    onAddRuleSet: () -> Unit,
    onUpdateRuleSet: (ScanRuleSet) -> Unit,
    onRemoveRuleSet: (String) -> Unit,
    onStartScan: () -> Unit,
    isScanning: Boolean
) {
    val allErrors = ruleSets.flatMap { getRuleSetValidationErrors(it) }
    val canStartScan = ruleSets.isNotEmpty() && allErrors.isEmpty() && !isScanning

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("写真ファイルスキャンルール設定", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("対象フォルダと抽出フィルタの組み合わせをルールセットとして管理できます。", style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onAddRuleSet, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("新しいルールセットを追加")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            if (ruleSets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ルールセットがありません。「新しいルールセットを追加」を押してください。", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(ruleSets) { rs ->
                        RuleSetCard(
                            ruleSet = rs,
                            onUpdateRuleSet = onUpdateRuleSet,
                            onRemoveRuleSet = { onRemoveRuleSet(rs.id) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (allErrors.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "スキャンを開始するには、設定されていない条件を指定してください (${allErrors.size}件の注意箇所)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Button(
            onClick = onStartScan,
            enabled = canStartScan,
            modifier = Modifier.fillMaxWidth().height(48.dp).pointerHoverIcon(if (canStartScan) PointerIcon.Hand else PointerIcon.Default)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isScanning) "スキャン中..." else "全ルールセットの写真スキャンを開始")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RuleSetCard(
    ruleSet: ScanRuleSet,
    onUpdateRuleSet: (ScanRuleSet) -> Unit,
    onRemoveRuleSet: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    var customExtsText by remember(ruleSet) { mutableStateOf(ruleSet.customExtensions.joinToString(", ")) }

    val errors = getRuleSetValidationErrors(ruleSet)

    val detectedExtensions = remember(ruleSet.folderPaths) {
        getExtensionsInDirectories(ruleSet.folderPaths)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = ruleSet.name,
                    onValueChange = { newName -> onUpdateRuleSet(ruleSet.copy(name = newName)) },
                    label = { Text("ルールセット名") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "開閉"
                    )
                }
                IconButton(onClick = onRemoveRuleSet, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                    Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error)
                }
            }

            // Error warnings if any
            if (errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                errors.forEach { err ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Summary when collapsed
            if (!isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                val subfolderText = if (ruleSet.includeSubfolders) "ファイルと子フォルダ" else "ファイルのみ"
                val filterText = when (ruleSet.fileFilterType) {
                    "ALL" -> "すべてのファイル"
                    "CUSTOM_EXT" -> "所定の拡張子 (${if (ruleSet.customExtensions.isEmpty()) "未指定" else ruleSet.customExtensions.joinToString(", ")})"
                    "NAME_CONTAINS" -> "特定文字列 (「${ruleSet.nameSubstring}」を含む)"
                    else -> "すべてのファイル"
                }
                Text(
                    text = "対象フォルダ: ${ruleSet.folderPaths.size}件 | 範囲: $subfolderText | 抽出: $filterText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 32.dp)
                )
            } else {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Section 1: Assigned Folder Paths (N:1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("対象フォルダパス一覧", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            val path = openDirectoryPickerDialog()
                            if (path != null && !ruleSet.folderPaths.contains(path)) {
                                onUpdateRuleSet(ruleSet.copy(folderPaths = ruleSet.folderPaths + path))
                            }
                        },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("参照 (フォルダを追加)")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (ruleSet.folderPaths.isEmpty()) {
                    Text("※ フォルダが指定されていません。「参照」ボタンからフォルダを追加してください。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ruleSet.folderPaths.forEach { path ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(path, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { onUpdateRuleSet(ruleSet.copy(folderPaths = ruleSet.folderPaths - path)) },
                                    modifier = Modifier.size(24.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "削除", tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Subfolder Inclusion Setting with Segmented Buttons
                Text("子フォルダの対象設定", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = ruleSet.includeSubfolders,
                        onClick = { onUpdateRuleSet(ruleSet.copy(includeSubfolders = true)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("ファイルと子フォルダ")
                    }
                    SegmentedButton(
                        selected = !ruleSet.includeSubfolders,
                        onClick = { onUpdateRuleSet(ruleSet.copy(includeSubfolders = false)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("ファイルのみ")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: File Filtering Setting with Segmented Buttons
                Text("対象ファイルの抽出条件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = ruleSet.fileFilterType == "ALL",
                        onClick = { onUpdateRuleSet(ruleSet.copy(fileFilterType = "ALL")) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("すべてのファイル")
                    }
                    SegmentedButton(
                        selected = ruleSet.fileFilterType == "CUSTOM_EXT",
                        onClick = { onUpdateRuleSet(ruleSet.copy(fileFilterType = "CUSTOM_EXT")) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("所定の拡張子のみ")
                    }
                    SegmentedButton(
                        selected = ruleSet.fileFilterType == "NAME_CONTAINS",
                        onClick = { onUpdateRuleSet(ruleSet.copy(fileFilterType = "NAME_CONTAINS")) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("特定文字列を含む")
                    }
                }

                if (ruleSet.fileFilterType == "CUSTOM_EXT") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (detectedExtensions.isNotEmpty()) {
                            Text("割り当てフォルダ内の検出拡張子から選択:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                detectedExtensions.forEach { ext ->
                                    val isSelected = ruleSet.customExtensions.contains(ext)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val newExts = if (isSelected) ruleSet.customExtensions - ext else ruleSet.customExtensions + ext
                                            customExtsText = newExts.joinToString(", ")
                                            onUpdateRuleSet(ruleSet.copy(customExtensions = newExts))
                                        },
                                        label = { Text(".$ext") },
                                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = customExtsText,
                            onValueChange = { text ->
                                customExtsText = text
                                val list = text.split(",").map { it.trim().removePrefix(".") }.filter { it.isNotEmpty() }
                                onUpdateRuleSet(ruleSet.copy(customExtensions = list))
                            },
                            label = { Text("拡張子の手入力 (カンマ区切り: jpg, png, webp)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = ruleSet.customExtensions.isEmpty()
                        )
                    }
                }

                if (ruleSet.fileFilterType == "NAME_CONTAINS") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ruleSet.nameSubstring,
                        onValueChange = { text ->
                            onUpdateRuleSet(ruleSet.copy(nameSubstring = text))
                        },
                        label = { Text("検索文字列 (例: IMG_)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = ruleSet.nameSubstring.isBlank()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoGridView(
    photos: List<Photo>,
    selectedPhoto: Photo?,
    onSelectPhoto: (Photo) -> Unit,
    ruleSets: List<ScanRuleSet>,
    selectedFolderFilter: String?,
    onFolderFilterChange: (String?) -> Unit,
    selectedExtensionFilter: String?,
    onExtensionFilterChange: (String?) -> Unit,
    selectedSizeFilter: SizeFilterOption,
    onSizeFilterChange: (SizeFilterOption) -> Unit,
    selectedSortField: SortField,
    onSortFieldChange: (SortField) -> Unit,
    selectedSortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    selectedGroupBy: GroupByOption,
    onGroupByChange: (GroupByOption) -> Unit
) {
    // Extract available folders and extensions from current photos or ruleSets
    val availableFolders = remember(photos, ruleSets) {
        val foldersFromPhotos = photos.map { it.folderPath }
        val foldersFromRules = ruleSets.flatMap { it.folderPaths }
        (foldersFromPhotos + foldersFromRules).filter { it.isNotBlank() }.distinct().sorted()
    }

    val availableExtensions = remember(photos) {
        photos.map { File(it.filePath).extension.lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    // Dropdown Expansion States
    var folderMenuExpanded by remember { mutableStateOf(false) }
    var extMenuExpanded by remember { mutableStateOf(false) }
    var sizeMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "写真ギャラリー (${photos.size}枚)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Control Panel: Filters, Sorting, Grouping
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 1. Folder Filter Dropdown
                    Box {
                        AssistChip(
                            onClick = { folderMenuExpanded = true },
                            label = {
                                Text("フォルダ: ${selectedFolderFilter?.let { File(it).name.ifEmpty { it } } ?: "すべて"}")
                            },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        )
                        DropdownMenu(
                            expanded = folderMenuExpanded,
                            onDismissRequest = { folderMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("すべてのフォルダ") },
                                onClick = {
                                    onFolderFilterChange(null)
                                    folderMenuExpanded = false
                                }
                            )
                            availableFolders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder) },
                                    onClick = {
                                        onFolderFilterChange(folder)
                                        folderMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 2. Extension Filter Dropdown
                    Box {
                        AssistChip(
                            onClick = { extMenuExpanded = true },
                            label = {
                                Text("拡張子: ${selectedExtensionFilter?.let { ".$it" } ?: "すべて"}")
                            },
                            leadingIcon = { Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        )
                        DropdownMenu(
                            expanded = extMenuExpanded,
                            onDismissRequest = { extMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("すべての拡張子") },
                                onClick = {
                                    onExtensionFilterChange(null)
                                    extMenuExpanded = false
                                }
                            )
                            availableExtensions.forEach { ext ->
                                DropdownMenuItem(
                                    text = { Text(".$ext") },
                                    onClick = {
                                        onExtensionFilterChange(ext)
                                        extMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 3. File Size Filter Dropdown
                    Box {
                        AssistChip(
                            onClick = { sizeMenuExpanded = true },
                            label = {
                                Text("サイズ: ${selectedSizeFilter.displayName}")
                            },
                            leadingIcon = { Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        )
                        DropdownMenu(
                            expanded = sizeMenuExpanded,
                            onDismissRequest = { sizeMenuExpanded = false }
                        ) {
                            SizeFilterOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        onSizeFilterChange(option)
                                        sizeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 4. Sort Field & Order
                    Box {
                        AssistChip(
                            onClick = { sortMenuExpanded = true },
                            label = {
                                Text("ソート: ${selectedSortField.displayName} (${selectedSortOrder.displayName})")
                            },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        )
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortField.entries.forEach { field ->
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text("${field.displayName} (${order.displayName})") },
                                        onClick = {
                                            onSortFieldChange(field)
                                            onSortOrderChange(order)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 5. Grouping Option Dropdown
                    Box {
                        FilterChip(
                            selected = selectedGroupBy != GroupByOption.NONE,
                            onClick = { groupMenuExpanded = true },
                            label = { Text("グループ化: ${selectedGroupBy.displayName}") },
                            leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        )
                        DropdownMenu(
                            expanded = groupMenuExpanded,
                            onDismissRequest = { groupMenuExpanded = false }
                        ) {
                            GroupByOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        onGroupByChange(option)
                                        groupMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Reset Filter Button if any filter active
                    if (selectedFolderFilter != null || selectedExtensionFilter != null || selectedSizeFilter != SizeFilterOption.ALL) {
                        IconButton(
                            onClick = {
                                onFolderFilterChange(null)
                                onExtensionFilterChange(null)
                                onSizeFilterChange(SizeFilterOption.ALL)
                            },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.Default.FilterListOff, contentDescription = "フィルター解除", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("条件に一致する写真が見つかりません。スキャンを実行するかフィルタ条件を緩和してください。", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            if (selectedGroupBy == GroupByOption.NONE) {
                // Flat Grid Display
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(photos) { photo ->
                        PhotoCardItem(
                            photo = photo,
                            isSelected = selectedPhoto?.filePath == photo.filePath,
                            onClick = { onSelectPhoto(photo) }
                        )
                    }
                }
            } else {
                // Grouped Display using LazyColumn + FlowRow / Row items
                val groupedPhotos = remember(photos, selectedGroupBy) {
                    when (selectedGroupBy) {
                        GroupByOption.FOLDER -> photos.groupBy { it.folderPath }
                        GroupByOption.EXTENSION -> photos.groupBy { File(it.filePath).extension.lowercase().ifEmpty { "なし" } }
                        GroupByOption.SIZE_CATEGORY -> photos.groupBy { SizeFilterOption.categorize(it.fileSize) }
                        GroupByOption.NONE -> mapOf("すべての写真" to photos)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    groupedPhotos.forEach { (groupKey, groupPhotoList) ->
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val groupTitle = when (selectedGroupBy) {
                                        GroupByOption.FOLDER -> "📁 フォルダ: $groupKey"
                                        GroupByOption.EXTENSION -> "📄 拡張子: .$groupKey"
                                        GroupByOption.SIZE_CATEGORY -> "📏 サイズ: $groupKey"
                                        else -> groupKey
                                    }
                                    Text(
                                        text = groupTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${groupPhotoList.size}枚",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                groupPhotoList.forEach { photo ->
                                    Box(modifier = Modifier.size(140.dp)) {
                                        PhotoCardItem(
                                            photo = photo,
                                            isSelected = selectedPhoto?.filePath == photo.filePath,
                                            onClick = { onSelectPhoto(photo) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoCardItem(photo: Photo, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onClick() }
            .border(2.dp, borderColor, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LocalImageThumbnail(
                imagePath = photo.thumbnailPath ?: photo.filePath,
                modifier = Modifier.fillMaxSize()
            )

            // Blur Score Badge
            if (photo.blurScore != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "スコア: ${photo.blurScore.toInt()}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DuplicatesView(
    duplicates: List<DuplicateGroup>,
    onSelectPhoto: (Photo) -> Unit,
    onTrashPhoto: (Photo) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("完全一致の重複写真 (${duplicates.size}グループ)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (duplicates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("重複している写真は見つかりませんでした。", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(duplicates) { group ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("ハッシュ: ${group.hash.take(16)}...", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                group.photos.forEach { photo ->
                                    Box(modifier = Modifier.size(120.dp)) {
                                        PhotoCardItem(photo = photo, isSelected = false, onClick = { onSelectPhoto(photo) })
                                        IconButton(
                                            onClick = { onTrashPhoto(photo) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                .pointerHoverIcon(PointerIcon.Hand)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "ゴミ箱へ移動", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlurView(
    blurryPhotos: List<Photo>,
    onSelectPhoto: (Photo) -> Unit,
    onTrashPhoto: (Photo) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ピンボケ・ブレ写真候補 (${blurryPhotos.size}枚)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (blurryPhotos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ブレスコアの低い写真は検出されませんでした。", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(blurryPhotos) { photo ->
                    PhotoCardItem(
                        photo = photo,
                        isSelected = false,
                        onClick = { onSelectPhoto(photo) }
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoDetailInspector(
    photo: Photo,
    onClose: () -> Unit,
    onTrash: (Photo) -> Unit
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("写真の詳細情報", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Default.Close, contentDescription = "閉じる")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp))) {
            LocalImageThumbnail(imagePath = photo.filePath, modifier = Modifier.fillMaxSize())
        }

        Spacer(modifier = Modifier.height(16.dp))

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = photo.takenAt?.let { dateFormat.format(Date(it * 1000L)) } ?: "N/A"

        InspectorDetailRow("ファイル名", File(photo.filePath).name)
        InspectorDetailRow("パス", photo.filePath)
        InspectorDetailRow("解像度", "${photo.width ?: 0} x ${photo.height ?: 0}")
        InspectorDetailRow("ファイルサイズ", "${photo.fileSize / 1024} KB")
        InspectorDetailRow("撮影日時", dateStr)
        InspectorDetailRow("ブレスコア", photo.blurScore?.let { String.format("%.2f", it) } ?: "N/A")
        InspectorDetailRow("完全一致ハッシュ", photo.exactHash?.take(12) ?: "N/A")
        InspectorDetailRow("知覚ハッシュ(pHash)", photo.phash ?: "N/A")

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onTrash(photo) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ゴミ箱へ移動")
        }
    }
}

@Composable
fun InspectorDetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun LocalImageThumbnail(imagePath: String, modifier: Modifier = Modifier) {
    var imageBitmap by remember(imagePath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(imagePath) {
        try {
            val file = File(imagePath)
            if (file.exists()) {
                val bufferedImage = ImageIO.read(file)
                if (bufferedImage != null) {
                    imageBitmap = bufferedImage.toComposeImageBitmap()
                }
            }
        } catch (e: Exception) {
            imageBitmap = null
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray)
        }
    }
}
