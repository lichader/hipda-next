#!/usr/bin/env bash
#
# Set up a local development environment for HiPDA Next on Linux or macOS.
#
# Installs into your home directory, without sudo:
#   - SDKMAN! (if missing) and the JDK named in .sdkmanrc
#   - the Android SDK command-line tools, platform tools and the compile SDK platform
#   - with --emulator: the Android emulator and two virtual devices, one for the
#     compile SDK and one for the oldest supported API
# then writes local.properties and builds the debug APK to check the setup.
#
# Safe to run again: steps that are already done are skipped.
# See docs/local-development-setup.md for the manual steps.

set -euo pipefail

# Android SDK command-line tools 23.0. To update, take the build number and archive
# names from https://dl.google.com/android/repository/repository2-3.xml (path
# "cmdline-tools;latest"), download each archive, check its SHA-1 against that file
# and record its SHA-256 here.
readonly CMDLINE_TOOLS_VERSION=23.0
readonly CMDLINE_TOOLS_BUILD=16111833
readonly CMDLINE_TOOLS_SHA256_LINUX=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8
readonly CMDLINE_TOOLS_SHA256_MAC_X86_64=7e601c04e7173754b3d051a87bb4c3a8243c65a9d7bfa9356ed6499fb8953fa3
readonly CMDLINE_TOOLS_SHA256_MAC_ARM64=58da8f215781cc0208743e88b65ae259780c55e7eb40e50373d377cf369232d6

readonly AVD_DEVICE=medium_phone
readonly AVD_PREFIX=hipda-api

usage() {
    cat <<'EOF'
Usage: scripts/bootstrap.sh [options]

Sets up JDK 21 (through SDKMAN!), the Android SDK and local.properties, then
builds the debug APK.

Options:
  --sdk-dir DIR   Android SDK location. Default: sdk.dir in local.properties,
                  then $ANDROID_HOME, then ~/Android/Sdk (Linux) or
                  ~/Library/Android/sdk (macOS).
  --emulator      Also install the emulator and create two virtual devices: one
                  for the compile SDK and one for the oldest API the app supports.
                  Takes about 10 GB, and each device grows by several GB
                  once it has been started.
  --skip-build    Do not build the debug APK at the end.
  -h, --help      Show this help.

Installing Android SDK packages accepts their licenses on your behalf. The
license texts are at https://developer.android.com/studio/terms.
EOF
}

step() { printf '\n==> %s\n' "$*"; }
info() { printf '    %s\n' "$*"; }
warn() { printf 'warning: %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# Options and platform

sdk_dir_option=""
with_emulator=0
skip_build=0
while (($# > 0)); do
    case "$1" in
        --sdk-dir)
            (($# >= 2)) || die "--sdk-dir needs a directory"
            sdk_dir_option="$2"
            shift 2
            ;;
        --sdk-dir=*) sdk_dir_option="${1#*=}"; shift ;;
        --emulator) with_emulator=1; shift ;;
        --skip-build) skip_build=1; shift ;;
        -h | --help) usage; exit 0 ;;
        *) usage >&2; die "unknown option: $1" ;;
    esac
done

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

os="$(uname -s)"
arch="$(uname -m)"
case "$os/$arch" in
    Linux/x86_64)
        cmdline_tools_zip="commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"
        cmdline_tools_sha256="$CMDLINE_TOOLS_SHA256_LINUX"
        default_sdk_dir="$HOME/Android/Sdk"
        abi=x86_64
        ;;
    Darwin/x86_64)
        cmdline_tools_zip="commandlinetools-mac_x86_64-${CMDLINE_TOOLS_BUILD}_latest.zip"
        cmdline_tools_sha256="$CMDLINE_TOOLS_SHA256_MAC_X86_64"
        default_sdk_dir="$HOME/Library/Android/sdk"
        abi=x86_64
        ;;
    Darwin/arm64)
        cmdline_tools_zip="commandlinetools-mac_arm64-${CMDLINE_TOOLS_BUILD}_latest.zip"
        cmdline_tools_sha256="$CMDLINE_TOOLS_SHA256_MAC_ARM64"
        default_sdk_dir="$HOME/Library/Android/sdk"
        abi=arm64-v8a
        ;;
    *)
        die "unsupported system $os/$arch. Supported: Linux x86_64 and macOS. On Windows, use WSL or follow docs/local-development-setup.md."
        ;;
