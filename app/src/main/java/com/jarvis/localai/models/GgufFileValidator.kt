package com.jarvis.localai.models

import java.io.File

object GgufFileValidator {
    private val magic = byteArrayOf(0x47, 0x47, 0x55, 0x46)

    fun hasHeader(file: File): Boolean = file.isFile && runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(magic.size)
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) break
                offset += count
            }
            offset == header.size && header.contentEquals(magic)
        }
    }.getOrDefault(false)

    fun matchProjector(model: File, projectors: List<File>): File? {
        val modelStem = model.name.substringBeforeLast('.').substringBefore("-Q")
        val matched = projectors.filter { it.name.contains(modelStem, ignoreCase = true) }
        return matched.singleOrNull() ?: projectors.singleOrNull()
    }
}