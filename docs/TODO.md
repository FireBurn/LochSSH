# Work left

This list reflects the current app and the project conversations. The older
`HANDOVER.md` predates the terminal, editor, and icon fixes.

On 17 October, the JDK 25 build, lint and all 451 local tests pass with real
SSH/SFTP and IPv6 enabled. The Android 13 emulator run passes 134 tests and skips
seven: six live-SSH tests without server arguments and the Android 14+ declaration
test. The Android 14 results below are from 9 October, before the latest changes.
Under load (two emulators at once) one or two timing-based Compose tests fail
at random and pass on rerun; run one emulator at a time.

Live SSH from the app on an emulator: `LiveSshAndroidTest` connects with an
Ed25519 key and an RSA key to a real OpenSSH server, approves the first-seen host
key, runs a command in the shell, lists tmux sessions, reads performance values
and lists a directory over SFTP. Start a throwaway server with its own host key and
`authorized_keys` (see HANDOVER.md), push the private keys to `/data/local/tmp`,
and pass `-e host 10.0.2.2 -e port 2222 -e user NAME -e ed25519Key PATH -e rsaKey PATH`
to `am instrument`. It passes on both emulators. It found that the emulator images
cannot sign with Ed25519 through the platform, so the app now bundles BouncyCastle
as JSch's fallback; before that, an Ed25519-only server and curve25519 key exchange
were unavailable on those images, and likely on real phones before Android 14.
This does not replace a check on the real phone.

## Notifications

Notification fixes now give the summary a tap action that opens the host/session
list. Connection titles include host, remote-session name, live terminal title and
a connection number so otherwise identical connections can be told apart.
Attached tmux names refresh every fifteen seconds, once per host. Discovery uses
the pane title too, so titles captured by tmux do not require title passthrough.
Session rows combine remote-session names with live terminal titles, suppressing
duplicate titles and host-only updates. Reconnecting drops the old task title.
Three registry tests and one pane-title test cover those labels. Check real
Claude, Codex and Antigravity titles on the phone with title updates enabled.
Discovery uses
pane PIDs and a bounded process listing to follow foreground descendants through
sudo/su and their extra terminals. Only command names are read, not arguments.
If ps is unsupported or processes are hidden, the original tmux name remains.
Screen and plain-shell tasks still rely on the terminal title. Check real
notification taps, sudo/su builds and server process restrictions on the phone.

