#!/usr/bin/env sh
set -eu

vault_path=${1:-}
if [ -z "$vault_path" ]; then
    printf 'Paste the path to your Obsidian vault: '
    IFS= read -r vault_path
fi

obsidian_dir=$vault_path/.obsidian
plugin_dir=$obsidian_dir/plugins/supernote
backup_root=$obsidian_dir/linkbridge-backups

if [ ! -d "$plugin_dir" ] || [ ! -d "$backup_root" ]; then
    printf 'No LinkBridge plugin backup was found in this vault.\n' >&2
    exit 1
fi

latest=''
for candidate in "$backup_root"/supernote-*; do
    [ -d "$candidate" ] || continue
    if [ -z "$latest" ] || [ "$candidate" -nt "$latest" ]; then
        latest=$candidate
    fi
done
if [ -z "$latest" ]; then
    printf 'No LinkBridge plugin backup was found in this vault.\n' >&2
    exit 1
fi

cp -R "$latest"/. "$plugin_dir"/

printf '\nPrevious Supernote plugin restored.\n'
printf 'Restored from: %s\n' "$latest"
printf 'Restart Obsidian, or disable and re-enable the Supernote plugin.\n'
