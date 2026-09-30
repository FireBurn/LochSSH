# Work left

This list reflects the current app and the project conversations. The older
`HANDOVER.md` predates the terminal, editor, and icon fixes.

## Fix before wider use

- Check long-lived SSH service behavior on a device. Android 14 and later now
  use `specialUse` with an explicit SSH purpose instead of the `dataSync` type
  subject to a [six-hour background limit](https://developer.android.com/develop/background-work/services/fgs/timeout).
  Older Android keeps its existing service start. Timeout callbacks stop the
  service and retain a retry reason; normal shutdown removes opening rows too.
  Four unit tests cover type selection and session cleanup. The built APK has
  the declared type, permission and SSH purpose. A phone test for the installed
  declaration is compiled but not yet run. Actual service start, timeout
  delivery and long screen-off runs still need device checks.
  The special-use declaration needs review before Google Play publication.
- Check host-key review and removal on a device, including a changed server key.
  The app now lists fingerprints and removes a selected key after confirmation.
  First-seen keys now require fingerprint approval before being saved, including
  keys from jump hosts. Seven storage tests cover approval, persistence, stale
  reviews and changed-key rejection. A real SSH test stops the first handshake
  without saving a key, then connects after exact-key approval.
  Check trust, cancel, retry and save errors
  in the phone UI; existing saved keys are kept.
- Verify host duplication and deletion failures on a device. The host list now
  shows an error dialog, and deletion requires confirmation. Host-editor save
  errors now return to the UI without escaping the coroutine; test a failed save.
  Keep-alive validation now uses the same safe range in the editor and save
  code. Zero disables it; oversized values disable Save and show the limit.
  Boundary and invalid-input unit tests cover the shared parser; check the field
  and its error message on a device.
- Check Clear all data on a device. It now clears the open Room database and
  encrypted preferences instead of deleting an unrelated file path.
- Add device tests for identity changes and failures. The editor now saves new
  secrets before updating the row and removes old secrets afterward, but this
  needs tests for key-source switches, failed writes, and cleanup failures.
  Save errors now return to the editor on the UI thread; check the messages on
  a device. Required usernames and credentials are checked before saving, but
  that validation also needs a device check.
- Check terminal behavior on a device with long output, full-screen programs,
  command history, resizing, reconnecting, and multiple open sessions. The
  Claude conversation reported stale text in command history, odd full-screen
  output, and session title errors. Unit tests now cover command recall and
  alternate-screen cursor restoration, but there is no device regression run
  for the current build. Also verify disconnect during a slow handshake: the
  service now cancels that attempt before it can appear as an open session.
  Stream read errors now trigger session cleanup; verify loss of connectivity
  on a device without leaving a dead session listed as open.
  The host list distinguishes connecting, key approval and failed sessions.
  Closing a connecting row cancels its service attempt; check this on a device.
  Terminal screens now stay with their connection instead of replaying a capped
  raw-output tail when reopened. Tests cover full-screen state after more than
  1 MB of output, split character/escape sequences and bounded scrollback.
  Check returning to a running full-screen program on a device.
- Compare the launcher and splash icons with the amber Nessie Gemini artwork at
  actual device sizes. The vectors trace that artwork and the launcher mark was
  enlarged after a 48-pixel desktop render. The adaptive mask and splash have
  not been checked on a device.
- Resolve Android Ed25519 without relying on local JSch JARs. PR
  [mwiede/jsch#1160](https://github.com/mwiede/jsch/pull/1160) now adds a separate
  Android artifact and passes Android emulator CI, Maven CI and the SonarCloud
  quality gate.
  Stock emulator images still lack imported-key Ed25519 signing, so that test
  needs a provider installed for coverage. The release workflow still publishes
  only the main JAR; agree on Android artifact publishing with the maintainer.
  Wait for a published release before replacing the app's local JARs.

## Complete the original app spec

- Check the terminal session drawer with live connections on a device.
  It lists titles and connection states and switches without starting another
  connection. Switching replaces the terminal page so Back returns to the host
  list; returning to a session uses its retained screen and scrollback.
  Two navigation tests pass on the phone, including older stacked terminal pages.
  Three drawer UI tests are compiled but need an unlocked screen to run.
- Test SOCKS5 forwarding through a live SSH server on a device. A JVM-side
  loopback test passed through a real SSH session, but no Android tunnel run has
  been recorded. Bind and proxy connection failures now appear in the terminal;
  verify those warnings on a device.
- Verify command-on-connect and tmux/Screen session selection on a device,
  including the 2→3 and 3→4 database migrations for existing hosts.
  Eight unit tests cover session listings and quoted attach commands, including
  Screen names with spaces and Unicode, multi-user listings, and tmux sessions
  with several clients. Check these cases in the phone's session picker.
- Check terminal keyboard customization on a device. Settings now supports
  changing and reordering keys, text keys, adding and removing rows, and reset.
  Layout and key-encoding unit tests pass; an isolated phone preferences test
  saves, reloads and resets the layout. Verify the editor and modifier behavior
  in a live terminal.
- Check terminal scrollback, long-press selection, copy, and paste gestures on a
  device. The current build keeps bounded normal-screen history and confirms
  multi-line paste; the interaction has not been tested on a phone.
- Verify imported RSA, Ed25519, ECDSA, and PKCS#8 keys and passphrases on a
  device. JVM tests now load and sign with those formats, including encrypted
  pasted and file-path keys; missing or wrong passphrases fail before login.
  Pasted keys no longer go through temporary files.
  Three instrumented tests pass on the Android 16 phone: generated-key signing,
  encrypted RSA import and signing, and missing-passphrase rejection. Server
  login with a generated key and the other import formats still need checks.
- Confirm the foreground service and battery settings on supported Android
  versions. The service has a wake lock. Settings now shows the battery exemption
  state and offers an explicit request, with battery and app settings as fallbacks.
  A wake lock does not exempt the app from
  [Doze](https://developer.android.com/training/monitoring-device-state/doze-standby).
  Check the request, refusal, status refresh and screen-off SSH on a device.
  No exemption is requested automatically, and other system limits still apply.
  Five phone tests cover the declared permission, system-state read, request
  intent, fallback pages and launch errors without changing the battery setting.

## Broader SSH-client features

- Check SFTP browsing, upload, download, and overwrite prompts against a live
  server on a device. The current build uses an extra channel on an open session.
  A local real-server test passes for listing, upload, download, overwrite
  refusal, explicit replacement and directory links. Device checks remain.
- Check saved-command insert, run, edit, delete, and the 4→5 database migration
  on a device. Saves now wait for the database result and retain input on
  failure; delete failures show an error. Four storage tests pass; check those
  error paths in the phone UI. Check connection history and the 5→6 migration on a device,
  including failed, cancelled, and interrupted connections.
- Check jump hosts and SSH-agent forwarding on a device.
  The [jump-host plan](jump-hosts.md) covers separate identities, server-key
  checks and connection cleanup. The transport now carries SSH through
  direct-tcpip channels; a local nested SSH login and command test passes with
  strict server-key checks. The editor now selects a saved jump host; five
  chain tests reject missing hosts, loops and excessive chain length. An
  isolated phone test verifies the 6→7 migration and saved jump choices.
  Timed channel reads now carry JSch's handshake and keep-alive timeouts;
  a silent destination fails with a timeout in the local SSH integration test.
  Cancel signals the handshake before waiting for the connection lock; tests
  cover cancellation before startup, during a silent channel read and at
  completion. Connecting sessions are closed before waiting for the manager
  lock; a silent-server test confirms direct handshakes stop before their read
  timeout. Cancellation during DNS/TCP setup and phone cleanup still need checks.
  Check the selector, live connections, key review, cancellation, and
  two-server tests with separate identities. Agent forwarding is off by default,
  requires a private-key identity and warns before enabling. Only the current
  connection's key is exposed; remote key-list changes are blocked. A local
  SSH test authenticates using only the forwarded key. Phone tests verify
  upgrades from versions 6 and 7 to 8, preserving jump choices and keeping
  forwarding disabled. Check the warning, remote login and disconnect on a phone.
- Check Loch, Black and Paper terminal themes on a device, including existing
  scrollback, inverse text, selection and remote programs with explicit colours.
  Settings changes apply to open terminals; default-colour and inverse-text
  unit tests pass. An isolated phone preferences test verifies theme persistence
  and reset. Add font choices.
- Check Save terminal text on a device: file selection, cancellation, UTF-8,
  failed writes and rotation while choosing a file. It exports retained
  scrollback and the screen after a warning about secrets. Two unit tests cover
  scrollback and alternate-screen exports. Full opt-in session recording,
  including output discarded from scrollback, still needs implementation.
- Check opening links from selected terminal text on a device. The menu lists
  valid HTTP/HTTPS links for confirmation, rejects credentials and other
  schemes, and reports browser launch failures. Three parsing tests pass;
  wrapped links and the selection-to-browser interaction still need checks.
- Add local shell sessions, homescreen shortcuts, and an app lock.
- Add IPv6 connection tests, proxy settings, and external-keyboard shortcuts.
  Ctrl punctuation and Alt+Ctrl character encoding now share the on-screen
  keyboard's encoder. Hardware F1–F12, Insert, Shift+Tab and modified navigation
  keys are encoded too; three Android tests pass on the phone. Check physical
  keyboard input and application shortcuts in a live terminal.
- Add port knocking and a lightweight remote performance view.
- Assess Mosh separately: it uses UDP and cannot share SSH port-forwarding
  behavior. Implement it as a separate session type.
- Add Telnet, serial connections, split terminals, and opt-in encrypted
  backup/sync. These need separate protocol and data-handling designs.

ConnectBot's [SSH library](https://github.com/connectbot/cbssh) lists local,
remote, and dynamic forwarding. [Termius for Android](https://www.termius.com/free-ssh-client-for-android)
lists SSH, SFTP, Mosh, Telnet, and serial connections. [Sonelli's JuiceSSH repositories](https://github.com/sonelli)
include Mosh, port-knocking, performance-monitor, and plugin code. The
[JuiceSSH plugin library](https://github.com/Sonelli/juicessh-pluginlibrary)
includes an SFTP client API. The original LochSSH spec also calls for a
customizable keyboard.
