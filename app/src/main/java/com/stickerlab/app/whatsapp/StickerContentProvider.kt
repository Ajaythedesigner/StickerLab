package com.stickerlab.app.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.stickerlab.app.BuildConfig
import com.stickerlab.app.data.StickerRepository
import com.stickerlab.app.model.StickerPack

/**
 * Official WhatsApp Sticker ContentProvider implementation.
 * WhatsApp queries this provider to fetch pack metadata and WebP sticker files.
 */
class StickerContentProvider : ContentProvider() {

    companion object {
        const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
        const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
        const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
        const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
        const val ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link"
        const val IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link"
        const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
        const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
        const val IMAGE_DATA_VERSION = "image_data_version"
        const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
        const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

        const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
        const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji"

        private const val METADATA_CODE = 1
        private const val METADATA_CODE_FOR_SINGLE_PACK = 2
        private const val STICKERS_CODE = 3
        private const val STICKERS_ASSET_CODE = 4
    }

    private lateinit var repository: StickerRepository
    private val matcher = UriMatcher(UriMatcher.NO_MATCH)

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        repository = StickerRepository(ctx)
        val authority = BuildConfig.CONTENT_PROVIDER_AUTHORITY
        matcher.addURI(authority, "metadata", METADATA_CODE)
        matcher.addURI(authority, "metadata/*", METADATA_CODE_FOR_SINGLE_PACK)
        matcher.addURI(authority, "stickers/*", STICKERS_CODE)
        matcher.addURI(authority, "stickers_asset/*/*", STICKERS_ASSET_CODE)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (matcher.match(uri)) {
            METADATA_CODE -> buildPackCursor(uri, repository.getPacks())
            METADATA_CODE_FOR_SINGLE_PACK -> {
                val id = uri.lastPathSegment ?: return null
                val pack = repository.getPack(id) ?: return null
                buildPackCursor(uri, listOf(pack))
            }
            STICKERS_CODE -> {
                val id = uri.lastPathSegment ?: return null
                val pack = repository.getPack(id) ?: return null
                buildStickersCursor(uri, pack)
            }
            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != STICKERS_ASSET_CODE) return null
        val segments = uri.pathSegments
        if (segments.size < 3) return null
        val packId = segments[segments.size - 2]
        val fileName = segments[segments.size - 1]
        val file = repository.getStickerFile(packId, fileName)
        if (!file.exists()) return null
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String {
        val auth = BuildConfig.CONTENT_PROVIDER_AUTHORITY
        return when (matcher.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$auth.metadata"
            METADATA_CODE_FOR_SINGLE_PACK -> "vnd.android.cursor.item/vnd.$auth.metadata"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$auth.stickers"
            STICKERS_ASSET_CODE -> {
                if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    private fun buildPackCursor(uri: Uri, packs: List<StickerPack>): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                STICKER_PACK_IDENTIFIER_IN_QUERY,
                STICKER_PACK_NAME_IN_QUERY,
                STICKER_PACK_PUBLISHER_IN_QUERY,
                STICKER_PACK_ICON_IN_QUERY,
                ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
                IOS_APP_DOWNLOAD_LINK_IN_QUERY,
                PUBLISHER_EMAIL,
                PUBLISHER_WEBSITE,
                PRIVACY_POLICY_WEBSITE,
                LICENSE_AGREEMENT_WEBSITE,
                IMAGE_DATA_VERSION,
                AVOID_CACHE,
                ANIMATED_STICKER_PACK
            )
        )
        packs.forEach { p ->
            cursor.addRow(
                arrayOf(
                    p.identifier,
                    p.name,
                    p.publisher,
                    p.trayImageFile,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    p.imageDataVersion.toString(),
                    0,
                    0
                )
            )
        }
        cursor.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    private fun buildStickersCursor(uri: Uri, pack: StickerPack): Cursor {
        val cursor = MatrixCursor(arrayOf(STICKER_FILE_NAME_IN_QUERY, STICKER_FILE_EMOJI_IN_QUERY))
        pack.stickers.forEach { s ->
            cursor.addRow(arrayOf(s.fileName, s.emojis.joinToString(",")))
        }
        cursor.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
