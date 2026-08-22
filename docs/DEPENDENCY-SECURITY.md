# Dependency security notes

Last reviewed: 2026-08-22.

The Supernote plugin must currently remain on the React Native generation supported by Supernote's preview plugin SDK. The repository nevertheless pins compatible patched build tooling where possible:

- React Native Community CLI 18.0.1 fixes the Metro development-server command-injection issue affecting 18.0.0.
- `fast-xml-parser` is overridden to 5.10.1 to remove the currently reported XML parser/builder advisories in the CLI's Android configuration path.

`npm audit --omit=dev` still reports two high-severity `image-size` denial-of-service advisories through Metro. As of this review there is no published patched `image-size` release. LinkBridge reaches it only during local Metro asset bundling, where inputs are repository-controlled app assets; it is not included as an active URL/image parser in the installed LinkBridge plugin runtime. Do not run the Metro development server on an untrusted network, and re-check for a patched release before each public build.

This is a narrow, dated exception rather than a blanket acceptance of audit failures. A stable release should move to a Supernote-supported SDK stack in which the remaining advisories are resolved.
