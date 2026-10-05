package uk.co.fireburn.lochssh.ssh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class RecordingCoordinator(private val files: RecordingFiles, private val scope: CoroutineScope) {
    class Recording(val id: String, val writer: RecordingWriter) {
        private val result = MutableStateFlow<Result?>(null)
        val completion = result.asStateFlow()
        data class Result(val error: String?)
        fun stop() = writer.stop()
        internal fun finished(error: String?) { result.value = Result(error) }
    }

    private val active = mutableMapOf<RecordingCapture, Recording>()

    @Synchronized fun start(capture: RecordingCapture, hostLabel: String): Recording {
        scope.ensureActive()
        check(capture !in active) { "Recording is already active or closing" }
        val (id, writer) = files.create(hostLabel)
        try { capture.start(writer) }
        catch (error: Exception) {
            writer.stop()
            if (writer.awaitClosed(5_000)) files.delete(RecordingFiles.Entry(id, 0, false))
            throw error
        }
        val recording = Recording(id, writer)
        active[capture] = recording
        val job = scope.launch(Dispatchers.IO) {
            var error: String? = null
            try {
                writer.state.first { it.closed }
                files.finish(id)
                error = writer.state.value.error
            } catch (_: Exception) {
                writer.stop()
                error = "Could not finish recording file"
            } finally {
                synchronized(this@RecordingCoordinator) { active.remove(capture, recording) }
                recording.finished(error)
            }
        }
        job.invokeOnCompletion {
            if (recording.completion.value == null) {
                writer.stop()
                synchronized(this) { active.remove(capture, recording) }
                recording.finished("Recording interrupted")
            }
        }
        return recording
    }
}
