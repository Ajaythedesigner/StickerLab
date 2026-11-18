package com.stickerlab.app.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.stickerlab.app.BuildConfig
import com.stickerlab.app.model.StickerPack
import java.io.File

object WhatsAppBridge {

    private const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    private const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
    private const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
    private const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"

    private const val CONSUMER_WHATSAPP_PACKAGE = "com.whatsapp"
    private const val SMB_WHATSAPP_PACKAGE = "com.whatsapp.w4b"
    private const val TELEGRAM_PACKAGE = "org.telegram.messenger"

    fun createAddPackIntent(pack: StickerPack): Intent {
        return Intent(ACTION_ENABLE_STICKER_PACK).apply {
            putExtra(EXTRA_STICKER_PACK_ID, pack.identifier)
            putExtra(EXTRA_STICKER_PACK_AUTHORITY, BuildConfig.CONTENT_PROVIDER_AUTHORITY)
            putExtra(EXTRA_STICKER_PACK_NAME, pack.name)
        }
    }

    fun isPackAddedToWhatsApp(context: Context, identifier: String): Boolean {
        return isWhitelistedInPackage(context, identifier, CONSUMER_WHATSAPP_PACKAGE) ||
            isWhitelistedInPackage(context, identifier, SMB_WHATSAPP_PACKAGE)
    }

    private fun isWhitelistedInPackage(context: Context, identifier: String, waPackage: String): Boolean {
        return try {
            val authority = "$waPackage.provider.sticker_whitelist_check"
            val queryUri = Uri.Builder()
                .scheme("content")
                .authority(authority)
                .appendPath("is_whitelisted")
                .appendQueryParameter("authority", BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                .appendQueryParameter("identifier", identifier)
                .build()

            context.contentResolver.query(queryUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex("result")
                    if (idx >= 0) return cursor.getInt(idx) == 1
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun shareToTelegram(context: Context, pngFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            pngFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Prefer Telegram directly if installed
            val pm = context.packageManager
            val hasTelegram = try {
                pm.getPackageInfo(TELEGRAM_PACKAGE, 0)
                true
            } catch (_: Exception) {
                false
            }
            if (hasTelegram) {
                setPackage(TELEGRAM_PACKAGE)
            }
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Sticker to Telegram"))
    }
}
