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
  The Android 13 emulator suite passes 45 tests; the Android 14+ declaration
  test is skipped there. This does not verify the newer service type.
  A separate legacy declaration test passes on Android 13, checking the
  selected type and base foreground-service permission.
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
  Host-editor load errors and missing rows now keep Save disabled and offer
  Retry. Host and tunnel rows load together in a transaction; partial results
  are not kept. Switching hosts resets form fields and cancels the old lookup.
  Check failed host and tunnel reads, retry, switching hosts during a slow
  lookup and attempting Save after the host was removed on a device.
  Saves now check that an existing host was updated before replacing tunnels.
  Six lookup unit tests pass. The Android 13 emulator test removes a loaded
  host and confirms Save reports an error without recreating its tunnels.
- Check Clear all data on a device. It now clears the open Room database and
  encrypted preferences instead of deleting an unrelated file path.
- Add device tests for identity changes and failures. The editor now saves new
  secrets before updating the row and removes old secrets afterward, but this
  needs tests for key-source switches, failed writes, and cleanup failures.
  Save errors now return to the editor on the UI thread; check the messages on
  a device. Required usernames and credentials are checked before saving, but
  that validation also needs a device check.
  Identity deletion now asks for confirmation. Its database delete refuses
  rows used by saved hosts, and failed deletes keep stored secrets. Cleanup
  failures are reported separately after a successful row deletion. Check
  confirmation, host usage and error messages on a device.
  Host saves check selected identities inside the database transaction, even
  without agent forwarding. Check deleting an unused identity while its host
  editor is open: Save must report the missing identity without changing rows.
  Four unit tests pass for missing and existing identities, no identity and
  the private-key requirement for agent forwarding.
  Five unit tests pass for deletion order, host usage, database errors,
  cleanup errors and cancellation. The guarded delete also passes a local
  SQLite check with two host references. The Room test passes on the Android
  13 emulator: both host references block deletion, removing one still blocks
  it, and removing the last reference allows deletion without removing hosts.
  Editor load errors and missing rows now keep Save disabled and offer Retry.
  Starting another lookup cancels the previous one. Check failed loads, retry,
  missing rows and switching identities before a lookup finishes on a device.
  Editor fields, including pasted keys and passwords, reset when the identity
  changes. Check that switching identities cannot carry credentials across.
  Six lookup unit tests pass for new and existing identities, missing rows,
  invalid IDs, storage errors and cancellation. Android editor-load tests
  pass on the Android 13 emulator for the save guard, retry and a cancelled
  lookup returning late. Form interactions and credential reset checks remain.
  Identity saves now check that an existing row was updated. A row deleted
  after loading produces an error; newly written secrets follow the failed-save
  cleanup path. Two Android 13 emulator tests pass for removed and existing
  identity saves. They use no-auth rows and do not verify encrypted secret cleanup.
  Three isolated identity-secret tests pass on the Android 13 emulator for
  database failure, a secret write that reports failure after applying, and
  switching from a pasted key to a path.
  They use dummy values in separate plain preferences, not the app's encrypted
  store. UI warnings and rollback with encrypted preferences still need checks.
  Successful identity saves now report old-secret cleanup failures and retain
  the saved row in the editor before offering retry. The Android 13 emulator
  cleanup-failure test passes; check the warning in the UI. Failed-save cleanup
  failures now include a separate warning. The new partial-write test passes.
  Orphaned-secret removal needs a durable retry path. Cancellation during old
  secret cleanup keeps newly saved credentials; the Android test passes.
  Eight selected unit tests pass for removal failures, cancellation and
  identity deletion. Two encrypted-storage tests pass on the Android 13
  emulator for save/reload, hidden plain references and values, deletion,
  clearing and saving after clear. They use separate test
  preferences and a separate Android Keystore alias, removed after each test.
  They do not verify rollback against the encrypted backend or recovery after
  a lost keystore key.
  Database version 9 adds a durable secret-cleanup queue without secret values.
  Migration and queue tests pass on the Android 13 emulator for preserved
  rows, duplicate references, reopening and references used by either
  credential field. Recording cleanup
  entries with saves/deletes and startup retry
  still need wiring before the queue can remove leftover secrets safely.
  Identity saves, identity deletion and Clear all data now share a secret-change
  lock. Both suspension and cancelled-waiter unit tests pass. The Android 13
  regression suite passes with 45 tests and one API 34+ test skipped. Check
  concurrent save, deletion and Clear all data in the UI; the suite does not
  cover those operations running together. Cleanup retry must use the same
  lock before it is enabled.
  The retry helper has five passing unit tests: live references are kept,
  failed usage checks do not delete secrets, deletion failures retain entries,
  failed queue removal can retry and cancellation stops processing. It is not
  connected to the database or startup yet. Check it with Room and encrypted
  storage, including reopening after interrupted cleanup, before enabling it.
