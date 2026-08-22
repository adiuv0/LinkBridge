# Public beta release checklist

## Required before posting

- [x] Select `adiuv0/LinkBridge` as the repository target and the MIT License.
- [x] Generate one long-lived Android release-signing keystore outside the repository.
- [ ] Store its plaintext recovery credentials in a separate password manager or encrypted backup.
- [x] Build the phone APK with `apps/phone/Build-SignedRelease.ps1`; never distribute the debug APK.
- [x] Build the `.snplg` from a clean dependency install and confirm sensitive SDK parameter logging is absent from the bundle.
- [x] Re-run the dated dependency audit in `docs/DEPENDENCY-SECURITY.md`; the remaining no-fix `image-size` build-time exception is documented there.
- [x] Verify the signed phone APK and exact public `.snplg` artifact on the physically tested phone and Nomad, including a save-confirmed BLE round trip.
- [x] Verify one Partner-app-synchronized `.note` fixture in the packaged Obsidian compatibility build.
- [x] Generate SHA-256 checksums for every release asset.
- [x] State clearly that Supernote plugin-preview firmware is currently required.
- [x] State the physically verified device/firmware matrix and label every other device unverified.
- [x] Include the Obsidian backup/restore compatibility bundle until the upstream changes ship.

## Upstream work

- [x] Rebase external-target parsing onto the current `supernote-typescript` source.
- [x] Add a real Nomad external-URL fixture and exact-target regression test.
- [x] Rebase external anchor handling onto the current Supernote Obsidian viewer.
- [x] Pass current upstream builds, lint, and test suites in fresh checkouts.
- [x] Prepare final apply-checked diffs and proposed PR text for project-owner review.
- [ ] Open the parser PR first.
- [ ] After parser acceptance, update the Obsidian submodule and open the viewer PR.

## Required before a stable 1.0

- [ ] Authenticated pairing and encrypted Bluetooth sessions.
- [ ] Chunked transport for URLs longer than the BLE attribute limit.
- [ ] Physical verification on additional Supernote and Android models.
- [ ] A documented support and upgrade policy.
