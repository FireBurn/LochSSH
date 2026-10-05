# Session recording

Not available in the app yet. The bounded writer, file store, host/time metadata
and per-connection capture hook have sixteen passing unit tests. The SSH reader
calls the hook before parsing output and stops it on reader completion or
disconnect, but nothing starts recording from the UI. Private-directory selection,
file finalization, consent and controls remain. Save terminal text
exports the retained screen and scrollback; it does not preserve output discarded
from the buffer.

## Controls

Add Start recording and Stop recording to the terminal menu. Starting requires
a warning that output may contain passwords, tokens and private text. Recording
is off by default, applies only to the selected connection, and never restarts
automatically after reconnecting. Show a recording indicator and any write error
in the terminal. A recording failure must not close the SSH connection.

Add a recordings list with size, start time, host label, export and delete.
Use private app files, not the cache or shared storage. Existing Android backup
is disabled; recording files must remain excluded if backup support is added.
Clear all data must stop active recordings before deleting their files.

## Data

Capture incoming bytes at the SSH reader before terminal parsing. Record only
output, not typed input or outgoing terminal replies. Keep the received bytes
unchanged, including escape sequences and partial UTF-8 characters. Label exports
as raw terminal output, not plain text or a replay format. Do not add credentials,
key material or connection configuration to file headers or filenames.

Copy each reader chunk before handing it to the writer: the SSH reader reuses its
buffer. Use one ordered writer per recording and a bounded queue. If the queue
fills, stop recording with an error rather than dropping chunks silently or
blocking the terminal reader. Bound each file and stop with an explicit size-limit
message; never truncate an old recording to make space.

Stopping drains accepted chunks and closes the file. Disconnect and service
shutdown also close it. Interrupted recordings remain available after restart,
marked incomplete; do not claim that process death flushed pending chunks.
Export copies a closed file through the document picker without deleting the
original. Cancellation leaves the original intact.

## Checks

- Preserve ordered output beyond scrollback capacity, including split UTF-8 and
  escape sequences, without capturing outgoing input.
- Reuse reader buffers without corrupting queued chunks. Test concurrent stop,
  failed writes, a full queue, size limits and disconnect.
- Keep recording and indicators tied to the right connection during switching.
  Reconnecting must require a fresh recording choice.
- Test export cancellation, failed copies, deletion and Clear all data.
- Kill the app process during recording and confirm incomplete files remain
  readable, without restarting recording or exposing files outside the app.

Implement the writer and file storage first, then SSH capture and lifecycle
handling, then controls and export. Verify each phase before committing it.