- Check terminal behavior on a device with long output, full-screen programs,
  command history, resizing, reconnecting, and multiple open sessions. The
  Claude conversation reported stale text in command history, odd full-screen
  output, and session title errors. Unit tests now cover command recall and
  alternate-screen cursor restoration. Drawer and navigation tests pass on an
  Android 33 emulator; live terminal regression checks remain. Also verify
  disconnect during a slow handshake: the
  service now cancels that attempt before it can appear as an open session.
  Stream read errors now trigger session cleanup; verify loss of connectivity
  on a device without leaving a dead session listed as open.
  The host list distinguishes connecting, key approval and failed sessions.
  Closing a connecting row cancels its service attempt; check this on a device.
  Terminal screens now stay with their connection instead of replaying a capped
  raw-output tail when reopened. Tests cover full-screen state after more than
  1 MB of output, split character/escape sequences and bounded scrollback.
  Check returning to a running full-screen program on a device.
  Check the reported crashes, switching from one live terminal to another,
  and disconnect while a drawer or menu is open. Session selection now
  navigates immediately; disconnect leaves the terminal only once. Resize
  tests cover selections made before shrinking the grid and repeated size
  changes. The terminal bar hides after eight seconds and opens through
  Show controls; session switching and disconnect are in its actions menu.
  Parser bounds now cover large cursor and scroll counts, invalid colour
  components, and unterminated oversized escape parameters. Check malformed
  output alongside tmux, Screen and full-screen programs on a device.
  Terminal status, cursor position and text-area size queries now receive
  replies through the connection's writer. Check query-driven applications
  while resizing and switching terminals. Four parser tests cover reply
  contents, split reads, resizing and unsupported queries. A real-server test
  confirms the remote program receives the cursor report.
  Arrow, Home and End keys now follow the remote application's cursor mode
  on both keyboards. Modified keys keep their modifier sequences. Check tmux,
  Screen and editor navigation after entering and leaving full-screen mode.
  Three unit tests cover mode changes, modified keys and session state through
  resizing. All four physical-key encoder tests pass on the Android emulator,
  including application cursor mode and modified keys.
  Wrapping now follows the remote program's setting. A line feed or cursor
  movement cancels a pending wrap instead of skipping another row. Tests cover
  full lines, disabled wrapping, mode changes, backspace and cursor movement.
  Check right-edge output in editors and after terminal resizing on a device.
  Origin mode now makes cursor addresses and reports relative to the scrolling
  region. Cursor movement stays within its margins, invalid margins are ignored,
  and saved cursor state retains origin, wrapping and text colours. Six unit
  tests cover positioning, reports, margins, saved state and resizing. Check these
  modes with editors and tmux while resizing and switching screens on a device.
  Soft reset restores cursor modes, margins, text attributes and saved cursor
  state without clearing text. Full reset clears both screens and scrollback,
  disables bracketed paste and returns the cursor home. Escape, Cancel and
  Substitute now interrupt unfinished commands. Seven unit tests cover resets,
  retained text, screen changes and interrupted input. Check remote reset and recovery
  after interrupted full-screen programs on a device.
  DEC line-drawing and UK character sets now honour selection, shift controls,
  saved cursor state and screen changes. Reset restores ASCII. Seven unit tests
  cover mappings, shifts, split input, saved state and resizing. Check borders
  and symbols in tmux, Screen and text-based menus at different font sizes.
