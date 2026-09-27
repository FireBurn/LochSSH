# Work left

This list reflects the current app and the project conversations. The older
`HANDOVER.md` predates the terminal, editor, and icon fixes.

## Fix before wider use

- Check host-key review and removal on a device, including a changed server key.
  The app now lists fingerprints and removes a selected key after confirmation.
- Verify host duplication and deletion failures on a device. The host list now
  shows an error dialog, and deletion requires confirmation. Host-editor save
  errors now return to the UI without escaping the coroutine; test a failed save.
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
  versions. The service has a wake lock; there is no battery-optimization request.

## Broader SSH-client features

- Check SFTP browsing, upload, download, and overwrite prompts against a live
  server on a device. The current build uses an extra channel on an open session.
- Check saved-command insert, run, edit, delete, and the 4→5 database migration
  on a device. Check connection history and the 5→6 migration on a device,
  including failed, cancelled, and interrupted connections.
- Add jump hosts and SSH-agent forwarding.
- Add terminal themes, font choices, URL opening, and saved session transcripts.
- Add local shell sessions, homescreen shortcuts, and an app lock.
- Add IPv6 connection tests, proxy settings, and external-keyboard shortcuts.
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
