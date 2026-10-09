#!/usr/bin/env bash
#
# Copyright 2026 Dustin Rodrigues
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Refreshes the NiFi theme copied into the Advanced UI from a NiFi release, so the UI keeps
# matching NiFi's look. Run it after changing nifi.version in pom.xml.
#
# Usage: ./update-nifi-theme.sh [--source <nifi checkout>] [<nifi version>]
#
#   <nifi version>  NiFi release to copy from, e.g. 2.12.0. Defaults to nifi.version in pom.xml.
#   --source DIR    Copy from a local NiFi checkout instead of downloading the release tag.
#                   The checkout is used as-is, whatever is checked out.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
target="${repo_root}/nifi-route-on-first-match-ui/src/main/frontend/src/nifi-shared"
shared_path="nifi-frontend/src/main/frontend/libs/shared/src/assets"

source_dir=""
version=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --source)
            source_dir="${2:?--source needs a directory}"
            shift 2
            ;;
        -h|--help)
            sed -n '/^# Refreshes/,/^$/p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            version="$1"
            shift
            ;;
    esac
done

if [[ -z "${version}" ]]; then
    version="$(sed -n 's:.*<nifi.version>\(.*\)</nifi.version>.*:\1:p' "${repo_root}/pom.xml")"
    [[ -n "${version}" ]] || { echo "Could not read nifi.version from pom.xml" >&2; exit 1; }
fi

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

if [[ -z "${source_dir}" ]]; then
    tag="rel/nifi-${version}"
    echo "Downloading ${shared_path} from NiFi ${tag}"
    # Shallow, sparse clone that fetches only the shared assets
    if ! git -c advice.detachedHead=false clone --quiet --depth 1 --filter=blob:none --sparse --branch "${tag}" \
        https://github.com/apache/nifi.git "${work}/nifi" 2> "${work}/clone.log"; then
        cat "${work}/clone.log" >&2
        echo "Could not download ${tag}; check that NiFi ${version} is a released version" >&2
        exit 1
    fi
    git -C "${work}/nifi" sparse-checkout set --no-cone "/${shared_path}/"
    source_dir="${work}/nifi"
    source_label="NiFi ${version} (${tag})"
else
    # Record the commit rather than the local path, since SOURCE is committed
    source_label="local NiFi checkout at $(git -C "${source_dir}" describe --tags --always --dirty 2>/dev/null || echo "an unknown commit")"
    [[ -d "${source_dir}/${shared_path}" ]] || { echo "No ${shared_path} in ${source_dir}" >&2; exit 1; }
fi

assets="${source_dir}/${shared_path}"
staging="${work}/nifi-shared/assets"
mkdir -p "${staging}/fonts" "${staging}/icons"

# Only what the Advanced UI uses: the theme, the font it loads, and the favicon
cp -R "${assets}/themes" "${staging}/"
cp -R "${assets}/fonts/flowfont" "${staging}/fonts/"
cp "${assets}/icons/nifi-drop.svg" "${staging}/icons/"
rm -f "${staging}/themes/README.md"

# NiFi resolves these paths from the root of its frontend workspace; here they resolve from src/
material="${staging}/themes/material.scss"
sed -e "s#@use 'libs/shared/src/assets/#@use 'nifi-shared/assets/#" \
    -e "s#'libs/shared/src'#'src'#" \
    "${material}" > "${material}.tmp"
mv "${material}.tmp" "${material}"

if grep -rn "libs/" "${staging}/themes"; then
    echo "The NiFi theme references workspace paths this script does not know how to rewrite (above)." >&2
    echo "Update the path rewrites in $(basename "$0"), then run it again." >&2
    exit 1
fi

echo "${source_label}" > "${work}/nifi-shared/SOURCE"

rm -rf "${target}"
mv "${work}/nifi-shared" "${target}"

echo "Updated ${target#"${repo_root}"/} from ${source_label}"
git -C "${repo_root}" status --short -- "${target}" || true
