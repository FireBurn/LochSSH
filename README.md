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

## Create and install a key

Create an identity, enter its name and username, choose PUBLIC_KEY,
then Generate key pair. Save and install on server saves the private key in the
phone's encrypted storage before connecting. Enter the server address, SSH port
and a one-time password. New server keys need fingerprint approval; changed
server keys are rejected. The password is not saved and must be entered again
after a failed attempt or server-key review.

The installer adds the public key to `~/.ssh/authorized_keys` over SFTP, keeps
existing entries and skips duplicates. It sets `.ssh` to 700 and `authorized_keys`
to 600, then checks a new connection using the key. Choose the saved identity in
the host settings after success. This flow needs a directly reachable server
with password authentication and SFTP enabled, using the standard authorized-keys
path. Jump hosts and port knocking are not part of this setup flow.

## Homescreen shortcuts

Choose Add to homescreen from a saved host's menu. A supported launcher asks
before adding the shortcut. Opening it loads the current host and asks before
connecting; a deleted host shows an error. The shortcut stores only the host ID,
not credentials. Launcher interaction still needs device checks.

## Connection proxies

In the host editor, choose HTTP CONNECT or SOCKS5 under Connection proxy and
enter the proxy address and port. Leave Proxy login at None for anonymous
access, or choose a separate password identity created under Identities. The
proxy password stays in encrypted storage, not the host row.

Proxy password exchanges are not encrypted. Only use a trusted network or VPN;
SSH does not protect the proxy handshake. Saving an authenticated proxy requires
confirmation. For jump hosts, configure the proxy on the first jump host.
Port knocking cannot run through a proxy. Choose None to clear proxy settings.
See [proxy checks and limits](docs/ssh-proxies.md); live Android connections still
need testing.

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

## App lock

Settings → App lock uses Android's device-unlock prompt. It is off by default
and needs a device PIN, pattern or password. When enabled, leaving LochSSH locks
its screens without disconnecting SSH sessions. Unlocking returns to the same
screen; unsaved form input may reset.

While enabled, LochSSH asks Android to
[block screenshots and recent-app previews](https://developer.android.com/security/fraud-prevention/activities).
This protects the app's screens, not copied text, connection notifications or
already-running SSH sessions. Android's background limits still apply.

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

The main screen groups sessions by saved host, with its name and address above
the remote-session rows. Different hosts stay separate even if their names match.

From a host's Actions menu, choose Find and connect all remote sessions. The
button above the host rows finds and connects remote sessions on all saved hosts.
These actions do not change saved startup settings. Dropped remote-session rows
are reused, and already-open targets are not opened again. Pending discovery and
server-key reviews are kept. Reconnect all dropped sessions restores known
connections without searching the servers again.

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
Wheel reports follow the legacy, UTF-8 or SGR encoding requested by the remote program.

## Server performance

Open Performance from a connected terminal's menu for Linux CPU use, load,
memory, root-disk usage, network rates and available temperature sensors. CPU
and network rates need two readings; CPU history keeps the last 30 values.
Readings run every two seconds after the previous request finishes, only while
the view is open and the app is visible. Missing sensors show Unavailable.
The view uses read-only SSH commands and does not install software on the server.
Failed requests stop polling until Retry or reopening the view.

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
