# LinkBridge 0.5.0 public-beta candidate

This candidate contains the physically tested Bluetooth-only workflow and a permanently signed Android phone APK.

## Ready

- Supernote plugin package rebuilt without internet permission or sensitive SDK parameter logging.
- Version-pinned Obsidian 3.1.2 compatibility bundle with automatic backup and restore tools.
- Exact external URL targets preserved through the `.note` parser and clickable in Obsidian.
- Phone outbox, retry, save-confirmed acknowledgement, and duplicate prevention physically verified.
- Exact release APK and `.snplg` pairing physically installed and round-trip verified on the documented Pixel and Nomad.
- No shared LAN, cloud relay, or Supernote internet connection required.
- Android APK signed with the permanent LinkBridge release identity and verified with Android `apksigner`.
- SHA-256 checksums included for all three distributable release assets.

## Before posting

- Store the signing recovery credentials in a separate password manager or encrypted backup.
- Create the public repository and enable GitHub private vulnerability reporting.
- Review the final Reddit copy and release assets before making them public.

The Supernote plugin-preview firmware requirement and unauthenticated-Bluetooth beta limitation must be included in any public post.
