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

The app currently includes local JSch and Android-support JARs in `app/libs/` for Android Ed25519 authentication and X25519 key exchange. See [the work list](docs/TODO.md) and [the upstream PR](https://github.com/mwiede/jsch/pull/1160). Do not replace them with the original `com.jcraft:jsch` library.

## Screen-off connections

Settings → Screen-off connections shows whether LochSSH is exempt from Android's
battery optimization. Request battery exemption opens an Android prompt; nothing
is requested automatically. If the prompt is unavailable, the app opens battery
settings or its Android app settings instead.

Allowing background access may use more battery. Other system limits, network
changes and remote timeouts can still close a connection.

## Remote tmux and screen

In the host editor, choose **Shell**, **Ask**, **Auto tmux**, or **Auto screen**. Shell opens a normal shell and can run a saved command. Ask checks for tmux and Screen on each connection and offers existing sessions or a new one. Auto tmux attaches to or creates a session named `lochssh`; Auto screen resumes a session or creates one. Open **Remote sessions** from the terminal menu to list sessions. Attaching or creating a session sends a command to the current terminal; use it at a shell prompt, not while another program is running.

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
