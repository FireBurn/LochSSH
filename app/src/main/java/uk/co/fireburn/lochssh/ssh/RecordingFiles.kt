package uk.co.fireburn.lochssh.ssh

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.UUID

internal class RecordingFiles(private val directory: File,
    private val newId: () -> String = { UUID.randomUUID().toString() }) {
    data class Entry(val id: String, val size: Long, val complete: Boolean,
        val hostLabel: String = "Unknown host", val startedAt: Long = 0)
    private val active = mutableMapOf<String, RecordingWriter>()

    @Synchronized fun create(hostLabel: String = "Unknown host",
        startedAt: Long = System.currentTimeMillis()): Pair<String, RecordingWriter> {
        require(hostLabel.length <= 512 && startedAt > 0)
        prepareDirectory()
        val id = newId()
        val file = file(id, false)
        check(file.createNewFile()) { "Could not create recording" }
        val details = File(directory, "$id.details")
        var detailsCreated = false
        val writer = try {
            RecordingMetadata(hostLabel, startedAt).write(details)
            detailsCreated = true
            RecordingWriter(file.outputStream())
        } catch (error: Exception) {
            file.delete()
            if (detailsCreated) details.delete()
            throw error
        }
        active[id] = writer
        return id to writer
    }

    @Synchronized fun finish(id: String) {
        val writer = active[id] ?: throw IOException("Recording is not active")
        check(writer.state.value.closed) { "Recording is still closing" }
        val source = file(id, false)
        checkRegularFile(source)
        if (writer.state.value.error == null) {
            val target = file(id, true)
            check(!target.exists() && source.renameTo(target)) { "Could not finish recording" }
        }
        active.remove(id)
    }

    @Synchronized fun list(): List<Entry> {
        if (!directory.exists()) return emptyList()
        prepareDirectory()
        return directory.listFiles()?.mapNotNull { file ->
            val complete = file.name.endsWith(".terminal")
            val suffix = if (complete) ".terminal" else ".partial"
            if (!file.name.endsWith(suffix)) return@mapNotNull null
            val id = file.name.removeSuffix(suffix)
            if (!validId(id) || !Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) return@mapNotNull null
            val details = RecordingMetadata.read(File(directory, "$id.details"))
            Entry(id, file.length(), complete, details?.hostLabel ?: "Unknown host", details?.startedAt ?: 0)
        }?.sortedWith(compareByDescending<Entry> { it.startedAt }.thenBy { it.id })
            ?: throw IOException("Could not list recordings")
    }

    @Synchronized fun export(entry: Entry, output: OutputStream) {
        requireClosed(entry.id)
        val source = file(entry.id, entry.complete)
        checkRegularFile(source)
        source.inputStream().use { it.copyTo(output) }
    }

    @Synchronized fun delete(entry: Entry) {
        requireClosed(entry.id)
        val source = file(entry.id, entry.complete)
        checkRegularFile(source)
        val details = File(directory, "${entry.id}.details")
        if (Files.exists(details.toPath(), NOFOLLOW_LINKS)) checkRegularFile(details)
        check(source.delete()) { "Could not delete recording" }
        if (details.exists()) check(details.delete()) { "Could not delete recording details" }
    }

    private fun requireClosed(id: String) {
        check(active[id]?.state?.value?.closed != false) { "Stop recording first" }
    }

    private fun prepareDirectory() {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create recording folder")
        if (!Files.isDirectory(directory.toPath(), NOFOLLOW_LINKS)) throw IOException("Invalid recording folder")
    }

    private fun file(id: String, complete: Boolean): File {
        require(validId(id)) { "Invalid recording ID" }
        prepareDirectory()
        return File(directory, id + if (complete) ".terminal" else ".partial")
    }

    private fun validId(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)

    private fun checkRegularFile(file: File) {
        if (!Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) throw IOException("Recording file is unavailable")
    }
}
