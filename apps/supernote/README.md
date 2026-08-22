# LinkBridge Supernote plugin

This React Native Supernote plugin receives one queued URL from the LinkBridge Android phone app over Bluetooth Low Energy, inserts it as a native external link in the open `.note` page, saves the note, acknowledges delivery, selects the inserted link, and closes.

## Build

Requirements:

- Node.js 18 or newer
- Android SDK with the platform configured by `android/build.gradle`
- 64-bit JDK 17 or newer; set `LINKBRIDGE_JAVA_HOME` if Android Studio's bundled runtime is unavailable
- Supernote plugin-preview firmware for installation and testing

From this directory:

```powershell
npm ci
npm run lint
npx tsc --noEmit
npm test -- --runInBand
npm run build:plugin
```

The SDK postinstall patch fixes native-library linkage for plugin packaging and removes a debug statement that would otherwise log link parameters. The build output is `build/outputs/LinkBridgeSupernote.snplg`.

The native APK embedded in a plugin build uses the developer machine's ordinary Android debug signing key; no private key is stored in this repository. The `.snplg` is installed and upgraded through Supernote's plugin manager using the version metadata in `PluginConfig.json`.

## Scope

This component does not request internet access. It needs Bluetooth and note-file access supplied by the Supernote plugin environment. See the repository's privacy and security document for the current unauthenticated-Bluetooth beta limitation.
