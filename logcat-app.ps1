# Stream logs from the PhoneClaw app when device is connected via USB.
# Usage: .\logcat-app.ps1
# Requires: adb in PATH, device connected.

$args = @("-v", "time",
    "MainActivity:V", "BuddyService:V", "UnlockReceiver:V", "RuleEngine:V",
    "BootReceiver:V", "BuddyNotificationManager:V", "*:S")
& adb logcat @args
