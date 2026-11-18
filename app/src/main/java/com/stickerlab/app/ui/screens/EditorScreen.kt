package com.stickerlab.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stickerlab.app.data.StickerRepository
import com.stickerlab.app.model.EditorState
import com.stickerlab.app.model.StickerPack
import com.stickerlab.app.model.StickerShape
import com.stickerlab.app.ui.theme.*
import com.stickerlab.app.whatsapp.WhatsAppBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    pack: StickerPack,
    repository: StickerRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editorState by remember { mutableStateOf(EditorState()) }
    var activeTab by remember { mutableIntStateOf(0) } // 0: Shape, 1: Text, 2: Adjust
    var isSaving by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            isVideo = mime.startsWith("video/")
            selectedUri = uri
            editorState = EditorState()
        }
    }

    // Load media bitmap whenever URI or video timestamp changes
    LaunchedEffect(selectedUri, editorState.videoFrameMs) {
        val uri = selectedUri ?: return@LaunchedEffect
        val (bmp, dur) = withContext(Dispatchers.IO) {
            repository.loadMediaBitmap(uri, isVideo, editorState.videoFrameMs)
        }
        if (bmp != null) {
            sourceBitmap = bmp
            if (dur > 0 && editorState.videoDurationMs != dur) {
                editorState = editorState.copy(videoDurationMs = dur)
            }
        }
    }

    // Re-render 512x512 preview whenever edits change
    LaunchedEffect(sourceBitmap, editorState) {
        val src = sourceBitmap ?: return@LaunchedEffect
        previewBitmap = withContext(Dispatchers.Default) {
            repository.renderStickerBitmap(src, editorState)
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sticker Editor", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(pack.name, color = TextMuted, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Pick media", tint = WhatsAppGreen)
                    }
                    if (previewBitmap != null) {
                        IconButton(onClick = {
                            previewBitmap?.let { bmp ->
                                val png = repository.exportForTelegramPng(bmp)
                                WhatsAppBridge.shareToTelegram(context, png)
                            }
                        }) {
                            Icon(Icons.Rounded.Send, contentDescription = "Share to Telegram", tint = TelegramSky)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Center 512x512 Interactive Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth(0.92f)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, rotate ->
                                editorState = editorState.copy(
                                    scale = (editorState.scale * zoom).coerceIn(0.4f, 3f),
                                    rotation = editorState.rotation + rotate,
                                    offsetX = editorState.offsetX + pan.x,
                                    offsetY = editorState.offsetY + pan.y
                                )
                            }
                        },
                    shape = RoundedCornerShape(26.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    val bmp = previewBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Sticker preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    mediaPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = WhatsAppGreen,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Tap to pick Photo, GIF or Video", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Pinch, drag & rotate directly here", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Minimal Bottom Editing Dock
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Minimal Pill Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgDark)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Shape", "Text", "Adjust").forEachIndexed { idx, title ->
                            val selected = activeTab == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) SurfaceElevated else Color.Transparent)
                                    .clickable { activeTab = idx }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    title,
                                    color = if (selected) TextPrimary else TextMuted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    when (activeTab) {
                        0 -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(StickerShape.entries) { shape ->
                                    val sel = editorState.shape == shape
                                    FilterChip(
                                        selected = sel,
                                        onClick = { editorState = editorState.copy(shape = shape) },
                                        label = { Text(shape.label, fontSize = 12.sp) }
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Border", color = TextMuted, fontSize = 12.sp)
                                Slider(
                                    value = editorState.outlineWidth,
                                    onValueChange = { editorState = editorState.copy(outlineWidth = it) },
                                    valueRange = 0f..22f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        1 -> {
                            OutlinedTextField(
                                value = editorState.text,
                                onValueChange = { editorState = editorState.copy(text = it) },
                                placeholder = { Text("Add caption (e.g. Semma Da!)", color = TextMuted, fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(0xFFFFFFFF, 0xFFFACC15, 0xFF25D366, 0xFF38BDF8, 0xFFFB7185).forEach { hex ->
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(hex))
                                            .clickable { editorState = editorState.copy(textColor = hex.toInt()) }
                                    )
                                }
                                Slider(
                                    value = editorState.textSize,
                                    onValueChange = { editorState = editorState.copy(textSize = it) },
                                    valueRange = 28f..96f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        2 -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Zoom", color = TextMuted, fontSize = 12.sp)
                                Slider(
                                    value = editorState.scale,
                                    onValueChange = { editorState = editorState.copy(scale = it) },
                                    valueRange = 0.5f..2.5f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (isVideo && editorState.videoDurationMs > 0L) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Frame", color = TextMuted, fontSize = 12.sp)
                                    Slider(
                                        value = editorState.videoFrameMs.toFloat(),
                                        onValueChange = { editorState = editorState.copy(videoFrameMs = it.toLong()) },
                                        valueRange = 0f..editorState.videoDurationMs.toFloat(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Save to Pack Button
                    Button(
                        onClick = {
                            val bmp = previewBitmap ?: return@Button
                            isSaving = true
                            repository.saveStickerToPack(pack.identifier, bmp)
                            isSaving = false
                            onSaved()
                        },
                        enabled = previewBitmap != null && !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = BgDark)
                    ) {
                        Text(
                            if (isSaving) "Saving..." else "Save Sticker to Pack",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
