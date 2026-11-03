package com.lanyan.chat

import android.app.Application
import androidx.room.Room
import com.lanyan.chat.data.local.AvatarStorage
import com.lanyan.chat.data.local.ChatImageStorage
import com.lanyan.chat.data.local.LanYanDatabase
import com.lanyan.chat.data.local.SettingsStore
import com.lanyan.chat.data.remote.OpenAiClient
import com.lanyan.chat.data.repository.ChatRepository
import com.lanyan.chat.data.repository.MemoryRepository
import com.lanyan.chat.domain.MemoryConsolidator
import com.lanyan.chat.domain.MemoryEngine
import com.lanyan.chat.domain.SuggestionEngine

class LanYanApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: android.content.Context) {
    private val database = Room.databaseBuilder(
        context,
        LanYanDatabase::class.java,
        "lanyan.db",
    ).addMigrations(
        LanYanDatabase.MIGRATION_1_2,
        LanYanDatabase.MIGRATION_2_3,
        LanYanDatabase.MIGRATION_3_4,
        LanYanDatabase.MIGRATION_4_5,
    ).build()

    val settingsStore = SettingsStore(context)
    val avatarStorage = AvatarStorage(context)
    val chatImageStorage = ChatImageStorage(context)
    val chatRepository = ChatRepository(database.conversationDao(), database.messageDao())
    val memoryRepository = MemoryRepository(database.memoryDao())
    val modelGateway = OpenAiClient()
    val memoryEngine = MemoryEngine(memoryRepository)
    val memoryConsolidator = MemoryConsolidator(memoryRepository, chatRepository, modelGateway)
    val suggestionEngine = SuggestionEngine(modelGateway)
}
