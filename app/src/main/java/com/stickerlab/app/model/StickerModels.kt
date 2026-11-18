package com.stickerlab.app.model

enum class StickerShape(val label: String) {
    FULL("Full"),
    ROUNDED("Rounded"),
    CIRCLE("Circle"),
    HEART("Heart"),
    STAR("Star")
}

data class StickerItem(
    val fileName: String,
    val emojis: List<String> = listOf("✨", "😎")
)

data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String = "StickerLab",
    val trayImageFile: String = "tray.png",
    val imageDataVersion: Int = 1,
    val stickers: List<StickerItem> = emptyList()
)

data class EditorState(
    val shape: StickerShape = StickerShape.ROUNDED,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val outlineWidth: Float = 10f,
    val outlineColor: Int = 0xFFFFFFFF.toInt(),
    val text: String = "",
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textSize: Float = 56f,
    val textOffsetY: Float = 0.82f,
    val videoFrameMs: Long = 0L,
    val videoDurationMs: Long = 0L
)
