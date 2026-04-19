#!/usr/bin/env bash
set -e

cd "$(dirname "$0")"

if [ ! -f ./local.properties ]; then
    sdk_path=""
    for candidate in "$ANDROID_HOME" "$ANDROID_SDK_ROOT" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [ -n "$candidate" ] && [ -d "$candidate" ]; then
            sdk_path="$candidate"
            break
        fi
    done
    if [ -z "$sdk_path" ]; then
        echo "Fehler: Android SDK nicht gefunden. ANDROID_HOME setzen oder SDK installieren." >&2
        exit 1
    fi
    echo "Kein local.properties gefunden – schreibe sdk.dir=$sdk_path"
    printf 'sdk.dir=%s\n' "$sdk_path" > ./local.properties
fi

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$(pwd)/.gradle-bootstrap}"

if [ ! -x ./gradlew ]; then
    if ! command -v gradle >/dev/null 2>&1; then
        echo "Fehler: weder ./gradlew noch 'gradle' im PATH gefunden." >&2
        echo "Bitte Gradle installieren (z. B. 'brew install gradle') und erneut ausfuehren." >&2
        exit 1
    fi
    echo "Kein gradlew gefunden – erzeuge Wrapper mit System-Gradle ..."
    gradle wrapper --gradle-version 8.7 --distribution-type bin
fi

./gradlew assembleRelease

TARGET_IP="192.168.11.41"
APPLICATION_ID="de.henosch.koteltv"
APK_PATH="app/build/outputs/apk/release/app-release.apk"

find_device() {
    local dev
    local mdns_ep

    find_connected_device() {
        adb devices | awk -v ip="$TARGET_IP" '
            $2=="device" && index($1, ip ":")==1 { print $1; exit }
        '
    }

    dev=$(find_connected_device)
    if [ -n "$dev" ]; then
        printf '%s' "$dev"
        return 0
    fi

    while IFS= read -r mdns_ep; do
        [ -n "$mdns_ep" ] || continue
        adb connect "$mdns_ep" >/dev/null 2>&1 || true
        dev=$(find_connected_device)
        if [ -n "$dev" ]; then
            printf '%s' "$dev"
            return 0
        fi
    done < <(
        adb mdns services 2>/dev/null | awk -v ip="$TARGET_IP" '
            index($NF, ip ":")==1 { print $NF }
        '
    )

    adb connect "$TARGET_IP:5555" >/dev/null 2>&1 || true
    dev=$(find_connected_device)
    if [ -n "$dev" ]; then
        printf '%s' "$dev"
        return 0
    fi

    return 1
}

DEVICE=$(find_device) || {
    echo "Fehler: Kein adb-Gerät mit IP $TARGET_IP gefunden (weder verbunden, per mDNS noch auf :5555)." >&2
    exit 1
}
echo "Nutze Gerät: $DEVICE"

adb -s "$DEVICE" uninstall "$APPLICATION_ID" || true
adb -s "$DEVICE" shell am force-stop "$APPLICATION_ID"
adb -s "$DEVICE" install -r "$APK_PATH" 2>&1
adb -s "$DEVICE" shell am start -n "$APPLICATION_ID/.MainActivity" 2>&1
