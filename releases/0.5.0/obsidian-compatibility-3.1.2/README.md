# LinkBridge compatibility for the Supernote Obsidian plugin

This bundle is the official `philips/supernote-obsidian-plugin` 3.1.2 release source plus one focused compatibility change: native external URL links stored in a Supernote `.note` file are clickable in the rendered page.

It does not change Partner-app synchronization, your notes, or your Obsidian settings. The installer replaces only `main.js`, `manifest.json`, and `styles.css`. Before doing that, it backs up the existing `supernote` plugin directory inside the vault at `.obsidian/linkbridge-backups/`.

## Windows

1. Install and enable the normal **Supernote** community plugin in Obsidian.
2. Close Obsidian, or be prepared to restart it afterward.
3. Double-click **Install on Windows.cmd**.
4. Paste the path to the vault that contains your Supernote files.
5. Restart Obsidian.

To undo the compatibility change, double-click **Restore previous version on Windows.cmd** and choose the same vault.

## macOS or Linux

From a terminal in this extracted folder:

```sh
chmod +x install-linkbridge-obsidian.sh restore-previous-obsidian-plugin.sh
./install-linkbridge-obsidian.sh "/path/to/your/vault"
```

To restore the most recent backup:

```sh
./restore-previous-obsidian-plugin.sh "/path/to/your/vault"
```

## Updates

An official Supernote plugin update can replace this compatibility build. If external links stop responding after an update, rerun this installer. The long-term fix is being prepared as a small upstream contribution so this bundle can eventually become unnecessary.

Base upstream commit: `fb9743944ab08fea951ce96322f9de9d39523162` (`3.1.2`).

Verification: the compatibility build passes the upstream production build, all 222 Obsidian tests, and its lint configuration with zero errors.