esac

# Project settings, read from the build so the script follows SDK and JDK bumps.
gradle_property() { sed -n "s/^ *$1 *= *\([0-9][0-9]*\).*/\1/p" build.gradle | head -n 1; }
compile_sdk="$(gradle_property compileSdkVersion)"
min_sdk="$(gradle_property minSdkVersion)"
java_major="$(sed -n 's/^toolchainVersion=//p' gradle/gradle-daemon-jvm.properties)"
java_version="$(sed -n 's/^java=//p' .sdkmanrc)"
[[ -n "$compile_sdk" && -n "$min_sdk" ]] || die "could not read compileSdkVersion and minSdkVersion from build.gradle"
[[ -n "$java_major" ]] || die "could not read toolchainVersion from gradle/gradle-daemon-jvm.properties"
[[ -n "$java_version" ]] || die "could not read the java version from .sdkmanrc"

step "Checking required tools"
missing=()
for tool in curl unzip zip tar sed; do
    command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
done
if ! command -v sha256sum >/dev/null 2>&1 && ! command -v shasum >/dev/null 2>&1; then
    missing+=("sha256sum or shasum")
fi
((${#missing[@]} == 0)) || die "install these with your package manager, then run the script again: ${missing[*]}"
info "curl, unzip, zip, tar and sed found"

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

# Remember any adb on PATH before the SDK's platform tools are added.
existing_adb="$(command -v adb 2>/dev/null || true)"

# ---------------------------------------------------------------------------
# JDK through SDKMAN!

SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
export SDKMAN_DIR
installed_sdkman=0

# SDKMAN!'s functions are not written for errexit, nounset or pipefail.
sdk_cmd() {
    local status=0
    set +euo pipefail
    # shellcheck source=/dev/null
    source "$SDKMAN_DIR/bin/sdkman-init.sh"
    sdk "$@"
    status=$?
    set -euo pipefail
    return "$status"
}

step "Installing JDK $java_major ($java_version) with SDKMAN!"
if [[ ! -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]]; then
    info "SDKMAN! not found in $SDKMAN_DIR; installing it without editing your shell startup files"
    curl -fsSL "https://get.sdkman.io?rcupdate=false" | bash
    installed_sdkman=1
fi
# Installs the JDK named in .sdkmanrc if needed and keeps your default JDK.
sdk_cmd env install || die "SDKMAN! could not install java $java_version"

export JAVA_HOME="$SDKMAN_DIR/candidates/java/$java_version"
export PATH="$JAVA_HOME/bin:$PATH"
jdk_major="$(sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$JAVA_HOME/release" 2>/dev/null || true)"
[[ "$jdk_major" == "$java_major" ]] || die "$JAVA_HOME is not a JDK $java_major (found '${jdk_major:-nothing}'); fix .sdkmanrc"
info "JAVA_HOME=$JAVA_HOME"

# ---------------------------------------------------------------------------
# Android SDK

# Gradle reads sdk.dir from local.properties before ANDROID_HOME, so use the same order.
local_properties_sdk_dir=""
if [[ -f local.properties ]]; then
    local_properties_sdk_dir="$(sed -n 's/^sdk\.dir=//p' local.properties | tail -n 1 | sed 's/\\\(.\)/\1/g')"
fi
if [[ -n "$sdk_dir_option" ]]; then
    sdk_dir="$sdk_dir_option"
elif [[ -n "$local_properties_sdk_dir" ]]; then
    sdk_dir="$local_properties_sdk_dir"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
    sdk_dir="$ANDROID_HOME"
elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    sdk_dir="$ANDROID_SDK_ROOT"
else
    sdk_dir="$default_sdk_dir"
fi
mkdir -p "$sdk_dir"
sdk_dir="$(cd "$sdk_dir" && pwd)"

for var in ANDROID_HOME ANDROID_SDK_ROOT; do
    if [[ -n "${!var:-}" && "${!var%/}" != "$sdk_dir" ]]; then
        warn "$var is set to ${!var}, but this project uses $sdk_dir"
    fi
done
export ANDROID_HOME="$sdk_dir"
unset ANDROID_SDK_ROOT

# Command-line tools 23.0 and later include the `android` CLI, which replaces
# sdkmanager. An older cmdline-tools/latest (for example from Android Studio) is left
# alone, and the pinned version is installed next to it.
if [[ -x "$sdk_dir/cmdline-tools/latest/bin/android" || ! -e "$sdk_dir/cmdline-tools/latest" ]]; then
    cmdline_tools_dir="$sdk_dir/cmdline-tools/latest"
else
    cmdline_tools_dir="$sdk_dir/cmdline-tools/$CMDLINE_TOOLS_VERSION"
fi
android_bin="$cmdline_tools_dir/bin/android"
avdmanager="$cmdline_tools_dir/bin/avdmanager"

step "Installing Android SDK command-line tools in $sdk_dir"
if [[ -x "$android_bin" ]]; then
    info "already installed in $cmdline_tools_dir"
else
    [[ ! -e "$cmdline_tools_dir" ]] || die "$cmdline_tools_dir exists but has no bin/android; remove it and run the script again"
    download_dir="$(mktemp -d)"
    trap 'rm -rf "$download_dir"' EXIT
    curl -fL --progress-bar -o "$download_dir/$cmdline_tools_zip" \
        "https://dl.google.com/android/repository/$cmdline_tools_zip"
    actual_sha256="$(sha256 "$download_dir/$cmdline_tools_zip")"
    [[ "$actual_sha256" == "$cmdline_tools_sha256" ]] ||
        die "checksum mismatch for $cmdline_tools_zip: expected $cmdline_tools_sha256, got $actual_sha256"
    unzip -q "$download_dir/$cmdline_tools_zip" -d "$download_dir"
    mkdir -p "$sdk_dir/cmdline-tools"
    mv "$download_dir/cmdline-tools" "$cmdline_tools_dir"
    rm -rf "$download_dir"
    trap - EXIT
    info "installed in $cmdline_tools_dir"
fi

# The first run downloads the rest of the Android CLI into ~/.android. Installing a
# package accepts its license and writes it to $sdk_dir/licenses, which also lets
# Gradle install the build tools it needs.
android_cli() { "$android_bin" --sdk="$sdk_dir" "$@" </dev/null; }

# Package paths matching a pattern (`*` wildcards), installed or available.
sdk_list() { android_cli sdk list --all "$1" 2>/dev/null | awk '$1 ~ /\// { print $1 }' | sort -u || true; }

# The package for a whole API level: "android-36" up to API 36, "android-37.0" from
# API 37 on. Minor releases (37.1) and previews (37.2-beta1) are skipped.
api_package() {
    local prefix="$1" api="$2" suffix="${3:-}"
    sdk_list "${prefix}android-${api}*${suffix}" |
        grep -E "^${prefix}android-${api}(\.0)?${suffix}\$" | head -n 1 || true
}

step "Installing Android SDK packages"
platform_package="$(api_package platforms/ "$compile_sdk")"
[[ -n "$platform_package" ]] ||
    die "no SDK platform for API $compile_sdk found; check your network connection, or run: $android_bin sdk list --all 'platforms/*'"
android_cli sdk install "$platform_package" platform-tools
info "installed $platform_package and platform-tools"

# ---------------------------------------------------------------------------
# Emulator (optional)

avds_created=()
if ((with_emulator)); then
    step "Installing the emulator and system images"
    new_image="$(api_package system-images/ "$compile_sdk" "/google_apis/$abi")"
    [[ -n "$new_image" ]] || die "no google_apis $abi system image for API $compile_sdk"
    old_api="$(sdk_list "system-images/android-*/google_apis/$abi" |
        sed -n "s|^system-images/android-\([0-9][0-9]*\)/google_apis/$abi\$|\1|p" |
        awk -v min="$min_sdk" '$1 >= min' | sort -n | head -n 1 || true)"
    images=("$new_image")
    if [[ -n "$old_api" && "$old_api" != "$compile_sdk" ]]; then
        images+=("system-images/android-$old_api/google_apis/$abi")
    else
        warn "no google_apis $abi system image at or above API $min_sdk besides API $compile_sdk"
    fi
    android_cli sdk install emulator "${images[@]}"
    info "installed emulator and ${images[*]}"

    step "Creating virtual devices"
    # avdmanager puts AVDs under $XDG_CONFIG_HOME/.android when that is set, but the
    # emulator and the android CLI only look in ~/.android/avd, so use that for all.
    export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-${ANDROID_USER_HOME:-$HOME/.android}/avd}"
    mkdir -p "$ANDROID_AVD_HOME"
    existing_avds="$("$avdmanager" list avd -c 2>/dev/null || true)"
    for image in "${images[@]}"; do
        api="$(printf '%s\n' "$image" | sed 's|^system-images/android-\([0-9]*\).*|\1|')"
        name="$AVD_PREFIX$api"
        if grep -qx "$name" <<<"$existing_avds"; then
            info "$name already exists"
        else
            # avdmanager still names packages with semicolons.
            echo no | "$avdmanager" create avd --name "$name" --package "${image//\//;}" --device "$AVD_DEVICE" >/dev/null
            info "created $name ($image)"
        fi
        avds_created+=("$name")
    done
