#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
vault_path=${1:-}
if [ -z "$vault_path" ]; then
    printf 'Paste the path to your Obsidian vault: '
    IFS= read -r vault_path
fi

obsidian_dir=$vault_path/.obsidian
plugin_dir=$obsidian_dir/plugins/supernote
plugin_source=$script_dir/plugin

if [ ! -d "$obsidian_dir" ]; then
    printf 'This does not look like an Obsidian vault: %s\n' "$vault_path" >&2
    exit 1
fi
if [ ! -d "$plugin_dir" ]; then
    printf 'Install and enable the official Supernote plugin first, then run this installer again.\n' >&2
    exit 1
fi
for name in main.js manifest.json styles.css; do
    if [ ! -f "$plugin_source/$name" ]; then
        printf 'The compatibility bundle is incomplete: %s is missing.\n' "$name" >&2
        exit 1
    fi
done

backup_root=$obsidian_dir/linkbridge-backups
backup_dir=$backup_root/supernote-$(date +%Y%m%d-%H%M%S)
mkdir -p "$backup_dir"
cp -R "$plugin_dir"/. "$backup_dir"/

for name in main.js manifest.json styles.css; do
    cp "$plugin_source/$name" "$plugin_dir/$name"
done

printf '\nLinkBridge compatibility installed.\n'
printf 'Previous plugin files backed up to: %s\n' "$backup_dir"
printf 'Restart Obsidian, or disable and re-enable the Supernote plugin.\n'
