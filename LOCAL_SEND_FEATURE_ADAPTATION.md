# LocalSend-inspired adaptation plan

This project is an independent Android implementation. The LocalSend fork used for technical study is Apache-2.0 licensed: https://github.com/lei56602-glitch/localsend

## Compatibility boundary
- Keep QuanLian's existing QUANLIAN2 TCP file-transfer protocol and current Android UI.
- Do not claim wire compatibility with LocalSend. LocalSend currently uses a different HTTP/TLS API and certificate-fingerprint identity model.
- Reimplement compatible discovery and reliability concepts in Java; do not copy its Flutter/Rust implementation wholesale.

## Adaptation checklist
1. UDP multicast discovery alongside Android NSD.
2. Stable installation/device identity, separate from the display alias.
3. Deduplicate discoveries by stable identity, not display name.
4. Confirm a discovered endpoint with an app-specific TCP probe before showing it as sendable.
5. Expire UDP-only devices when announcements stop; keep NSD state independently.
6. Keep a discovery event/store model so additional channels can be added later.
7. Add bounded retries and subnet scanning as a fallback for networks that suppress multicast.
8. Add authenticated transfer identity and TLS/channel protection in a later protocol version; do not pretend a plain UDP announcement is authentication.
9. Add favorites/manual endpoint discovery, transfer history, resumable transfer, and robust per-file progress where compatible with the existing app.

## Attribution and license
LocalSend is Copyright 2022-2026 Tien Do Nam and contributors, licensed under Apache License 2.0. This document records it as a technical reference. Any future direct reuse of LocalSend source must preserve its license, copyright notices, identify modified files, and include the license/NOTICE obligations applicable to the reused files.