The process fields come from the [ps manual](https://man7.org/linux/man-pages/man1/ps.1.html)
and pane IDs from the [tmux manual](https://man.openbsd.org/tmux).
Five local tests cover nested foreground tasks, hidden/missing descendants,
tmux labels, notification titles and duplicate title suppression. Notifications
use the same combined label as session rows. Two Android tests cover summary and
connection notification navigation; actual notification taps still need checking.

## Conversation sources

Claude's LochSSH conversation includes the keyboard, terminal, session and icon
requests reflected below. A newer Claude conversation from 8–11 October covers
live emulator tests, Telnet, split view, the pipe-based local shell, auto-reattach
and wrapped-link handling. The indexed Antigravity conversation, LochSSH Visual
Asset Generation, contains the branding request and quota errors, without a
completed response. Its launcher, splash and three image-prompt requirements
are represented in `branding.md` and the app assets.

No LochSSH conversation was found in the current Qwen session metadata or the
OpenCode session index. This does not prove older or exported logs are absent;
include them if a project log becomes available. Unrelated project conversations
are outside this audit.

## Fix before wider use

- Test duplicate service start requests for the same session.
  Reconnect buttons filter opening and connected rows, but service intents can
  arrive before the UI reflects that change. `onStartCommand` now returns before
  creating an attempt if that ID is opening or connected. Previously it cancelled
  the opening attempt or replaced the live manager after another handshake.
  Explicit disconnect remains separate; failed attempts are removed so retries
  can proceed. Duplicate requests still check unused-shell replacement when the
  target is connected. Remote attach commands are not changed.
  Test two queued starts for one ID, independent IDs on the same host, and retry
  after a cancelled or failed attempt. UI eligibility tests alone do not cover this.
- Test the actual Android keyboard input connection, not only key-event encoding.
  Claude's project conversation explicitly requested soft-keyboard Backspace
  and latched Ctrl/Alt followed by a typed character, while keeping DEL as
  forward delete. Null and empty commits are now ignored, and both deletion
  methods forward Backspace requests. Three Android tests cover those paths,
  supplementary text and callbacks replaced after opening the input connection.
  Before the fix, the regressions reproduced literal "null" text and missing
  code-point Backspace requests. Check keyboard-specific behaviour, latched
  modifiers, repeated deletion and modifier clearing
  through the input connection. The hidden field does not retain remote text;
  deletion requests currently forward their count rather than translating
  UTF-16 lengths using the remote cursor's surrounding text.
  Both deletion methods now send forward-delete keys for the after-cursor count.
  Negative lengths and counts above 4096 are rejected before sending any input.
  Backspace clears Ctrl/Alt like the key-event path. Three more Android tests
  cover forward deletes, rejecting invalid ranges without partial deletion,
  and rejecting excessive counts without sending input. Before the fix, the
  first two tests reproduced the missing deletes and invalid-range handling.
  Latched Ctrl/Alt/Fn state is now scoped to the session and connection manager.
  Two Android tests reproduced modifiers carrying across session switches and
  reconnects, then pass with fresh state for each input target. Ordinary redraws
  preserve the latch. Live keyboard checks above remain.
- Check password-assisted key installation on a test server and the phone.
  Generate key pair now offers Save and install on server. The private key is
  saved before upload; the one-time password is never written to storage.
  Installation uses SFTP, keeps existing entries and key restrictions, skips
  duplicates, refuses symlinks and checks a fresh public-key login. New server
  keys require fingerprint approval and another password entry. Changed keys
  are rejected. Cancellation closes the connection. Direct connections with
  standard ~/.ssh/authorized_keys, password authentication and SFTP are required.
  Check real password login, existing permissions, failed writes, disabled key
  authentication and servers using a different authorized-keys path. Choose the
  saved identity in host settings after installation; jump hosts and port
  knocking are not supported by the setup dialog.
  Eleven unit tests cover line handling and duplicates, including quoted options,
  escaped quotes and key text in comments. Parsing follows the
  [OpenSSH authorized_keys format](https://man.openbsd.org/sshd.8#AUTHORIZED_KEYS_FILE_FORMAT),
  so a mentioned key is not mistaken for an installed entry. Six local SFTP tests
  check preserved entries/restrictions, permissions and symlink rejection using
  isolated files under /tmp, not the account's real authorized_keys. They also
  reject oversized files, additions over the limit and a directory in
  place of authorized_keys. Rejected size checks preserve the file's bytes.
  Three Android tests cover success, failure, cancellation and password-byte clearing.
- Check held navigation keys, touch scrolling and screen-off recovery on the phone.
  Arrows and Page Up/Down repeat after a 400 ms hold, then every 80 ms, stopping
  on release. Held keys also stop when the activity pauses and do not restart
  on resume without another press. An Android regression reproduces background
  repeats before the fix and checks pause, resume and a fresh press afterward.
  The keyboard also cancels a held key when its session or connection changes,
  so repeating input cannot move into a newly selected terminal. A separate
  Android test checks a held arrow across a target change and a fresh page-key
  press on the new target.
  Changing a held button's action also cancels it rather than sending repeats
  for the replacement. Two more Android tests cover changing Up to Down during
  a hold and cancelling a touch, including stopping repeats and clearing Ctrl.
  Remote mouse modes 1000/1002/1003 enable touch wheel events; mode
  1006 selects the text coordinate format and 1005 selects UTF-8 coordinates.
  Switching encodings clears the previous one; disabling an inactive encoding
  leaves the current one intact. Three unit tests cover extended coordinates,
  limits, switching and reset. Encoding follows the
  [xterm mouse specification](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h2-Mouse-Tracking).
  Remote mode 1007 enables arrow-key scrolling on the alternate screen when
  mouse reporting is off. Mouse reports take priority, application-cursor mode
  selects the arrow encoding, and scrolling retained history sends no input.
  Three unit tests cover enable/disable, screen changes, reset and priority.
  Two Android gesture tests pass for arrow reports, disabling alternate scroll,
  mouse priority and ordinary history scrolling without remote input.
  Check the touch gesture with real pagers and editors on the phone.
  Without mouse input, dragging still
  scrolls local history. Check Claude Code, tmux mouse settings, Screen, selection,
  cancellation and switching sessions while a key is held.
  Two Android tests cover arrow repetition stopping on release and a short page
  key tap. Two unit tests check wheel encoding and requested modes/reset.
  The host editor has Find and connect all remote sessions. Discovery includes
  tmux's active command and window name. Remote names remain visible after host
  title updates and reconnecting. Unused shells close only after a replacement
  connects; shells with sent input stay open. A missing registry entry returns to
  the host list. The terminal menu opens Background connections settings.
  Cleanup also preserves full-screen or mouse-using programs started by login
  files, even before any user input. Five unit tests cover untouched shells,
  sent input/startup commands, automatic sessions and program screen/mouse modes.
  Two Android tests check missing connections returning to hosts and dropped
  connections offering Reconnect and Manage hosts. Four unit tests cover tmux
  descriptions, connect-all discovery and preserving remote names/replacements.
  Phone exit history on 2 October showed repeated system kills and no LochSSH
  battery exemption. Keep-alives already default to 30 seconds. Check recovery
  after an actual process kill and screen-off behaviour with an approved exemption;
  an exemption cannot prevent every vendor or Android system limit.
- Check bulk remote-session connection and recovery on a device. The host list
  groups open and dropped sessions by host ID. Each group shows the current saved
  host name and address; a deleted host keeps its last connection name. Session
  names, status, resume and dismiss actions remain inside the group. Three unit
  tests cover interleaved rows, matching names, renamed/deleted hosts and empty
  lists. Two Android tests cover visible host details, resume and dismissal.
  The host list also has Find and connect all remote sessions in each host's menu,
  plus Find and connect remote sessions on all hosts above the host rows.
  These are one-time actions; saved startup settings remain unchanged.
  Failed discovery rows are reused, repeated taps while discovering do not start
  another connection, and server-key reviews remain for approval. Retries keep
  the discovery choice. Discovery and attach channels can share the same SSH
  transport despite different startup choices, keeping port forwards shared.
  Six unit tests cover discovery planning, retries and reviews; two cover
  sharing settings while retaining credential, proxy and forwarding differences.
  Two Android tests pass for the host-menu action and the global button, including
  disabling it when no hosts are saved. Check discovery on quark, multiple
  hosts, reception loss and live shared tunnels on the phone. The phone has the
  tested APK from commit 80d7b5f, including these host-list updates and keyboard
  fixes. Its installed checksum matches that build; live checks remain.
  The remote session chooser offers Connect all and avoids duplicating open targets.
  It also reuses dropped rows instead of adding a second row, and recognizes
  Screen targets despite changes in their attached/detached state. Five unit
  tests cover row reuse, opening connections, targets on another host,
  preserving server-key review and preferring an opening row over a failed one.
  Discovery also retries disconnected managers before their exit callback records
  an error, and prefers an already-opening row over such a stale connection.
  Two Android tests reproduced both missed retries and selecting the stale row;
  both pass with the shared reconnect rule.
  The main host list and session drawer offer Reconnect all dropped sessions,
  sharing the same button and eligibility rule. A unit test checks that opening
  connections and server-key reviews cannot reconnect. Failed connections
  retain their host and attach command; explicit disconnect removes the entry.
  A missing terminal record now offers Manage hosts rather than an empty grid.
  Explicit remote-session commands use an SSH command request with a terminal,
  not text typed into a login shell which may already have attached tmux.
  Automatic screen/tmux startup now uses the same command-request path after
  checking the selected tool. The shell remains available when it is missing.
  A local SSH test runs a command with a terminal and confirms its input only
  receives the subsequent user bytes. A registry test checks the attach target
  survives failed connections, reopening and service failure.
  Four Android 13 drawer tests pass, including tapping Reconnect all and hiding
  its button once no dropped sessions remain.
  Check quark's startup files, reception loss, duplicate taps, multiple hosts,
  server-key approval, reconnecting screen and tmux, and app process recreation.
- Retest attaching tmux on the phone. Two captured crashes came from drawing
  text outside the terminal's visible area. Text is now measured before drawing;
  an Android test covers drawing beyond each edge without crashing.
  Resizing now fits the visible area and keeps size changes made before SSH
  finishes connecting. Screen attachment uses `-A` to fit its windows.
  Check rotation, keyboard changes and reattachment with live screen and tmux;
  tmux's own window-size setting can limit resizing with multiple clients.
  Settings now has Wrap long lines, enabled by default. Turning it off clips
  new output at the right edge, keeping carriage-return progress on one row.
  Two unit tests cover progress updates and remote attempts to restore wrapping.
  A third checks that changing wrapping waits for the buffer's shared lock,
  avoiding an unsynchronized change while SSH output is being parsed.
  An isolated Android 13 preferences test checks the default, saved choice on
  reopening preferences and reset. Check the switch and an actual process
  restart on the phone. Existing output is not
  reflowed, and programs inside a multiplexer can wrap before sending output.
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
  The Android 14+ declaration test is skipped on Android 13 and passes on an
  Android 14 emulator. Service start and timeout delivery still need a phone.
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
  Saved rows and their credential references are now removed in one database
  transaction while preserving the secret-cleanup list. The list is emptied
  only after encrypted storage clears successfully, so a failed clear can be
  retried by startup cleanup. The Android 13 database test checks both credential
  fields, existing cleanup entries, repeated clearing and final queue removal.
  A second Android 13 test forces identity deletion to fail and verifies that
  the transaction restores the host, identity and original cleanup list.
  Check encrypted-storage failure and restart through the Settings action.
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
  Deletion reloads the current identity inside the shared secret-change lock,
  so an old list or confirmation snapshot does not choose credential references
  for cleanup. Four new unit tests pass for current references, a missing row,
  read failure and cancellation. The Room tests pass on Android 13 for changed
  references and missing rows.
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
  credential field. Startup now retries queued cleanup off the UI thread.
  Settings shows cleanup failures and offers Retry cleanup.
  Identity saves, identity deletion and Clear all data now share a secret-change
  lock. Both suspension and cancelled-waiter unit tests pass. The last Android
  13 regression run passed 58 tests with one API 34+ test skipped. Check
  concurrent save, deletion and Clear all data in the UI; the suite does not
  cover those operations running together. Startup and manual cleanup use
  the same lock.
  The retry helper has five passing unit tests: live references are kept,
  failed usage checks do not delete secrets, deletion failures retain entries,
  failed queue removal can retry and cancellation stops processing. Before
  startup wiring, an Android 13 test
  uses Room and separate encrypted preferences: a failed deletion stays queued
  after database reopening, retry removes the unused secret and both live
  credential fields survive. This is not a process-death test; interrupted
  recording and concurrent writers still need checks.
  Identity deletion now removes the current row and records cleanup references
  in one Room transaction, under the shared lock. Cleanup checks other
  identities before deleting each secret; failed cleanup leaves its entry.
  Build, lint and 12 selected lock/removal unit tests pass. Three new encrypted
  Android tests pass on Android 13 for shared credentials, host-blocked deletion
  and a failed queue insert rolling back deletion. Check failed secret deletion
  during row deletion, process death and UI errors too.
  Identity saves now queue new references before writing secrets. The row
  transaction queues replaced references and removes the newly used entries.
  Failed rollback keeps its cleanup entry, and old-secret cleanup checks other
  identities. Saves reload and validate current credentials under the shared
  lock rather than reusing an older editor snapshot. Build, lint and 11 selected
  cleanup/lock/input unit tests pass. Eight save Android tests pass, with
  queue assertions plus new cases for failed queue insertion and credentials
  changed after loading. Process-death recovery, encrypted rollback and concurrent UI
  operations remain unverified.
  Startup/manual recovery builds and lints successfully; seven selected
  cleanup/lock unit tests pass. The reopening test now calls the production
  recovery class, and two new encrypted tests cover a failed removal followed
  by retry and a failed database read. All nine encrypted-storage tests pass
  on Android 13, including cancelled recovery leaving secrets queued. Room
  cancelling an internal job after database closure reports a storage failure;
  cancelling the caller still cancels recovery. The Settings cleanup card is
  reachable by scrolling, and an empty-queue retry leaves the screen usable
  without an error on Android 13. Two Compose tests pass for the error message,
  retry callback, disabled button while checking and restored button afterwards.
  Check Settings with a real queued failure, startup with queued work, Clear
  all data during recovery and actual process death on Android.
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
  head is not clipped. A cold-start screenshot on the Android 13 emulator also
  shows the splash creature and prompt intact and legible during the launch
  transition. Other masks, themed icons and the phone splash remain unchecked.
- Complete spacing-mark support and check joined-emoji rendering on a device.
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
  Check accent shaping, selection and right-edge output on a device. Joined emoji now share one cell: a
  zero-width joiner glues the next character and skin-tone modifiers glue to the
  one before; four unit tests cover this. Two regional indicators in a row now form one
  two-column flag. The emoji selector U+FE0F widens listed symbols to two columns
  (see terminal-widths.md); U+FE0E narrows listed emoji to one column, and spacing marks
  currently occupy separate cells; a width regression covers Devanagari,
  Bengali and a two-column Vietnamese reading mark. Shaping and selection
  must respect Unicode boundary properties separately from column widths;
  changing all spacing marks to zero width is not sufficient.
  Check editor and tmux output against a desktop
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
  loopback test passed through a real SSH session. On 9 October the live
  emulator test also passed a local forward, a SOCKS5 request that read the
  server banner, and a second shell sharing the first connection after the
  first shell closed, on Android 13. A phone run has not been recorded. Bind and proxy connection failures now appear in the terminal;
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
  On 9 October a live emulator test connected through a jump host (two
  host-key approvals, then a shell command) on Android 13; the phone, two
  different servers and cancellation remain.
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
  The [recording plan](session-recording.md) defines per-connection consent, raw
  output capture, private files, bounded writes, export and interrupted files.
  Implement its writer, SSH lifecycle and UI phases; the plan is not recording support.
  The unwired writer copies incoming buffers, keeps accepted chunks ordered,
  limits chunks to 64 KiB, its queue to 16 chunks and each file to 64 MiB.
  Queue overflow and size limits stop acceptance while draining accepted chunks.
  Stop closes the stream after draining; write and close failures are reported.
  Five unit tests cover buffer reuse, split bytes, draining, limits, a blocked
  writer with a full queue and write/close failures. The unwired file store creates
  UUID-named partial files and renames them only after a successful writer close.
  It lists incomplete files after reopening, copies closed output for export,
  blocks active-file export/deletion and rejects invalid IDs and symbolic links.
  Four file tests cover those paths and preserving existing metadata on a name
  collision. Host labels and start times are stored separately from raw output;
  malformed, missing or oversized metadata falls back to Unknown host with no
  start time. Three metadata tests cover Unicode, bounds, invalid files and
  symbolic links. Each SSH manager now has an off-by-default capture hook before
  parsing incoming output, stopped by reader completion and disconnect. Four
  capture tests cover explicit start/stop, 128 KiB of exact reader output,
  continued output after recording failure and rejecting an active replacement.
  They do not prove a live SSH recording or file finalization on disconnect.
  Closing a connection permanently closes its capture hook, so a delayed start
  cannot attach a writer after disconnect. Two more capture tests cover rejecting
  late starts and draining accepted output on repeated close.
  A coordinator now waits for writer closure before finishing files. It rejects
  duplicate starts, removes files from rejected starts, and reports finalization
  errors separately. Five coordinator tests cover close-driven finalization,
  rejected-start cleanup, duplicate starts, cancelled owners and old Stop actions
  not stopping a newer recording. The Android singleton now owns the coordinator
  independently of a terminal ViewModel, uses a terminal-recordings folder under
  noBackupFilesDir and runs file operations on IO threads. It rejects starts on
  disconnected managers. Three Android tests cover lazy folder creation, rejected
  starts and reading/deleting a separate private test folder. Document output is
  closed after export, but live capture and document-picker export remain untested.
  Clear all data now stops active writers, waits up to five seconds for file
  finalization and deletes closed recording files before clearing saved app data.
  New starts remain blocked through the rest of that cleanup. Two coordinator
  tests cover file removal, later starts and starts attempted during cleanup.
  Cleanup also removes orphan host/time details with valid recording IDs, rejects
  linked files and leaves unrelated files untouched. Three file-store tests cover
  orphan cleanup, linked details and refusing cleanup while a writer is active.
  Live settings interaction, stuck writes and interrupted
  cleanup still need checks. Start/Stop controls now require consent and show
  recording, closing and file errors for the selected connection. Reconnecting
  does not restart recording. Two Android tests cover closing/error status and
  removing the indicator when another connection is selected without stopping
  the original writer. The recordings list now shows host/time/size/completion,
  exports raw output through the document picker and confirms deletion. Two Android
  tests cover list/export selection and cancelled/confirmed deletion. Active files
  remain protected. Live SSH capture, consent, document-provider failures and picker
  cancellation still need checks. Recording is off until explicitly started.
  A failed-output-stream test confirms export preserves original bytes and host/time
  details. Export errors no longer claim the original exists after concurrent deletion.
  Export validates and opens the source before opening the destination, keeping
  deletion blocked through the copy. Two file-store tests cover unopened destinations
  for missing/active files and closing the output stream while preserving the source.
  Android file-URI checks cover raw bytes and a missing source leaving an existing
  destination untouched; external document-provider checks remain.
  Two concurrent file-store tests pause destination opening or byte copying and
  check that cleanup waits, export keeps every byte, and cleanup then removes
  the recording. Stuck providers and process death still need checks.
  The main screen has a Recordings action so saved files can be reached without
  an open connection. Its separate screen keeps file-picker selection across
  rotation through a ViewModel; process death requires selecting the file again.
  Three Android tests cover opening without a connection, closing, cancelled
  selection and an unavailable source's error. A manual Android 13 check opens
  the browser from the real main-screen button with no saved hosts or connections.
  Check rotation during export and returning from a provider.
- Check opening links from selected terminal text on a device. The menu lists
  valid HTTP/HTTPS links for confirmation, rejects credentials and other
  schemes, and reports browser launch failures. Trailing punctuation and mixed
  closing brackets are removed in one pass, keeping balanced URL brackets.
  Three regressions cover 100,000 trailing brackets of each type, nested
  balanced paths and mixed brackets with intervening punctuation.
  Six parsing tests pass;
  wrapped links and the selection-to-browser interaction still need checks.
- Local shell: built in plain Kotlin over pipes (host editor switch "Run a shell on this device", DB version 13). The app echoes and edits typing, so there is no tab completion, history or full-screen programs; a real pty needs native code. Interrupt (Ctrl-C) and Ctrl-D work. Check on the phone: prompt, rotation, Ctrl-C during a long command.
  A local terminal needs a real terminal connection for interactive programs,
  resize handling, input, output and exit status, not a process with plain pipes.
  Keep it separate from SSH host settings, server keys and network reconnects.
  Show it as Local in session lists and keep app-lock protection. Check shell
  startup, interactive editors, resizing, backgrounding, cancellation and
  process exit. Choose a supported Android terminal library that works with
  the Kotlin-only application before adding this session type.
  ConnectBot's [local transport](https://github.com/connectbot/connectbot/blob/main/app/src/main/java/org/connectbot/transport/Local.kt)
  calls its [native process helper](https://github.com/connectbot/connectbot/blob/main/app/src/main/java/com/google/ase/Exec.java)
  for shell creation, terminal resizing and exit watching. Its
  [CMake build](https://github.com/connectbot/connectbot/blob/main/app/CMakeLists.txt)
  compiles that helper inside the app; it is not a drop-in Kotlin dependency.
  Adopting this route needs approval for native source under the Kotlin-only rule.
- Check app lock on supported phones.
  App lock is optional under Settings and defaults to off. It uses Android's
  device-unlock prompt, hides the app's screens when its activity pauses and
  requests screenshot protection while enabled. SSH sessions are not disconnected.
  Navigation is retained; unsaved form input may reset. New processes start locked.
  The setting refreshes after returning from Android settings, and failed saves
  report an error. A device PIN, pattern or password is required before enabling it.
  Check Android 8/16, notification and shortcut launches while locked, cancelled
  prompts after rotation, device-credential removal and concurrent UI operations.
  Clipboard text and connection notifications are not hidden by this screen lock.
  Eight unit tests cover enabling, cancellation, failed/stale results, background
  locking and new processes. Eight Android tests cover protected content,
  retained navigation, screenshot flags, availability refresh and saved settings.
  Manual Android 13 checks confirm real PIN unlock, cancellation staying locked,
  returning from Home, a locked cold restart and rotation without another prompt.
  The emulator's temporary PIN was removed and its app-lock setting disabled.
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
  the host list while a request is running. Re-pinning an existing shortcut
  now re-enables it and updates its label before requesting pinning. Failed
  updates report an error and do not request pinning. Build and lint pass;
  disabled-ID recovery, renamed labels and launcher rate limits still need
  runtime checks. No device was available for these checks.
- Check saved proxies and IPv6 connections on Android.
  The [proxy notes](ssh-proxies.md) cover HTTP CONNECT, SOCKS5, separate password
  identities and first-jump routing. The host editor now saves proxy settings;
  database version 11 defaults existing hosts to None. Disabling clears saved
  fields. Optional proxy passwords use a separate identity in encrypted storage.
  Proxy references block identity deletion; saves check the current identity
  inside their database transaction. Missing credentials stop connections rather
  than falling back to anonymous or direct access. Authenticated saves require a
  warning confirmation because proxy password exchanges are not encrypted.
  Fourteen unit tests check validation, credential limits, hidden credentials,
  separate secret reads, stale identities, failed reads, disabling and routing.
  Six local tests cover real SSH through anonymous and authenticated HTTP/SOCKS5
  proxies, rejected passwords and IPv6 HTTP destination formatting.
  Android tests cover upgrades from versions 6–10, save/reload, duplicated proxy
  rows, identity deletion, stale choices, rejected saves, controls and warning
  confirmation. Live Android connections, changed proxy
  credentials and server-key review through a proxy still need checks.
  Cancellation now closes the pending proxy socket before asking JSch to
  disconnect. Two regression tests failed before this fix for silent HTTP and
  SOCKS5 handshakes, which JSch did not consider connected yet. Seven local
  failure tests pass for cancellation, read timeouts, closing before connecting,
  late socket creation and late session registration. Both silent-proxy
  cancellation checks also pass on Android 13. Android DNS delays, custom socket
  factories and malformed or slowly trickling proxy replies remain to check.
  All seven new Android checks pass on Android 13: the version-10 upgrade,
  three save/identity tests and three control/warning tests. The full Android
  run passes 108 tests and skips the Android 14+ service declaration check.
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
- Check port knocking and the remote performance view on the phone.
  The terminal menu now shows Linux CPU use and its recent history, load,
  memory, root-disk usage, per-interface network rates and available temperatures.
  Readings use fixed read-only commands over SSH; no server software is installed.
  Polling runs two seconds after each completed request while the view is open
  and resumed. Errors stop polling until Retry. Requests have a three-second
  read deadline and a 64 KiB output limit. Missing fields show Unavailable.
  Fifteen unit tests cover parsing, rates, invalid counters and live Linux/SSH
  readings. Four Android tests cover the displayed values, history, retry,
  pausing in the background and interrupting a read when the view closes.
  Check other Linux systems, missing /proc files, uncommon login shells,
  slow links, different temperature sensors and interrupted SSH channels.
  The port-sequence parser accepts comma-separated ports with optional `:tcp`
  or `:udp`, preserving order and repeated ports. Blank disables it. Three unit
  tests cover mixed protocols, boundaries and invalid input. The host editor
  now has an optional setting. The sender supports ordered TCP/UDP steps and
  a pause between them. Five tests cover IPv4/IPv6 local listeners, continuing
  after a refused TCP knock, repeated steps and cancellation during a pause.
  New SSH transports send configured knocks before
  connecting; shared transports do not repeat them. The saved pause accepts
  0–5000 milliseconds. Database version 10 defaults existing hosts to disabled;
  Android upgrade tests from versions 6–9 preserve hosts and store the new fields.
  Only directly reachable hosts and the first jump host support knocking;
  unreachable destinations in a jump chain are rejected instead of being knocked
  from the phone. An Android editor test checks saving/reopening settings, rejecting
  invalid ports without changing stored data, and disabling knocking with an invalid
  hidden pause. Check editor field interaction, IPv6 on Android, startup cancellation, DNS delays,
  actual knockd rules and reconnecting shared transports on a device.
- Split terminals are built: Terminal actions > Split view shows another connected
  session beside (landscape) or below (portrait) the current one. Touching a
  pane focuses it; the focused pane gets the keyboard bar, IME input, paste and
  saved commands, and has the accent border. Each pane resizes its own
  connection. Close split view returns to one pane. Switching sessions or
  leaving ends the split. Three emulator tests cover side-by-side and stacked
  layout and a touch both moving focus and reaching the pane. Not yet checked
  on a phone: typing into each pane, selection and scrolling in the second pane,
  rotation with a split open, a pane's connection dropping, and the toolbar
  hide timer. The second pane has no scrollback indicator or selection menu.
- Designs for split terminals, Telnet, serial and Mosh are in
  [other-sessions.md](other-sessions.md). Build in that order; Mosh is blocked on the
  Kotlin-only rule.
  Telnet is built: `TelnetNegotiator` and `TelnetConnection` (unit tests with a
  fake server), a Telnet path in the connection manager, a `telnet` host column
  (database version 12, migration tested on Android 13), and a Use Telnet switch in
  the host editor that moves the port between 22 and 23 and rejects tunnels, jump
  hosts, proxies, knocking and agent forwarding. An emulator test connects to a
  fake server, answers its option requests, shows its text, sends input and sees
  it close. Still to check on a phone: the editor switch, reconnect after a drop,
  and real servers. The host list labels Telnet hosts; open session rows do not.
- Assess Mosh separately: it uses UDP and cannot share SSH port-forwarding
  behavior. Implement it as a separate session type.
- Add serial connections and opt-in encrypted
  backup/sync. `BackupCipher` (AES-256-GCM, PBKDF2-SHA256 at 600,000 rounds,
  authenticated header, four unit tests) exists as the encryption layer; there is
  no backup file format, export/import screen or sync yet. These need separate protocol and data-handling designs.

ConnectBot's [SSH library](https://github.com/connectbot/cbssh) lists local,
remote, and dynamic forwarding. [Termius for Android](https://www.termius.com/free-ssh-client-for-android)
lists SSH, SFTP, Mosh, Telnet, and serial connections. [Sonelli's JuiceSSH repositories](https://github.com/sonelli)
include Mosh, port-knocking, performance-monitor, and plugin code. The
[JuiceSSH plugin library](https://github.com/Sonelli/juicessh-pluginlibrary)
includes an SFTP client API. The original LochSSH spec also calls for a
customizable keyboard.

## Automatic reattach and tool detection

- A dropped connection to a tmux or Screen session (attached from the chooser, or a host set
  to Auto tmux or Auto screen) reconnects by itself: up to five tries, waiting 2, 5, 10, 20 and
  30 seconds, shown as "Connection lost. Reconnecting in N seconds". Clean exits and manual
  disconnects do not retry. Logic is in `ssh/AutoReattach.kt` and `SshForegroundService`; the
  timing and retry rules have unit tests but the loop itself has not run against a real drop.
  Check on the phone: toggle airplane mode, then see the same tmux session come back.
- The host editor offers Auto tmux and Auto screen only after a connection has found that tool
  on the host (`tmux_seen` and `screen_seen`, DB version 14). The terminal's remote session
  dialog already offered New tmux and New Screen only when found.

## Tap to open links

- Tapping a link in terminal output asks "Open link in browser?" with that link, using the same
  checks as selected text (HTTP/HTTPS only, no credentials). The tap handler does not consume the
  touch. The tap itself needs a device check against scrolling, selection and tmux
  mouse mode. The buffer records automatic wraps, including their original column
  boundary. Tapping and selection join those rows without absorbing unrelated text
  after an explicit newline. Wraps follow rows into scrollback and survive a width
  increase; inserted/deleted rows break the old boundary. A wide character's unused
  padding cell is excluded. Eight link tests cover those cases and ordinary links.
  Check Claude authentication URLs in a live session: application-inserted hard
  breaks are not automatic wraps. Links beyond the visible viewport, width reductions
  and full-screen redraws need more checks. Links are not underlined yet, and the split
  view's second pane does not open links.
