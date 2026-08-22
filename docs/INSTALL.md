# Installing the LinkBridge public beta

LinkBridge has three parts: the Android phone app, the Supernote plugin, and external-link compatibility for the Supernote Obsidian plugin.

## Requirements

- An Android phone with Bluetooth Low Energy peripheral advertising support. Android 8 or later is required.
- A Supernote with the plugin-preview firmware and a visible **Settings → Apps → Plugins** page.
- The `philips/supernote-obsidian-plugin` community plugin if you want clickable links on the computer.

Supernote plugin support is still a preview feature as of August 2026. Stable and ordinary public-beta firmware may not include the Plugins page. The current [Supernote developer preview announcement](https://www.reddit.com/r/Supernote_dev/comments/1u842pk/plugin_preview_build_chauvet32942_beta_for_manta/) explains that distinction and provides the current firmware path. Do not change firmware casually if your notes depend on beta-only behavior.

Physical verification currently covers:

- Supernote Nomad on plugin-preview firmware (`Chauvet.E103.2605211001.2352_beta` build identifier).
- Pixel 11 Pro Fold on Android 17.
- Phone on cellular data while the Nomad has no Wi-Fi connection.

Manta, A5 X, A6 X, Samsung phones, and other Android devices may work but have not yet been physically verified. iPhone is not supported.

## 1. Phone

Install `LinkBridgePhone-<version>.apk` on the Android phone. Android may ask you to allow installation from the app that opened the APK. LinkBridge requests only Nearby Devices/Bluetooth access and deliberately has no internet permission.

## 2. Supernote

1. Copy `LinkBridgeSupernote-<version>.snplg` into the Supernote `MyStyle` folder using USB or the Supernote Partner app.
2. On the Supernote, open **Settings → Apps → Plugins → Add Plugin**.
3. Choose the LinkBridge package and install it.

Installing a newer LinkBridge package upgrades the existing plugin when its version code is higher.

## 3. Obsidian compatibility

Install and enable the normal [Supernote Obsidian plugin](https://github.com/philips/supernote-obsidian-plugin) first. Until external URL handling is accepted upstream, use the `LinkBridge-Obsidian-Compatibility-<version>.zip` release asset.

The compatibility installer backs up the existing plugin before replacing its three distributable files. It does not alter `.note` files, vault content, or the plugin's `data.json` settings. The included restore tool returns to the most recent backup.

## Everyday use

1. Share a web page from Android to **LinkBridge**.
2. Open the destination note and page on the Supernote.
3. Open **Plugins → LinkBridge**.
4. After the link is saved, move the already-selected title with the lasso if desired.

No shared Wi-Fi, server, account, or internet connection on the Supernote is involved.
