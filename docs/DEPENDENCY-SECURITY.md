# Dependency security notes

Last reviewed: 2026-10-09.

The Supernote plugin must currently remain on the React Native generation supported by Supernote's preview plugin SDK. The repository nevertheless pins compatible patched build tooling where possible:

- React Native Community CLI 18.0.1 fixes the Metro development-server command-injection issue affecting 18.0.0.
- `fast-xml-parser` is overridden to 5.10.1 to remove the currently reported XML parser/builder advisories in the CLI's Android configuration path.

`npm audit fix` (without `--force`) was applied on 2026-10-09, which cleared the `shell-quote`, `brace-expansion`, `compression`, `joi`, `js-yaml` and `qs` advisories that had been published since the previous review. `npm audit --omit=dev` still reports:

- `image-size` denial-of-service advisories through Metro. Fixed releases exist only in the 2.x line, while Metro 0.82 requires `image-size@^1`. LinkBridge reaches it only during local Metro asset bundling of repository-controlled assets; it is not part of the installed plugin runtime.
- `braces` and `sprintf-js` advisories in Jest and React Native build tooling. Their fixes require a newer React Native major, which the Supernote plugin SDK does not support yet.

Do not run the Metro development server on an untrusted network, and re-check before each public build.

This is a narrow, dated exception rather than a blanket acceptance of audit failures. A stable release should move to a Supernote-supported SDK stack in which the remaining advisories are resolved.
