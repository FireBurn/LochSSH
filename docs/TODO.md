# Work left

This list reflects the current app and the project conversations. The older
`HANDOVER.md` predates the terminal, editor, and icon fixes.

## Fix before wider use

- Store and check host keys across connections. `SshConnectionManager` uses
  `accept-new` but does not give JSch a persistent known-hosts file. A changed
  key must be rejected and shown to the user, not accepted as a new host.
- Make host and forward edits atomic. `HostEditorViewModel.save` updates the host,
  deletes its forwards, then inserts replacements in separate database calls.
  A failed insert can leave a partial configuration. Apply the same rule to host
  duplication and deletion.
- Preserve identity secrets if a database save fails. `IdentityEditorViewModel`
  deletes old key material before updating its row. Test switching key sources,
  failed saves, and deletion of unused secrets.
- Check terminal behavior on a device with long output, full-screen programs,
  command history, resizing, reconnecting, and multiple open sessions. The
  Claude conversation reported stale text in command history, odd full-screen
  output, and session title errors; subsequent commits addressed some of these,
  but there is no device regression run for the current build.
- Resolve Android Ed25519 without relying on the current JSch patch. PR
  [mwiede/jsch#1160](https://github.com/mwiede/jsch/pull/1160) has a requested
  architecture change. Its API 33 and 35 emulator jobs skip signing; the API 36
  image fails the mandatory signing check. The reviewer suggested a separate
  Android artifact. Keep the app's patched JAR until a verified replacement is
  installed.

## Complete the original app spec

- Add dynamic SOCKS5 forwarding and validate local listener ports in the editor.
  Local and remote forwarding exist; dynamic forwarding does not.
- Add a per-host command to run on connect, including a `tmux` option.
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
- Assess Mosh separately: it uses UDP and cannot share SSH port-forwarding
  behavior. Do not present it as a small SSH toggle.
- Decide whether Telnet, serial connections, sync, and split terminals fit
  LochSSH before adding them. They extend beyond the original SSH spec.

ConnectBot's [SSH library](https://github.com/connectbot/cbssh) lists local,
remote, and dynamic forwarding. [Termius for Android](https://www.termius.com/free-ssh-client-for-android)
lists SSH, SFTP, Mosh, Telnet, and serial connections. The original LochSSH
spec also calls for dynamic forwarding, automatic commands, and a customizable
keyboard.
