package com.lanyan.chat.data.local

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AvatarStorage(private val context: Context) {
    suspend fun saveAvatarBitmap(bitmap: Bitmap, isUser: Boolean): String =
        saveAvatarBitmap(bitmap, if (isUser) "user" else "assistant")

    suspend fun saveAvatarBitmap(bitmap: Bitmap, prefix: String): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "avatars").apply { mkdirs() }
        val destination = File(directory, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(destination).use { output ->
            require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "头像保存失败" }
        }
        destination.absolutePath
    }

    fun removeStoredAvatar(path: String?) {
        if (path.isNullOrBlank()) return
        val directory = File(context.filesDir, "avatars")
        val candidate = File(path)
        if (candidate.parentFile?.canonicalPath == directory.canonicalPath) {
            candidate.delete()
        }
    }
}
