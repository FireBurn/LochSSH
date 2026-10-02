# LochSSH

LochSSH is an Android SSH client. It is under active development and is not ready for general use.

## What works

- Saved hosts and identities with password or private-key authentication.
- Ed25519 key generation where Android supports it, with RSA as a fallback.
- Multiple SSH terminal sessions, an in-terminal session drawer and a customizable terminal keyboard.
- Local, remote, and local SOCKS5 port forwarding.
- A command that runs when a host connects, or automatic tmux or Screen startup.
- A remote tmux and Screen session picker.
- Saved jump hosts and optional SSH-agent forwarding.
- Saved SSH host keys. New keys need fingerprint approval; changed keys block connections.
- SFTP browsing and file transfers, saved commands, and connection history.

Check a new server's displayed fingerprint with its owner before choosing
Trust and connect. The prompt identifies the server, including jump hosts.
Review or remove saved keys under Settings → Saved host keys. Existing saved
keys are kept. Check [the work list](docs/TODO.md) before relying on the app for
sensitive connections.

## Homescreen shortcuts

Choose Add to homescreen from a saved host's menu. A supported launcher asks
before adding the shortcut. Opening it loads the current host and asks before
connecting; a deleted host shows an error. The shortcut stores only the host ID,
not credentials. Launcher interaction still needs device checks.

## Build

Use JDK 25 and an Android SDK with API 35 installed:

```sh
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
```

Run the instrumented tests with a connected Android device:

```sh
./gradlew :app:connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Android 8.0 (API 26) or newer is required.

The live IPv6 tests use `LOCHSSH_TEST_SSH_IPV6_HOST`, `LOCHSSH_TEST_SSH_HOST`,
`LOCHSSH_TEST_SSH_USER` and `LOCHSSH_TEST_SSH_KEY`. Both host values must reach
the same SSH server on port 22. The test host must already be in `known_hosts`;
the IPv6 alias is kept in memory. Run `:app:testDebugUnitTest --tests '*Ipv6ConnectionTest'`
with those variables set. Without them, these tests are skipped.

The app currently includes local JSch and Android-support JARs in `app/libs/` for Android Ed25519 authentication and X25519 key exchange. See [the work list](docs/TODO.md) and [the upstream PR](https://github.com/mwiede/jsch/pull/1160). Do not replace them with the original `com.jcraft:jsch` library.

## Screen-off connections

Settings → Screen-off connections shows whether LochSSH is exempt from Android's
battery optimization. Request battery exemption opens an Android prompt; nothing
is requested automatically. If the prompt is unavailable, the app opens battery
settings or its Android app settings instead.

The terminal menu also has a Background connections shortcut. SSH keep-alives
default to 30 seconds; change the interval in the host editor. They cannot prevent
Android from killing the app. If Android removes the connection, reopen the host
and find its remote sessions again.

Allowing background access may use more battery. Other system limits, network
changes and remote timeouts can still close a connection.

## Remote tmux and screen

In the host editor, choose Shell, Ask, Auto tmux, Auto screen, or Find and connect
all remote sessions. Shell opens a normal shell and can run a saved command. Ask
lists existing sessions and offers a new one. Auto tmux attaches to or creates a
session named `lochssh`; Auto screen resumes a session or creates one. Find and
connect all opens each discovered remote session in its own terminal, sharing the
SSH connection. If none are found, keep using the shell.

Open Remote sessions from the terminal menu to list sessions. Attaching or creating
one opens a separate terminal; it never types the attach command into the current
program. An unused shell closes after the new connection succeeds. A shell where
you have sent input stays open. Tmux names include the active program and window;
these descriptions are read when sessions are discovered.
Full-screen or mouse-using programs started by login files also stay open.

Hold an arrow or Page Up/Down key to repeat it. Dragging sends wheel events when
the remote program enables mouse input; otherwise it scrolls terminal history.
Tmux may require its `mouse` option to be enabled to pass wheel events through.

## Jump hosts and agent forwarding

Choose a saved **Jump host** in the host editor to connect through it. Each hop
uses its own identity and server-key checks. A jump host may use another jump;
loops are rejected. Hosts used as jumps cannot be deleted until those links
are removed.

**Forward SSH agent** is off by default and needs a private-key identity. It
lets the destination host use that connection's key for SSH authentication
elsewhere while connected. The private key stays on the phone, but a
compromised host can still ask it to sign. Enable only for hosts you trust.
Intermediate jump hosts do not receive a forwarded agent.

## Terminal keyboard

Open Settings → Terminal keyboard to change keys and their order. Add up to four rows with ten keys each, including short text keys. Reset restores the default layout; Cancel discards edits. Ctrl, Alt and Fn stay selected until a sending key is pressed.

## License

BSD 3-Clause. See [LICENSE](LICENSE).

Unicode character data uses [Unicode License V3](licenses/Unicode.txt).
JetBrains Mono v2.304 uses the [SIL Open Font License](app/src/main/assets/jetbrains-mono-license.txt).
The bundled regular and bold files come from the upstream no-ligature family.
