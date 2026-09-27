# LochSSH

LochSSH is an Android SSH client. It is under active development and is not ready for general use.

## What works

- Saved hosts and identities with password or private-key authentication.
- Ed25519 key generation where Android supports it, with RSA as a fallback.
- Multiple SSH terminal sessions and a customizable terminal keyboard.
- Local, remote, and local SOCKS5 port forwarding.
- A command that runs when a host connects, or automatic tmux or Screen startup.
- A remote tmux and Screen session picker.
- Saved SSH host keys. The first key is accepted; a changed key blocks later connections.
- SFTP browsing and file transfers, saved commands, and connection history.

The first host key is not shown for approval. Review or remove saved keys under Settings → Saved host keys. Check [the work list](docs/TODO.md) before relying on the app for sensitive connections.

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

## Remote tmux and screen

In the host editor, choose **Shell**, **Ask**, **Auto tmux**, or **Auto screen**. Shell opens a normal shell and can run a saved command. Ask checks for tmux and Screen on each connection and offers existing sessions or a new one. Auto tmux attaches to or creates a session named `lochssh`; Auto screen resumes a session or creates one. Open **Remote sessions** from the terminal menu to list sessions. Attaching or creating a session sends a command to the current terminal; use it at a shell prompt, not while another program is running.

## Terminal keyboard

Open Settings → Terminal keyboard to change keys and their order. Add up to four rows with ten keys each, including short text keys. Reset restores the default layout; Cancel discards edits. Ctrl, Alt and Fn stay selected until a sending key is pressed.

## License

BSD 3-Clause. See [LICENSE](LICENSE).
