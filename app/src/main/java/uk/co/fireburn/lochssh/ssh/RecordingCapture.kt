package uk.co.fireburn.lochssh.ssh

internal class RecordingCapture {
    private var writer: RecordingWriter? = null

    @Synchronized fun start(next: RecordingWriter) {
        check(writer?.state?.value?.closed != false) { "Stop the current recording first" }
        check(next.state.value.accepting) { "Recording is already stopped" }
        writer = next
    }

    @Synchronized fun append(bytes: ByteArray, length: Int) {
        writer?.append(bytes, length)
    }

    @Synchronized fun stop() { writer?.stop() }
}