fi

# ---------------------------------------------------------------------------
# local.properties

step "Writing local.properties"
sdk_dir_line="sdk.dir=$sdk_dir"
if [[ ! -f local.properties ]]; then
    printf '%s\n' "$sdk_dir_line" >local.properties
    info "created with $sdk_dir_line"
elif [[ -z "$local_properties_sdk_dir" ]]; then
    printf '%s\n' "$sdk_dir_line" >>local.properties
    info "added $sdk_dir_line"
elif [[ "$local_properties_sdk_dir" != "$sdk_dir" ]]; then
    # Only reachable with --sdk-dir: the user asked for a different SDK.
    sed -i.bak "s|^sdk\.dir=.*|$sdk_dir_line|" local.properties && rm -f local.properties.bak
    info "changed sdk.dir from $local_properties_sdk_dir to $sdk_dir"
else
    info "already has $sdk_dir_line"
fi

# ---------------------------------------------------------------------------
# Build check

if ((skip_build)); then
    step "Skipping the debug build (--skip-build)"
else
    step "Building the debug APK"
    ./gradlew :hipda:assembleDebug
    info "APK: $(ls hipda/build/outputs/apk/debug/*.apk)"
fi

# ---------------------------------------------------------------------------
# Next steps

step "Done"
path_dirs="\$ANDROID_HOME/platform-tools"
if ((with_emulator)); then
    path_dirs="$path_dirs:\$ANDROID_HOME/emulator"
