package com.example.mnlists.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** JSON encoding of a complete backup. Reflection-free and R8-safe. */
object BackupCodec {

    const val FORMAT = "mnlists-backup"
    const val VERSION = 1

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        coerceInputValues = false
        allowSpecialFloatingPointValues = false
    }

    fun encode(payload: BackupPayload): String = json.encodeToString(payload)

    /** Only called after [BackupValidator] has accepted the raw text. */
    @Throws(SerializationException::class)
    fun decode(raw: String): BackupPayload = json.decodeFromString(raw)
}
