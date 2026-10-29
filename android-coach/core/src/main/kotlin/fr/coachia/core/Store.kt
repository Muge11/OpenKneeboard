package fr.coachia.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Encrypts data at rest; the Android app plugs in an Android Keystore implementation. */
interface Codec {
    fun encode(plain: ByteArray): ByteArray
    fun decode(stored: ByteArray): ByteArray

    object Identity : Codec {
        override fun encode(plain: ByteArray) = plain
        override fun decode(stored: ByteArray) = stored
    }
}

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
}

fun newId(): String = UUID.randomUUID().toString()

/** Single JSON file holding everything the app knows. Small enough to rewrite on every change. */
class AppRepository(private val file: File, private val codec: Codec = Codec.Identity) {
    private val lock = Any()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppData> = _state.asStateFlow()
    val data: AppData get() = _state.value

    private fun load(): AppData {
        if (!file.exists()) return AppData()
        return runCatching {
            AppJson.decodeFromString(AppData.serializer(), codec.decode(file.readBytes()).decodeToString())
        }.getOrElse { AppData() }
    }

    fun update(transform: (AppData) -> AppData): AppData = synchronized(lock) {
        val next = transform(_state.value)
        if (next != _state.value) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(codec.encode(AppJson.encodeToString(AppData.serializer(), next).encodeToByteArray()))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            _state.value = next
        }
        next
    }
}
