package com.lanyan.chat.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 聊天页自定义背景，保存为压缩后的 JPEG 到应用私有目录。 */
class BackgroundStorage(private val context: Context) {
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = loadSampledBitmap(context, uri, MAX_SIZE)
            ?: error("无法读取所选图片，请重新选择")
        val directory = File(context.filesDir, "background").apply { mkdirs() }
        val destination = File(directory, "bg_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(destination).use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)) { "背景保存失败" }
            }
        } finally {
            bitmap.recycle()
        }
        destination.absolutePath
    }

    fun remove(path: String?) {
        if (path.isNullOrBlank()) return
        val directory = File(context.filesDir, "background")
        val candidate = File(path)
        if (candidate.parentFile?.canonicalPath == directory.canonicalPath) {
            candidate.delete()
        }
    }

    private companion object {
        const val MAX_SIZE = 2048
        const val QUALITY = 88
    }
}
