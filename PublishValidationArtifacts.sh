#!/usr/bin/env bash
set -euo pipefail

source_commit_message=${1:-"Validate current branch"}
artifact_commit_message=${2:-"Record validation artifacts"}
repo_root=$(dirname "$(realpath "${BASH_SOURCE[0]}")")
patch_sequence="$repo_root/PatchSequence.sh"
test_log="$repo_root/latest test results.log"
snapshot_manifest="$repo_root/latest snapshot manifest.json"

if [[ ! -f $patch_sequence ]]; then
    echo "PatchSequence.sh not found at: $patch_sequence" >&2
    exit 2
fi

cd "$repo_root"
"$patch_sequence" "" "$source_commit_message"

for artifact in "$test_log" "$snapshot_manifest"; do
    if [[ ! -f $artifact ]]; then
        echo "PatchSequence.sh did not produce required validation artifact: $artifact" >&2
        exit 2
    fi
done

branch=$(git branch --show-current)
if [[ -z $branch ]]; then
    echo "Validation artifacts can only be published from a named branch." >&2
    exit 2
fi

git add --force -- "$test_log" "$snapshot_manifest"
git commit -m "$artifact_commit_message"
git push
