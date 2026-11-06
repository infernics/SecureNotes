package com.andrey.securenotes

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Модель заметки. Хранится ТОЛЬКО в зашифрованном виде. */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Файловое хранилище заметок: один файл .enc на заметку,
 * содержимое — AES-256-GCM шифрованный JSON.
 * Внутреннее приватное хранилище приложения (filesDir/notes).
 */
class NoteRepository(context: Context, private val crypto: Crypto) {

    private val dir = File(context.filesDir, "notes").apply { mkdirs() }

    fun loadAll(): List<Note> =
        dir.listFiles { f -> f.name.endsWith(".enc") }
            .orEmpty()
            .mapNotNull { f ->
                runCatching {
                    val o = JSONObject(crypto.decryptToText(f.readBytes()))
                    Note(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        body = o.getString("body"),
                        updatedAt = o.getLong("updatedAt")
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.updatedAt }

    fun save(note: Note) {
        val json = JSONObject()
            .put("id", note.id)
            .put("title", note.title)
            .put("body", note.body)
            .put("updatedAt", note.updatedAt)
        File(dir, "${note.id}.enc").writeBytes(crypto.encrypt(json.toString()))
    }

    fun delete(id: String) {
        File(dir, "$id.enc").delete()
    }
}