fi
path_dirs="$path_dirs:\$ANDROID_HOME/${cmdline_tools_dir#"$sdk_dir"/}/bin"
path_line="export PATH=\"$path_dirs:\$PATH\""
cat <<EOF
To use the SDK tools in new shells, add these lines to ~/.zshrc or ~/.bashrc:

    export ANDROID_HOME="$sdk_dir"
    $path_line
EOF
if ((installed_sdkman)); then
    cat <<EOF
    source "$SDKMAN_DIR/bin/sdkman-init.sh"
EOF
fi
cat <<EOF

Gradle finds JDK $java_major by itself. To use it on the command line too, run
'sdk env' in the repository, or set sdkman_auto_env=true in
$SDKMAN_DIR/etc/config to switch automatically when you cd into it.
EOF
if ((${#avds_created[@]} > 0)); then
    printf '\nStart an emulator with: emulator -avd %s\n' "${avds_created[0]}"
fi
sdk_adb="$sdk_dir/platform-tools/adb"
if [[ -n "$existing_adb" && "$existing_adb" != "$sdk_adb" ]]; then
    existing_adb_version="$("$existing_adb" version 2>/dev/null | sed -n 's/^Version //p')"
    sdk_adb_version="$("$sdk_adb" version 2>/dev/null | sed -n 's/^Version //p')"
    if [[ "$existing_adb_version" != "$sdk_adb_version" ]]; then
        cat <<EOF

Note: another adb is on your PATH at $existing_adb (version
${existing_adb_version:-unknown}), but the SDK's is $sdk_adb_version. Two
different adb versions restart each other's server, so keep only one of them
first on your PATH.
EOF
    fi
fi