- Compare the launcher and splash icons with the amber Nessie Gemini artwork at
  actual device sizes. The vectors trace that artwork and the launcher mark was
  enlarged after a 48-pixel desktop render. The adaptive mask and splash have
  not been checked on a phone. The Android 13 emulator's circular launcher
  mask is checked: the amber creature and green prompt remain legible and the
  head is not clipped. Other masks, themed icons and the splash remain unchecked.
- Complete terminal character and mode support: spacing marks
  and joined emoji sequences.
  Hardware keypad keys now follow the remote application's keypad mode. Num Lock
  selects numeric input; unlocked keys use navigation or application sequences.
  Normal Enter and on-screen keys remain unchanged. Check a physical keypad with
  editors, Num Lock and modifiers on a device.
  Four unit tests cover sequences, Num Lock, navigation and remote mode changes.
  All five Android key-encoder tests pass on the emulator, including keypad mode
  and Num Lock. Physical-keypad interaction still needs a device check.
  [Unicode width tables](terminal-widths.md) are pinned to version 17.0.0 and
  used for mark classification on all Android versions. Exhaustive tests compare
  their results with the source ranges. Two-column characters now reserve a
  continuation cell, copy once and keep combining marks with the leading cell.
  Edits and resizing clear orphan halves. Nine tests cover wide characters,
  partial edits and 1,000 random editing and resizing actions.
  Check insertion, deletion, wrapping,
  copying, resizing, cursor painting and font fallback on a device.
  The UTF-8 decoder now rejects overlong, surrogate and out-of-range sequences
  and preserves valid characters after broken input. Five tests cover every
  valid Unicode code point and malformed input. Cells now store complete text,
  and drawing, selection and export retain supplementary characters. Check glyphs
  and font fallback on a device. Nonspacing and enclosing marks now stay with
  their base cell without advancing or triggering a wrap. A cell is bounded to
  128 UTF-16 units to prevent untrusted output growing it without limit. Seven
  tests cover accents, edge wrapping, split input, screen changes and bounded cells.
  Resizing now moves the remembered base cell with its row on both screens;
  two regression tests cover accents arriving after a height change.
  Check accent shaping, selection and right-edge output on a device. Variation-selector widths,
  spacing marks and joined emoji still need implementation. Check editor and tmux output against a desktop
  terminal, including screen changes and resizing.
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
  Three drawer UI tests and both navigation tests pass on an Android 33
  emulator, including repeated switches between sessions. Live connections,
  keyboard focus and the hiding toolbar still need interaction checks.
  Investigate an intermittent drawer-test startup failure: No compose
  hierarchies found before the first click. The navigation test passes in
  isolation and a full-suite rerun; the cause is not established.
  Explicit resume commands and shell auto-commands are now queued before
  the UI receives the connection, so early typing cannot precede them.
  Automatic tmux/Screen startup now waits for discovery and queues its command
  before enabling input. Closing the session during discovery prevents it
  reappearing as connected. Check early typing, missing tools, discovery failures
  and disconnect during startup on a device.
  Automatic startup checks only the selected tool instead of checking both
  tools and listing sessions. The session picker keeps full discovery.
  Four unit tests cover the selected check, missing tools, picker discovery
  and allowed commands. Check startup time against a remote server on a device.
- Test SOCKS5 forwarding through a live SSH server on a device. A JVM-side
  loopback test passed through a real SSH session, but no Android tunnel run has
  been recorded. Bind and proxy connection failures now appear in the terminal;
  verify those warnings on a device.
  Sessions for the same saved host and connection settings now share one SSH
  connection, with a separate shell for each terminal. Check local, remote
  and SOCKS5 forwards with several shells, closing the first shell, cancelling
  a new shell, and closing the last shell. A changed host or identity starts a
  separate connection. Check that losing the shared connection closes every
  affected terminal.
  Five real-server tests cover retaining local and SOCKS5 listeners until the
  last release, closing one channel without losing the connection, and refusing
  to share a disconnected connection. A failed SOCKS5 request still reaches
  the remaining terminal after the first closes. Opening channels are tracked before
  connect so cancellation can close them without closing another shell's SSH
  connection. Check cancellation during a delayed shell reply on a device.
  Remote-session discovery channels now close with their terminal, without
  closing the shared connection. A real-server test covers closing a pending
  check, rejecting checks after closure and running another command afterward.
  Check disconnect during tmux/Screen discovery on a device.
  Tunnel warnings now reach all terminals sharing the connection, including
  after the first terminal closes. Newly opened terminals receive retained
  forwarding errors. Check SOCKS5 failures and warning dismissal on a device.
