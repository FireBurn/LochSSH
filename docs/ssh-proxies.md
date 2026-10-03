# SSH proxies

HTTP CONNECT and SOCKS5 carry the first SSH connection. They do not replace
SSH authentication or server-key checks. SOCKS5 sends the destination name to
the proxy instead of resolving it on the phone. HTTP destinations using IPv6
need brackets in the CONNECT request, as described by
[HTTP CONNECT](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.6).

The connection settings accept a proxy address, port and optional username and
password. Invalid settings fail before connecting. Proxy authentication failure
does not fall back to a direct connection. Proxy credentials are separate from
the server's SSH credentials and are omitted from the proxy settings' text
representation.

With jump hosts, configure the proxy on the first jump host. Later hosts are
reached through SSH, not through a second proxy. Port knocking cannot run through
an HTTP or SOCKS proxy. Shared SSH connections include the proxy settings when
checking whether another terminal can reuse them.

## Saved settings

The host editor has None, HTTP CONNECT and SOCKS5 choices, an address and a port.
Database version 11 adds these fields; existing hosts default to None.
Disabling the choice clears its saved fields and ignores hidden input.

Use a separate saved password identity for optional proxy authentication. This
keeps the password in the existing encrypted store and separates it from SSH
login. Leave the proxy identity at None for anonymous access. Key-only identities
and missing rows are rejected. Identity-use checks include proxy references so
deletion cannot leave a host with missing credentials. Saving checks the current
identity inside the host-save transaction. Connecting reads the password from
encrypted storage and rejects missing passwords or unsupported credential lengths.

Saving authenticated proxy settings requires a warning confirmation. HTTP Basic and SOCKS5
password authentication are not encrypted between the phone and the proxy:
see [HTTP Basic](https://www.rfc-editor.org/rfc/rfc7617.html#section-4) and
[SOCKS5 password authentication](https://www.rfc-editor.org/rfc/rfc1929.html#section-3).
Only use them over a trusted network or VPN. SSH protects the connection to the
SSH server after the proxy handshake, not the proxy password exchange.

Cancellation closes the pending proxy socket before disconnecting the SSH
session. Closing an attempt also prevents later socket or session registration.
This is needed because JSch ignores disconnect during the proxy handshake.
Sockets use the connection timeout for TCP setup and handshake reads.

Check reconnecting after a proxy or its password identity changes, strict
server-key review through a proxy, IPv6 destinations and live Android connections.
System DNS lookups and custom socket factories can still take time to return;
cancellation closes their sockets once available rather than opening a late
connection. Check Android DNS delays and bound malformed or slowly trickling
proxy responses.

## Current checks

Seven unit tests cover addresses, ports, credential limits, hidden credentials,
jump-host rules and rejecting port knocking through a proxy. Six local protocol
tests cover HTTP and SOCKS5, each with anonymous and password-authenticated SSH
login and a command, rejected passwords and HTTP IPv6 destination formatting.
The listeners use loopback addresses and the existing trusted local test server.
Seven saved-settings unit tests cover separate credential reads, missing or
changed identities, failed password reads, disabled fields and route validation.
Android tests cover upgrades from database versions 6–10, saving and reopening,
duplicated rows, blocked identity deletion, stale identities, failed validation,
proxy controls, disabling and warning confirmation. Seven local failure tests
cover silent HTTP/SOCKS5 cancellation, read timeouts, closing before connection,
late socket creation and late session registration. The two cancellation tests
failed before the socket-close fix. Both cancellation checks also pass on the
Android 13 emulator using local silent proxy listeners. Live Android SSH
connections through a proxy
and the failure checks above remain unverified.
