# Work left

This list reflects the current app and the project conversations. The older
`HANDOVER.md` predates the terminal, editor, and icon fixes.

## Fix before wider use

- Check host-key review and removal on a device, including a changed server key.
  The app now lists fingerprints and removes a selected key after confirmation.
- Verify host duplication and deletion failures on a device. The host list now
  shows an error dialog, and deletion requires confirmation.
- Add device tests for identity changes and failures. The editor now saves new
  secrets before updating the row and removes old secrets afterward, but this
  needs tests for key-source switches, failed writes, and cleanup failures.
- Check terminal behavior on a device with long output, full-screen programs,
  command history, resizing, reconnecting, and multiple open sessions. The
  Claude conversation reported stale text in command history, odd full-screen
  output, and session title errors. Unit tests now cover command recall and
  alternate-screen cursor restoration, but there is no device regression run
  for the current build.
- Compare the launcher and splash icons with the amber Nessie Gemini artwork at
  actual device sizes. The current vectors trace that artwork, but the final
  rendered result has not been checked on a device.
- Resolve Android Ed25519 without relying on local JSch JARs. PR
  [mwiede/jsch#1160](https://github.com/mwiede/jsch/pull/1160) now adds a separate
  Android artifact and passes Android emulator CI, Maven CI and the SonarCloud
  quality gate.
  Stock emulator images still lack imported-key Ed25519 signing, so that test
  needs a provider installed for coverage. Wait for maintainer review and a
  published release before replacing the app's local JARs.

## Complete the original app spec

- Test SOCKS5 forwarding through a live SSH server on a device. A JVM-side
  loopback test passed through a real SSH session, but no Android tunnel run has
  been recorded. Surface tunnel bind and connection failures in the UI.
- Verify command-on-connect and tmux/Screen session selection on a device,
  including the 2→3 and 3→4 database migrations for existing hosts.
- Let users customize the terminal key bar. Modifier latching and the DEL key
  exist, but the layout is fixed.
- Add terminal selection, copy, paste, and scrollback. The current terminal draws
  a live grid with no selection or scrollback controls.
- Finish private-key support and tests for imported RSA, Ed25519, ECDSA, and
  PKCS#8 keys, including passphrase handling.
- Confirm the foreground service and battery settings on supported Android
  versions. The service has a wake lock; there is no battery-optimization request.

## Broader SSH-client features

- Add an SFTP browser with upload and download.
- Add saved command snippets and a connection history.
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
