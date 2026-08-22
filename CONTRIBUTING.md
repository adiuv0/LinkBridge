# Contributing to LinkBridge

Thanks for helping improve LinkBridge. Focused bug fixes, device-compatibility reports, tests, documentation improvements, and small workflow refinements are welcome.

## Before filing an issue

- Check the current [public-beta limitations](README.md#public-beta-limitations).
- Confirm whether the problem is on the phone, Supernote, or Obsidian side.
- Use a harmless test URL and remove private note names, device addresses, and account information from diagnostics.
- Report security problems privately according to [SECURITY.md](SECURITY.md).

## Development checks

For the Android phone app, run the release unit suite through the bundled Gradle wrapper:

```powershell
.\apps\supernote\android\gradlew.bat -p .\apps\phone :app:testReleaseUnitTest
```

For the Supernote plugin:

```powershell
Set-Location .\apps\supernote
npm ci
npm test -- --runInBand
npm run lint
npx tsc --noEmit
npm run build:plugin
```

Do not commit generated packages, signing material, recovery credentials, device captures, or private `.note` files. Public Android builds must use a long-lived release identity; debug APKs must never be distributed as releases.

Changes intended for the upstream Supernote parser or Obsidian plugin should remain generic to native external links. LinkBridge-specific behavior belongs in this repository.

By submitting a contribution, you agree that it may be distributed under the repository's MIT License.
