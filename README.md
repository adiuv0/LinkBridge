# LinkBridge

[![GitHub release](https://img.shields.io/github/v/release/adiuv0/LinkBridge?include_prereleases&label=release)](https://github.com/adiuv0/LinkBridge/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

LinkBridge sends a web URL directly from an Android phone to the open page of a Supernote note over Bluetooth Low Energy. The phone may use cellular data and the Supernote does not need Wi-Fi or an internet connection.

The received URL is saved as a native external link inside the `.note` file. When the Supernote Partner app later transfers that file to the computer, the patched Supernote Obsidian plugin renders the URL as a clickable web link.

## Current status

This repository contains a physically tested public beta for the Supernote Nomad and Android:

- Android share target and one-shot BLE URL sender
- durable phone outbox that survives app and Bluetooth interruptions
- Supernote plugin that receives automatically when opened
- native `.note` URL insertion on the current page
- compact article-title display with the exact URL retained as the destination
- content-aware placement in the nearest clear space on the page
- automatic lasso selection so the new link can be repositioned immediately
- save-confirmed delivery acknowledgements and receipt-based duplicate prevention
- exact URL preservation, including case, escaping, query strings, and fragments
- external-link support for `philips/supernote-obsidian-plugin`

The phone-to-Nomad transfer has been tested without a shared network. A physical round trip completed in about 2.8 seconds during development.

## Everyday workflow

1. On the phone, use the browser's **Share** action and choose **LinkBridge**.
2. LinkBridge saves the item to its phone outbox and waits for the Supernote.
3. Open the destination note and page on the Supernote.
4. Open **Plugins → LinkBridge**. Receiving starts automatically.
5. LinkBridge inserts and saves the link, records a delivery receipt, confirms success to the phone, selects the link, and closes automatically. Move the selected link with the lasso tool if desired.
6. Continue using the Supernote Partner app and the Obsidian Supernote plugin normally. Repositioning preserves the native external link and its Obsidian click target.

The phone removes an item from its outbox only after the Supernote confirms that the note was saved. If a transfer times out or either device disconnects, the link remains queued and can be retried. A retry of an already saved item is acknowledged without inserting a duplicate.

## Installation

Download the three files attached to the [0.5.0 public-beta release](https://github.com/adiuv0/LinkBridge/releases/tag/v0.5.0), then follow [docs/INSTALL.md](docs/INSTALL.md) for the phone, Supernote, and temporary Obsidian compatibility steps.

Do not distribute an Android debug APK. Public phone builds must be created with `apps/phone/Build-SignedRelease.ps1` and a long-lived release-signing key. The compatibility installer backs up the user's existing Obsidian plugin and includes a restore tool.

## Public-beta limitations

- URLs are limited to 500 UTF-8 bytes.
- The BLE service is intentionally short-lived but does not yet authenticate a paired phone.
- The phone outbox keeps up to 100 items, but the current Supernote action receives one item at a time.
- LinkBridge refuses to insert only when it cannot find a clear area large enough for the actual rendered link.
- Partner-app synchronization was not modified; LinkBridge relies on the user's existing sync workflow.
- The Obsidian changes are installed locally and are not yet an upstream release of the community plugin.

Before a stable 1.0, add application-level pairing/authentication, encrypted sessions, and chunking for long URLs. See [docs/PRIVACY-AND-SECURITY.md](docs/PRIVACY-AND-SECURITY.md) for the current threat model.

## Verification

The repository includes a real Nomad fixture under `fixtures/supernote`. Parser and overlay regression tests cover exact external URL decoding and click behavior; the documented release pairing was also installed and round-trip tested on physical hardware.

To rebuild the Supernote package from `apps/supernote`:

```powershell
npm run lint
npx tsc --noEmit
npm run build:plugin
```

Initialize the permanent signing identity once (it is stored outside this repository), then build a signed phone release. The build script loads the Windows-protected signing configuration automatically:

```powershell
.\apps\phone\Initialize-ReleaseSigning.ps1
.\apps\phone\Build-SignedRelease.ps1
```

Back up the recovery credentials created by the initialization script in a separate password manager or encrypted store. Never commit the keystore or recovery files.

## Contributing and security

Bug reports and focused improvements are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Please use test URLs and remove private note names, device addresses, and account information from diagnostics.

Do not open a public issue for a suspected security vulnerability. Follow [SECURITY.md](SECURITY.md) to report it privately through GitHub.

## License

LinkBridge is available under the [MIT License](LICENSE).
