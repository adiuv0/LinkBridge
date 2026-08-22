# LinkBridge 0.5.0 public beta

This first public beta contains the physically tested Bluetooth-only workflow and a permanently signed Android phone APK.

## Highlights

- Supernote plugin package rebuilt without internet permission or sensitive SDK parameter logging.
- Version-pinned Obsidian 3.1.2 compatibility bundle with automatic backup and restore tools.
- Exact external URL targets preserved through the `.note` parser and clickable in Obsidian.
- Phone outbox, retry, save-confirmed acknowledgement, and duplicate prevention physically verified.
- Exact release APK and `.snplg` pairing physically installed and round-trip verified on the documented Pixel and Nomad.
- No shared LAN, cloud relay, or Supernote internet connection required.
- Android APK signed with the permanent LinkBridge release identity and verified with Android `apksigner`.
- SHA-256 checksums included for all three distributable release assets.

## Downloads

- `LinkBridgePhone-0.5.0.apk` — Android share target and BLE sender.
- `LinkBridgeSupernote-0.5.0.snplg` — Supernote plugin-preview package.
- `LinkBridge-Obsidian-Compatibility-3.1.2.zip` — temporary patched Obsidian plugin with backup and restore tools.
- `CHECKSUMS-SHA256.txt` — SHA-256 hashes for all three assets.

Read the [installation guide](https://github.com/adiuv0/LinkBridge/blob/main/docs/INSTALL.md) before installing. The Supernote plugin-preview firmware is required.

## Important beta limitation

LinkBridge 0.5.0 does not yet authenticate the nearby phone at the application layer. Do not use this beta for sensitive or credential-bearing URLs in a hostile radio environment. See the [privacy and security notes](https://github.com/adiuv0/LinkBridge/blob/main/docs/PRIVACY-AND-SECURITY.md) for the full threat model.
