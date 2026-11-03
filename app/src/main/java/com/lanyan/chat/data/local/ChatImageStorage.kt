package com.lanyan.chat.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.random.Random

/**
 * 聊天图片存储：尽量保留原图字节，不做裁剪。
 * 只有 HEIC 等兼容性差的格式才转成高质量 JPEG。
 */
class ChatImageStorage(private val context: Context) {
    suspend fun importImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase(Locale.ROOT)
        val size = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        require(size <= MAX_BYTES) { "图片不能超过 8 MB" }

        val directory = File(context.filesDir, "chat_images").apply { mkdirs() }
        val baseName = "img_${System.currentTimeMillis()}_${Random.nextInt(1000)}"
        val directExtension = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> null
        }

        if (directExtension != null) {
            val destination = File(directory, "$baseName.$directExtension")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            } ?: error("无法读取所选图片")
            destination.absolutePath
        } else {
            // HEIC / HEIF / 未知格式：解码后转成高质量 JPEG，保证各模型服务都能识别
            val bitmap = loadSampledBitmap(context, uri, FALLBACK_MAX_SIZE)
                ?: error("无法读取所选图片，请重新选择")
            val destination = File(directory, "$baseName.jpg")
            try {
                FileOutputStream(destination).use { output ->
                    require(bitmap.compress(Bitmap.CompressFormat.JPEG, FALLBACK_QUALITY, output)) {
                        "图片保存失败"
                    }
                }
            } finally {
                bitmap.recycle()
            }
            destination.absolutePath
        }
    }

    fun remove(path: String?) {
        if (path.isNullOrBlank()) return
        val directory = File(context.filesDir, "chat_images")
        val candidate = File(path)
        if (candidate.parentFile?.canonicalPath == directory.canonicalPath) {
            candidate.delete()
        }
    }

    private companion object {
        const val MAX_BYTES = 8L * 1024L * 1024L
        const val FALLBACK_MAX_SIZE = 2048
        const val FALLBACK_QUALITY = 92
    }
}
