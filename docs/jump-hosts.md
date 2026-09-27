# Jump hosts

## Host settings

Add an optional saved-host selector to the host editor. Each jump uses its own
identity, password or key, port and keep-alive settings. A host with no jump
connects directly. Existing hosts keep that behaviour after migration.

Reject missing hosts and cycles before connecting. Deleting a jump host must
not silently turn a dependent host into a direct connection.

## Connection

Resolve the chain before starting the handshake. Connect the outermost host
first, then carry the next SSH connection over a direct-tcpip channel. Do not
open a listening port on the phone. Do not launch shells, commands or saved
forwards on intermediate hosts.

Check each server key against its original hostname and port. Do not use a
loopback name or skip host-key checks. Key review must identify the host in the
chain whose key needs approval. Never forward an authentication agent merely
because a host is used as a jump.

The connection manager owns every intermediate session and channel. Failed
authentication, rejected keys, cancellation and destination disconnect must
close the entire chain. SFTP and forwards use the destination session.

## Checks

- Room migration preserves existing hosts, duplication and saved forwards.
- Missing hosts, self-reference and longer cycles fail with a useful message.
- Two SSH servers verify independent passwords and keys, hostname checks and
  destination DNS lookup through the jump.
- Destination shell, SFTP and forwarding work through the chain.
- Cancelling each handshake and losing the jump leaves no session or socket.
- Device tests cover the selector, host-key prompts, retries and cleanup.
