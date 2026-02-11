package com.ytdownloader.data.consent

import com.ytdownloader.domain.repo.ConsentRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

class FileConsentRepository(
    private val baseDirectory: String,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
) : ConsentRepository {
    private val fileSystem = FileSystem.SYSTEM
    private val consentPath = baseDirectory.toPath().resolve("consent.json")

    override suspend fun isConsentGranted(): Boolean {
        if (!fileSystem.exists(consentPath)) return false
        return runCatching {
            fileSystem.read(consentPath) { json.decodeFromString(ConsentState.serializer(), readUtf8()) }
        }.getOrNull()?.accepted ?: false
    }

    override suspend fun grantConsent() {
        fileSystem.createDirectories(consentPath.parent!!)
        fileSystem.write(consentPath) {
            writeUtf8(json.encodeToString(ConsentState.serializer(), ConsentState(true)))
        }
    }
}

@Serializable
private data class ConsentState(val accepted: Boolean)