- Verify command-on-connect and tmux/Screen session selection on a device,
  including the 2→3 and 3→4 database migrations for existing hosts.
  Eight unit tests cover session listings and quoted attach commands, including
  Screen names with spaces and Unicode, multi-user listings, and tmux sessions
  with several clients. Check these cases in the phone's session picker.
  Selecting or creating a remote session now opens a new shell before running
  the attach command. Check this while the original terminal is running an
  interactive program, and check retry after a failed new shell.
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
  Three instrumented tests pass on the Android 16 phone and Android 13 emulator: generated-key signing,
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
  These five tests also pass on the Android 13 emulator.

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
  Both the 6→7 and 7→8 migration tests pass on the Android 13 emulator,
  preserving saved hosts, identities, forwards and jump choices.
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
  and reset. Settings now offers Noto Sans Mono and JetBrains Mono. The choice
  is saved and applies to open terminals, which remeasure and resize their grid.
  JetBrains Mono v2.304 uses the no-ligature family, with regular and bold files
  and its licence bundled. Check font selection, persistence, reset, fallback
  glyphs, bold text and remote resizing on a device.
  Two unit tests cover saved choices and defaults. Android persistence,
  reset and loaded-font width tests pass on the Android 13 emulator.
- Check Save terminal text on a device: file selection, cancellation, UTF-8,
  failed writes and rotation while choosing a file. It exports retained
  scrollback and the screen after a warning about secrets. Two unit tests cover
  scrollback and alternate-screen exports. Full opt-in session recording,
  including output discarded from scrollback, still needs implementation.
- Check opening links from selected terminal text on a device. The menu lists
  valid HTTP/HTTPS links for confirmation, rejects credentials and other
  schemes, and reports browser launch failures. Three parsing tests pass;
  wrapped links and the selection-to-browser interaction still need checks.
- Add local shell sessions and an app lock.
  Saved-host menus now request a pinned homescreen shortcut. Launching it loads
  the current host and asks before connecting; no credentials go into the
  shortcut. Missing hosts and storage errors do not start a connection.
  Check launcher support, cancellation, deleted and renamed hosts, rotation
  and launching while another session is open on a device.
  Two Android intent tests pass on the Android 13 emulator, covering the
  explicit target, host-only contents, unrelated actions and invalid IDs.
  Runtime confirmation and launcher tests remain.
  Host deletion disables its pinned shortcut after the database succeeds.
  Clear all data disables saved-host shortcuts before removing rows. Cleanup
  errors are reported; check both paths and launcher failures on a device.
  Two unit tests cover selecting one host's shortcut and all saved-host
  shortcuts without selecting unrelated IDs.
  Shortcut requests run off the UI thread. Check launcher delays and leaving
  the host list while a request is running. Re-adding an existing disabled
  shortcut needs recovery: Android rejects pin requests for disabled IDs.
  Check renamed-host labels when requesting an existing shortcut again.
- Add proxy settings and check IPv6 connections on Android.
  Three real-server IPv6 tests cover SSH authentication, local forwarding and
  SOCKS5 IPv6 requests. They require an explicit IPv6 address for the same
  server already trusted under the test host name. Trust aliases stay in memory
  and retain known-host markers; the user's known-host file is not changed.
  Check saved IPv6 host entry, Android networking and server-key review on a device.
  Session-switching keyboard shortcuts are optional and off by default.
  When enabled, Ctrl+Alt+Page Up/Down cycles through session rows and wraps at
  the ends. Repeated key-down events do not cause repeated switches. Other
  chords and disabled shortcuts keep the existing remote encoding.
  Check the toggle, persistence, repeat handling and switching with a physical
  keyboard on a device.
  Three unit tests cover cycling, wraparound and missing sessions. Android
  key-event, remote-encoding and preference tests pass on the Android 13 emulator.
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
