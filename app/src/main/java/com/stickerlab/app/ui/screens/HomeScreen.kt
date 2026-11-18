package com.stickerlab.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stickerlab.app.data.StickerRepository
import com.stickerlab.app.model.StickerPack
import com.stickerlab.app.ui.theme.*
import com.stickerlab.app.whatsapp.WhatsAppBridge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    packs: List<StickerPack>,
    repository: StickerRepository,
    onCreatePack: (String) -> Unit,
    onDeletePack: (String) -> Unit,
    onOpenEditor: (StickerPack) -> Unit,
    onAddToWhatsApp: (StickerPack) -> Unit
) {
    val context = LocalContext.current
    var showNewPackDialog by remember { mutableStateOf(false) }
    var newPackName by remember { mutableStateOf("") }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WhatsAppGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = BgDark, modifier = Modifier.size(16.dp))
                        }
                        Text("StickerLab", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = { showNewPackDialog = true }) {
                        Text("+ Pack", color = WhatsAppGreen, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgDark)
            )
        },
        floatingActionButton = {
            packs.firstOrNull()?.let { firstPack ->
                ExtendedFloatingActionButton(
                    onClick = { onOpenEditor(firstPack) },
                    containerColor = WhatsAppGreen,
                    contentColor = Color(0xFF05180D),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("New Sticker", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            items(packs, key = { it.identifier }) { pack ->
                val isAdded = remember(pack.identifier, pack.imageDataVersion) {
                    WhatsAppBridge.isPackAddedToWhatsApp(context, pack.identifier)
                }
                PackCard(
                    pack = pack,
                    repository = repository,
                    isAdded = isAdded,
                    onAddSticker = { onOpenEditor(pack) },
                    onAddToWhatsApp = { onAddToWhatsApp(pack) },
                    onDeletePack = { onDeletePack(pack.identifier) }
                )
            }
        }
    }

    if (showNewPackDialog) {
        AlertDialog(
            onDismissRequest = { showNewPackDialog = false },
            containerColor = SurfaceCard,
            title = { Text("Create Sticker Pack", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPackName,
                    onValueChange = { newPackName = it },
                    placeholder = { Text("e.g. Friends Memes", color = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePack(newPackName.ifBlank { "My Pack" })
                        newPackName = ""
                        showNewPackDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = BgDark)
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPackDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun PackCard(
    pack: StickerPack,
    repository: StickerRepository,
    isAdded: Boolean,
    onAddSticker: () -> Unit,
    onAddToWhatsApp: () -> Unit,
    onDeletePack: () -> Unit
) {
    val canAddWhatsApp = pack.stickers.size >= 3

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pack.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = if (canAddWhatsApp) "${pack.stickers.size} stickers • Ready for WhatsApp"
                        else "${pack.stickers.size}/3 stickers • Add ${3 - pack.stickers.size} more for WhatsApp",
                        color = if (canAddWhatsApp) WhatsAppGreen else TextMuted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDeletePack) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete pack", tint = TextMuted)
                }
            }

            // Minimal Sticker Strip
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Surface(
                        onClick = onAddSticker,
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add sticker", tint = WhatsAppGreen)
                        }
                    }
                }
                items(pack.stickers, key = { it.fileName }) { sticker ->
                    val file = repository.getStickerFile(pack.identifier, sticker.fileName)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceElevated,
                        modifier = Modifier.size(72.dp)
                    ) {
                        AsyncImage(
                            model = file,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    }
                }
            }

            // Primary 1-Click Add to WhatsApp CTA
            Button(
                onClick = onAddToWhatsApp,
                enabled = canAddWhatsApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdded) SurfaceElevated else WhatsAppGreen,
                    contentColor = if (isAdded) WhatsAppGreen else BgDark,
                    disabledContainerColor = SurfaceElevated,
                    disabledContentColor = TextMuted
                )
            ) {
                if (isAdded) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Added to WhatsApp (Tap to Update)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                } else {
                    Text(
                        if (canAddWhatsApp) "Add to WhatsApp" else "Add 3 Stickers to Unlock WhatsApp",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
