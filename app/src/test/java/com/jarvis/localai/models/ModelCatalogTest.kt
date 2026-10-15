package com.jarvis.localai.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelCatalogTest {
    @Test
    fun smolVlmEntryUsesOfficialPairAndLicense() {
        val entry = ModelCatalog.entries.single()

        assertEquals("ggml-org/SmolVLM-500M-Instruct-GGUF", entry.repository)
        assertEquals("72e986006ef53e37cdd3f6d4241c90b0f01df376", entry.revision)
        assertEquals("SmolVLM-500M-Instruct-Q8_0.gguf", entry.modelFilename)
        assertEquals("mmproj-SmolVLM-500M-Instruct-Q8_0.gguf", entry.projectorFilename)
        assertEquals("Apache-2.0", entry.license)
        assertEquals("Q8_0", entry.quantization)
        assertEquals(436_806_912L, entry.modelSizeBytes)
        assertEquals(108_783_360L, entry.projectorSizeBytes)
    }

    @Test
    fun downloadUrlsUseOfficialHttpsRepository() {
        val entry = ModelCatalog.entries.single()

        assertTrue(entry.downloadUrl(entry.modelFilename).startsWith("https://huggingface.co/ggml-org/"))
        assertTrue(entry.downloadUrl(entry.modelFilename).contains(entry.revision))
        assertTrue(entry.downloadUrl(entry.projectorFilename).contains(entry.projectorFilename))
    }

    @Test
    fun ggufValidationChecksMagicHeader() {
        val file = Files.createTempFile("jarvis-model", ".gguf").toFile()
        try {
            file.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x00))
            assertTrue(GgufFileValidator.hasHeader(file))
            file.writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x00))
            assertTrue(!GgufFileValidator.hasHeader(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun ggufValidationMatchesModelProjectorPairByName() {
        val model = File("SmolVLM-500M-Instruct-Q8_0.gguf")
        val matching = File("mmproj-SmolVLM-500M-Instruct-Q8_0.gguf")
        val other = File("mmproj-OtherModel-Q8_0.gguf")
        val duplicate = File("mmproj-SmolVLM-500M-Instruct-Q8_0-copy.gguf")

        assertEquals(matching, GgufFileValidator.matchProjector(model, listOf(other, matching)))
        assertEquals(null, GgufFileValidator.matchProjector(model, listOf(other, matching, duplicate)))
    }
}