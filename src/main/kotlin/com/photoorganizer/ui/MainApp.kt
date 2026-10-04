package com.photoorganizer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoorganizer.models.DuplicateGroup
import com.photoorganizer.models.Photo
import com.photoorganizer.services.ApiService
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO

enum class NavItem(val title: String) {
    FOLDERS("フォルダ管理"),
    PHOTOS("写真ギャラリー"),
    DUPLICATES("重複写真"),
    BLUR("ピンボケ・ブレ検出")
}

@Composable
fun MainApp() {
    var currentNav by remember { mutableStateOf(NavItem.PHOTOS) }
    var registeredFolders by remember { mutableStateOf(listOf<String>()) }
    var photos by remember { mutableStateOf(listOf<Photo>()) }
    var duplicates by remember { mutableStateOf(listOf<DuplicateGroup>()) }
    var blurryPhotos by remember { mutableStateOf(listOf<Photo>()) }
    var selectedPhoto by remember { mutableStateOf<Photo?>(null) }

    // Scan State
    var isScanning by remember { mutableStateOf(false) }
    var scanProgressCurrent by remember { mutableStateOf(0) }
    var scanProgressTotal by remember { mutableStateOf(0) }
    var scanProgressMessage by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    fun refreshPhotos() {
        coroutineScope.launch {
            photos = ApiService.fetchPhotos()
            duplicates = ApiService.fetchExactDuplicates()
            blurryPhotos = ApiService.fetchBlurryPhotos()
        }
    }

    LaunchedEffect(Unit) {
        refreshPhotos()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        NavigationRailItem(
                            selected = currentNav == NavItem.PHOTOS,
                            onClick = { currentNav = NavItem.PHOTOS; refreshPhotos() },
                            icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            label = { Text(NavItem.PHOTOS.title, textAlign = TextAlign.Center) },
                            alwaysShowLabel = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        NavigationRailItem(
                            selected = currentNav == NavItem.DUPLICATES,
                            onClick = { currentNav = NavItem.DUPLICATES; refreshPhotos() },
                            icon = { Icon(Icons.Default.CopyAll, contentDescription = null) },
                            label = { Text(NavItem.DUPLICATES.title, textAlign = TextAlign.Center) },
                            alwaysShowLabel = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        NavigationRailItem(
                            selected = currentNav == NavItem.BLUR,
                            onClick = { currentNav = NavItem.BLUR; refreshPhotos() },
                            icon = { Icon(Icons.Default.BlurOn, contentDescription = null) },
                            label = { Text(NavItem.BLUR.title, textAlign = TextAlign.Center) },
                            alwaysShowLabel = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Main Content Workspace
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Scan Progress Indicator Bar
                        if (isScanning || scanProgressMessage.isNotEmpty()) {
                            ScanProgressBanner(
                                isScanning = isScanning,
                                current = scanProgressCurrent,
                                total = scanProgressTotal,
                                message = scanProgressMessage
                            )
                        }

                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                when (currentNav) {
                                    NavItem.FOLDERS -> FolderManagementView(
                                        folders = registeredFolders,
                                        onAddFolder = { folder ->
                                            if (folder.isNotBlank() && !registeredFolders.contains(folder)) {
                                                registeredFolders = registeredFolders + folder
                                            }
                                        },
                                        onRemoveFolder = { folder ->
                                            registeredFolders = registeredFolders.filter { it != folder }
                                        },
                                        onStartScan = {
                                            isScanning = true
                                            coroutineScope.launch {
                                                ApiService.startScan(registeredFolders).collect { progress ->
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
                                        onSelectPhoto = { selectedPhoto = it }
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
        }
    }
}

@Composable
fun ScanProgressBanner(isScanning: Boolean, current: Int, total: Int, message: String) {
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
            }
        }
    }
}

@Composable
fun FolderManagementView(
    folders: List<String>,
    onAddFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onStartScan: () -> Unit,
    isScanning: Boolean
) {
    var newFolderPath by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("対象フォルダ管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("スキャン・整理対象となるローカルフォルダを登録してください。", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newFolderPath,
                onValueChange = { newFolderPath = it },
                label = { Text("フォルダの絶対パス") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onAddFolder(newFolderPath)
                    newFolderPath = ""
                },
                enabled = newFolderPath.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("フォルダを追加")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            if (folders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("登録されたフォルダはありません。", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(folders) { folder ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(folder, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { onRemoveFolder(folder) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartScan,
            enabled = folders.isNotEmpty() && !isScanning,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isScanning) "スキャン中..." else "写真のスキャンを開始")
        }
    }
}

@Composable
fun PhotoGridView(
    photos: List<Photo>,
    selectedPhoto: Photo?,
    onSelectPhoto: (Photo) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("写真ギャラリー (${photos.size}枚)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("写真が見つかりません。フォルダを追加してスキャンを実行してください。", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
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
        }
    }
}

@Composable
fun PhotoCardItem(photo: Photo, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        modifier = Modifier
            .aspectRatio(1f)
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
                                            modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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
            IconButton(onClick = onClose) {
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
            modifier = Modifier.fillMaxWidth()
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
