# Data and battery use

What the app sends while connected, measured against a real server (Quark, 372 processes) on 29 Oct 2026.

## Compression

SSH traffic is compressed with `zlib@openssh.com`, which starts after sign-in. It is on by default and has a switch
in Settings ("Compress SSH traffic"). It applies to the next connection and only when the server offers it; a server
without it connects uncompressed. A live test checks both the compressed and uncompressed negotiation. Mosh compresses
its own traffic, so the switch does not affect it. Telnet and serial are not compressed.

Typed keys and short prompts gain little. Listings, logs and anything that scrolls compress well: the process list the
app reads for session names is 18 KB as text and 3.4 KB compressed.

## Background work

Before 29 Oct the app ran four commands over SSH every 15 seconds on each host with tmux sessions, including a full
`ps -e`, whether or not the app was on screen. That was about 18 KB of output plus four channel round trips per scan,
roughly 5 MB an hour per host uncompressed.

Now:

- Nothing is polled while the app is hidden. A scan runs two seconds after the app comes back, and every 15 seconds
  while it is on screen.
- A scan is one command (the tmux listing and the process list together), not four.
- Compression applies to that traffic too.

The SSH keep-alive is a few bytes every 30 seconds by default (it is a setting per host; 0 turns it off, at the cost
of noticing a dead connection later).

## Battery

The foreground service holds a partial wake lock while any session is open (12 hours at most), so the phone does not
sleep the connection. Mosh is the better choice on a flaky or metered network: it survives sleep and network changes
without reconnecting.

## Not done

- Per-host compression choice (it is one switch for the whole app).
- Cutting the scan further: it still reads the whole process list; reading only the panes' process trees would need a
  server-side script.
