# Other session types

Order of work, easiest first. Each is a separate session type; none changes SSH
host settings, server keys or the shared-connection logic.

## Split terminals (no new protocol)

Show two open sessions at once. Landscape and tablets: side by side. Portrait:
stacked, each at least 12 rows. The keyboard bar and the IME input go to the
focused pane; focus changes on tap. Each pane keeps its own `TerminalSession`,
size and scrollback, so resizing sends a separate window-size change per
connection. Needs: a layout state (which two session IDs, orientation), focus
state in the view model, and the existing resize path called per pane. Tests: layout
choice for each size, focus moving, input reaching only the focused connection,
and a pane closing while the other stays. Kotlin only.

## Telnet

Plain TCP to a host and port, shown as `Telnet` in session lists. Kotlin only, no
library: a small option negotiator for the RFC 854 command bytes. Answer
terminal type (xterm-256color), window size (NAWS, sent again on resize), echo
and suppress-go-ahead; refuse everything else; double 0xFF in output both ways.
Warn on first use that traffic and passwords are not encrypted, and never offer
saved passwords for Telnet. No reconnect on network change; a dropped socket is
shown as dropped. A new `TELNET` host kind in the database (migration, host
editor fields limited to name, host, port). Tests: negotiation replies from a
byte script, 0xFF escaping, split reads, and a local server round trip.

## Serial

USB serial adapters over OTG using the `usb-serial-for-android` library (Java,
MIT). Host kind `SERIAL` with baud rate, data bits, parity, stop bits and flow
control. Ask for the Android USB permission when connecting; list attached
devices to choose one. No automatic reconnect; unplugging ends the session with a
clear message. Terminal size does not apply. Tests: settings validation and a
loopback through a fake port; the real adapter needs a phone with OTG.

## Mosh

Built in plain Kotlin (package `mosh`), no native code. The host editor has a "Use Mosh" switch. Connecting
signs in over SSH with the normal host settings, runs `mosh-server new` over an exec channel (it tries
`C.UTF-8` then `en_US.UTF-8`, sets `MOSH_SERVER_NETWORK_TMOUT` to three days, and runs the tmux or Screen
command for Auto and New tmux), reads the `MOSH CONNECT port key` line, drops the SSH link and carries on
over UDP.

The protocol layer follows the Mosh source: AES-128-OCB packets (BouncyCastle, already bundled) with an
8 byte nonce whose top bit is the direction, zlib-compressed fragments, and a small hand-written protobuf
codec for the instruction, host and user messages. The client numbers its events as states, resends until
the server acknowledges, sends a heartbeat every three seconds, answers the server's state numbers, and
opens a new socket when the network changes, which the server accepts as roaming. Leaving a session sends
the shutdown instruction so the server's shell ends.

Not done: local echo prediction, and jump hosts, proxies, tunnels and agent forwarding (the editor refuses
them). The UDP port the server picks must be reachable, and the host needs `mosh-server`.

Tests: protocol unit tests; `MoshInteropTest` starts a real `mosh-server` when `MOSH_SERVER` names one
(types, sees the screen change, resizes, survives a broken socket, shuts down); and a live test through the
app (`LiveSshAndroidTest`, `-e mosh true`).
