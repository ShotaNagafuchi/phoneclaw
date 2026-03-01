#!/usr/bin/env bash
# Stream logs from the PhoneClaw app when device is connected via USB.
# Usage: ./logcat-app.sh
# Requires: adb in PATH, device connected.

set -e
# App package and tags we care about
PACKAGE="com.example.universal"
TAGS="MainActivity BuddyService UnlockReceiver RuleEngine BootReceiver BuddyNotificationManager"

# Show only these tags (V=verbose); *:S silences the rest
exec adb logcat -v time MainActivity:V BuddyService:V UnlockReceiver:V RuleEngine:V BootReceiver:V BuddyNotificationManager:V "*:S"
