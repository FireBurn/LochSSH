# LochSSH

LochSSH is an Android SSH client. It is under active development and is not ready for general use.

## What works

- Saved hosts and identities with password or private-key authentication.
- Ed25519 key generation where Android supports it, with RSA as a fallback.
- Multiple SSH terminal sessions and a key bar for terminal controls.
- Local, remote, and local SOCKS5 port forwarding.
- A command that runs when a host connects.
- Saved SSH host keys. The first key is accepted; a changed key blocks later connections.

The first host key is not shown for approval, and there is no in-app way to inspect or remove saved keys yet. Check [the work list](docs/TODO.md) before relying on the app for sensitive connections.

## Build

Use JDK 25 and an Android SDK with API 35 installed:

```sh
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Android 8.0 (API 26) or newer is required.

The app currently includes a patched JSch JAR in `app/libs/` for Android Ed25519 authentication. See [the work list](docs/TODO.md) and [the upstream PR](https://github.com/mwiede/jsch/pull/1160). Do not replace it with the original `com.jcraft:jsch` library.

## Remote tmux and screen

The host editor has a **Run on connect** field. For example, `tmux new-session -A -s main` attaches to a tmux session named `main`, or creates it if needed. This sends a command to the remote shell; LochSSH does not yet find or manage tmux or GNU Screen sessions itself.

## License

BSD 3-Clause. See [LICENSE](LICENSE).
