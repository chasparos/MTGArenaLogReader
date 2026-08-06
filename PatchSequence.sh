#!/usr/bin/env bash
set -euo pipefail

patch_file=${1:-}
commit_message=${2:-}

if [[ -z $commit_message ]]; then
    echo "Usage: $0 [patch-file] <commit-message>" >&2
    exit 2
fi

script_path=$(realpath "${BASH_SOURCE[0]}")
repo_root=$(dirname "$script_path")
script_hash_before_patch=$(sha256sum "$script_path" | awk '{print $1}')
archive="$repo_root/latest snapshot.zip"
test_log="$repo_root/latest test results.log"
snapshot_manifest="$repo_root/latest snapshot manifest.json"
maven_wrapper="$repo_root/mvnw"

git_text() {
    git "$@"
}

file_sha256() {
    sha256sum "$1" | awk '{print $1}'
}

json_escape() {
    local value=$1
    value=${value//\\/\\\\}
    value=${value//\"/\\\"}
    value=${value//$'\n'/\\n}
    value=${value//$'\r'/\\r}
    value=${value//$'\t'/\\t}
    printf '%s' "$value"
}

json_string_array() {
    local item
    local first=true
    printf '['
    while IFS= read -r item; do
        if [[ $first == false ]]; then
            printf ','
        fi
        first=false
        printf '"%s"' "$(json_escape "$item")"
    done
    printf ']'
}

get_test_summary() {
    local summary
    summary=$(grep -E 'Tests run:[[:space:]]*[0-9]+,[[:space:]]*Failures:[[:space:]]*[0-9]+,[[:space:]]*Errors:[[:space:]]*[0-9]+,[[:space:]]*Skipped:[[:space:]]*[0-9]+' "$test_log" | tail -n 1 || true)
    if [[ -n $summary ]]; then
        printf '%s' "$summary" | sed -E 's/.*Tests run:[[:space:]]*([0-9]+),[[:space:]]*Failures:[[:space:]]*([0-9]+),[[:space:]]*Errors:[[:space:]]*([0-9]+),[[:space:]]*Skipped:[[:space:]]*([0-9]+).*/"testsRun": \1, "failures": \2, "errors": \3, "skipped": \4/'
    else
        printf '"testsRun": null, "failures": null, "errors": null, "skipped": null'
    fi
}

get_java_version() {
    java -version 2>&1 | head -n 1 || true
}

write_snapshot_manifest() {
    local test_exit=$1
    local outcome=unknown
    local branch

    if grep -q 'BUILD SUCCESS' "$test_log"; then
        outcome=passed
    elif grep -q 'BUILD FAILURE' "$test_log"; then
        outcome=failed
    fi

    branch=$(git_text branch --show-current)
    if [[ -z $branch ]]; then
        branch='(detached HEAD)'
    fi

    {
        printf '{\n'
        printf '  "schemaVersion": 1,\n'
        printf '  "generatedAtUtc": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "repository": {\n'
        printf '    "commit": "%s",\n' "$(git_text rev-parse HEAD)"
        printf '    "branch": "%s",\n' "$(json_escape "$branch")"
        printf '    "workingTreeStatus": '
        git status --short --untracked-files=all | json_string_array
        printf '\n  },\n'
        printf '  "snapshot": {\n'
        printf '    "file": "%s",\n' "$(basename "$archive")"
        printf '    "bytes": %s,\n' "$(stat -c %s "$archive")"
        printf '    "sha256": "%s",\n' "$(file_sha256 "$archive")"
        printf '    "production": "git archive --format=zip HEAD",\n'
        printf '    "ignoredFilePolicy": "Contains committed tracked files from HEAD only; excludes .git metadata, ignored files, and untracked files."\n'
        printf '  },\n'
        printf '  "validation": {\n'
        printf '    "buildCommand": "./mvnw test",\n'
        printf '    "testExitCode": %s,\n' "$test_exit"
        printf '    "testLog": "%s",\n' "$(basename "$test_log")"
        printf '    "testLogSha256": "%s",\n' "$(file_sha256 "$test_log")"
        printf '    "summary": { "outcome": "%s", %s }\n' "$outcome" "$(get_test_summary)"
        printf '  },\n'
        printf '  "runtime": {\n'
        printf '    "operatingSystem": "%s",\n' "$(json_escape "$(uname -srvmo)")"
        printf '    "bash": "%s",\n' "$(json_escape "$BASH_VERSION")"
        printf '    "git": "%s",\n' "$(json_escape "$(git_text --version)")"
        printf '    "java": "%s"\n' "$(json_escape "$(get_java_version)")"
        printf '  },\n'
        printf '  "largeTrackedFiles": [\n'
        git ls-tree -r -l HEAD |
            awk -F '\t' '{ split($1, fields, " "); print fields[4] "\t" $2 }' |
            sort -nr -k1,1 |
            head -n 20 |
            awk -F '\t' 'BEGIN { first = 1 } { if (!first) printf ",\n"; first = 0; printf "    { \"path\": \"%s\", \"bytes\": %s }", $2, $1 }'
        printf '\n  ]\n'
        printf '}\n'
    } > "$snapshot_manifest"
}

if [[ ! -f $maven_wrapper ]]; then
    echo "Maven Wrapper not found at: $maven_wrapper" >&2
    exit 2
fi

cd "$repo_root"

if [[ -z $patch_file ]]; then
    echo "No patch file given. Skipping patch application."
else
    requested_patch=$patch_file
    if [[ $requested_patch != /* && -f "$HOME/Downloads/$requested_patch" ]]; then
        requested_patch="$HOME/Downloads/$requested_patch"
    fi
    source_patch=$(realpath "$requested_patch")
    root_patch="$repo_root/$(basename "$source_patch")"
    if [[ $source_patch != "$root_patch" ]]; then
        echo "Copying patch to project root: $root_patch"
        cp -f "$source_patch" "$root_patch"
    fi
    echo "Checking patch: $root_patch"
    git apply --check --ignore-whitespace -- "$root_patch"
    echo "Applying patch: $root_patch"
    git apply --ignore-whitespace -- "$root_patch"
    mkdir -p "$repo_root/applied patches"
    echo "Archiving applied patch: $repo_root/applied patches/$(basename "$root_patch")"
    mv -f "$root_patch" "$repo_root/applied patches/"

    script_hash_after_patch=$(sha256sum "$script_path" | awk '{print $1}')
    if [[ $script_hash_after_patch != "$script_hash_before_patch" ]]; then
        echo "PatchSequence.sh changed. Restarting with the updated script before validation..."
        exec "$script_path" "" "$commit_message"
    fi
fi

echo "Running tests..."
set +e
"$maven_wrapper" test 2>&1 | tee "$test_log"
test_exit=${PIPESTATUS[0]}
set -e

if [[ $test_exit -ne 0 ]]; then
    echo "Tests failed. Leaving applied changes uncommitted and unstaged."
    rm -f "$archive" "$snapshot_manifest"
    echo "Creating diagnostic archive from unchanged HEAD: $archive"
    git archive --format=zip --output="$archive" HEAD
    echo "Creating failed-validation snapshot manifest: $snapshot_manifest"
    write_snapshot_manifest "$test_exit"
    exit "$test_exit"
fi

echo "Staging repository state..."
git add -A
git update-index --chmod=+x -- mvnw
if ! git diff --cached --quiet; then
    echo "Committing: $commit_message"
    git commit -m "$commit_message"
else
    echo "No staged changes. Skipping commit."
fi

rm -f "$archive" "$snapshot_manifest"
echo "Creating archive: $archive"
git archive --format=zip --output="$archive" HEAD
echo "Creating snapshot manifest: $snapshot_manifest"
write_snapshot_manifest "$test_exit"

echo "Test exit code: $test_exit"
echo "Test log: $test_log"
echo "Snapshot: $archive"
echo "Snapshot manifest: $snapshot_manifest"
