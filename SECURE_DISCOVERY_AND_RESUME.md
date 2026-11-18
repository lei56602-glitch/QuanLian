# Secure discovery and resumable transfer (QuanLian protocol v3)

## Implemented in this branch
- IPv4 subnet fallback scan on the fixed receive port (38890), bounded to at most 1,022 addresses and a 12-second scan window.
- Manually entered IP and port endpoints, with TLS certificate fingerprint inspection and an explicit first-use trust confirmation.
- Persistent favorite endpoints storing host, port, and the pinned SHA-256 certificate fingerprint.
- A per-install RSA identity generated and held by Android Keystore; TLS 1.2/1.3 sockets use certificate pinning. A previously trusted device whose fingerprint changes is rejected.
- Discovery announcements (UDP and NSD TXT attributes) carry the TLS certificate fingerprint. A discovered endpoint is accepted only after a pinned TLS probe.
- QUANLIAN3 transfer framing: 1 MiB data chunks, per-chunk offset acknowledgements, SHA-256 integrity validation, and persistent partial files in app-private files storage.
- Resume identity combines sender installation identity and file SHA-256, preventing unrelated senders from sharing a partial-file path.
- Sender reads the selected content URI twice (hash pass, then transfer pass) instead of making a full duplicate in cache, reducing local storage pressure for large files.

## Security model and limitations
- First-use device trust is explicit for manual entry and before sending to a newly discovered device. After trust, the app pins the certificate fingerprint and blocks changed certificates.
- The discovery packet itself is not cryptographically signed. The first trust decision is therefore a trust-on-first-use decision; verify the displayed fingerprint through a separate trusted channel for high-assurance deployments.
- TLS authenticates the receiving device to the sender and encrypts the transfer. The sender's display name is not a cryptographic identity proof to the receiver.
- Local subnet scanning is a fallback for the fixed receive port; if that port is occupied and the receiver falls back to an ephemeral port, NSD/UDP discovery remains the supported route.
- QUANLIAN2 is still accepted by the receiver for backward compatibility. QUANLIAN3 is used by the new sender. TLS pinning requires both endpoints to run this upgraded branch; older plain-TCP builds are not compatible with the new sender.
- Interrupted partial files remain app-private until a successful SHA-256 check; stale partial-file cleanup and per-transfer quotas should be added before production.

## Not yet production-verified
- Cross-device Android Keystore TLS interoperability on real phone/TV hardware.
- Wi-Fi access points that suppress multicast or client-to-client traffic.
- Automatic reconnect/retry UX after a connection drops. A user retry re-enters the QUANLIAN3 session and resumes from the receiver's persisted offset.
- Stress tests for very large files, low disk space, multiple simultaneous senders, and app process death.
