# Prepared upstream contributions

The proposed changes are intentionally generic: they teach the parser and viewer about native Supernote external URL links rather than adding LinkBridge-specific behavior.

Validated against fresh upstream checkouts on 2026-08-22:

- `philips/supernote-typescript` 0.7.1: lint, 192 tests, library build, and fixture-site build passed.
- `philips/supernote-obsidian-plugin` 3.1.2: lint passed with no errors, 222 tests passed, and the production build passed.

No branch, commit, push, issue, or pull request has been created. The parser change should be proposed first because the viewer change depends on its `target` and `isExternal` fields.

## Parser PR draft

Title: `Preserve native external URL link targets`

Body:

> Supernote `.note` files can store native external URL links in `LINKFILE`, not only internal note paths. The existing parser normalizes every decoded value as a device path, which can lose the external destination semantics and append an internal page anchor.
>
> This change exposes the exact decoded value as `ILink.target`, marks HTTP(S) targets with `ILink.isExternal`, and keeps `text` backward-compatible for internal note links. External URLs retain their exact case, escaping, query string, and fragment and do not receive `#Page N` normalization.
>
> A real Nomad fixture and regression test cover a mixed-case URL with percent escaping, query parameters, and a fragment. The fixture catalog/site is updated as well.

Suggested files: `upstream-contributions/supernote-typescript.patch`.

## Obsidian viewer PR draft

Title: `Render native external Supernote links as web anchors`

Body:

> This builds on the parser's external-target metadata. Link overlays for native external links now use the exact URL as their anchor destination and set `_blank` plus `noopener noreferrer`. Internal note links keep the existing Obsidian/web-component click handling.
>
> Unit coverage verifies exact URL preservation, the real Nomad fixture, and that the web component leaves external anchors to native browser navigation without dispatching the internal-link event.

Suggested files: `upstream-contributions/supernote-obsidian-plugin.patch`.
