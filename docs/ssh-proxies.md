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

The host editor and database still need proxy fields. Add None, HTTP CONNECT
and SOCKS5 choices, an address and a port. Existing hosts must default to None.
Disabling the choice must clear its saved fields and ignore hidden input.

Use a separate saved password identity for optional proxy authentication. This
keeps the password in the existing encrypted store and separates it from SSH
login. Allow anonymous proxies without an identity. Reject key-only identities,
missing rows and missing passwords. Include proxy references in identity-use
checks so deletion cannot leave a host with missing credentials. Validate the
current identity inside the host-save transaction.

Show a warning before saving authenticated proxy settings: HTTP Basic and SOCKS5
password authentication are not encrypted between the phone and the proxy:
see [HTTP Basic](https://www.rfc-editor.org/rfc/rfc7617.html#section-4) and
[SOCKS5 password authentication](https://www.rfc-editor.org/rfc/rfc1929.html#section-3).
Only use them over a trusted network or VPN. SSH protects the connection to the
SSH server after the proxy handshake, not the proxy password exchange.

Check migration, host duplication, identity deletion, save failures, disabling
settings, jump-host selection and reconnecting after a proxy change. Check
timeouts and cancellation against an unresponsive proxy, strict server-key
review through a proxy, IPv6 destinations and live Android connections.

## Current checks

Seven unit tests cover addresses, ports, credential limits, hidden credentials,
jump-host rules and rejecting port knocking through a proxy. Six local protocol
tests cover HTTP and SOCKS5, each with anonymous and password-authenticated SSH
login and a command, rejected passwords and HTTP IPv6 destination formatting.
The listeners use loopback addresses and the existing trusted local test server.
No proxy settings are exposed in the app yet.
