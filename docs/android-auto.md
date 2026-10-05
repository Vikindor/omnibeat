# Testing Android Auto

This guide covers two ways to test OmniBeat's Android Auto integration on a Windows PC:

* Google's Desktop Head Unit (DHU), which runs as a desktop application.
* Open Headunit, which runs on a second Android emulator.

Commands use PowerShell and run on the PC.

## How the test setup works

Install OmniBeat and Android Auto on the phone. The phone runs the app, manages playback, and supplies the Android Auto interface. The head unit displays that interface and sends user input back to the phone.

You can use a physical phone or a phone emulator. Android Automotive OS is a separate operating system for vehicles; its emulator is not needed for either setup below.

## Prepare the phone

Install OmniBeat and a full version of Android Auto. For an emulator, start with a Google Play system image. If Android Auto cannot be installed from the Play Store, you will need a compatible package for the emulator's architecture.

Some emulator images include an Android Auto package whose version ends in `-stub`. That package is a placeholder, not a usable Android Auto installation. A device without Google Play Services may also fail to complete the connection even if the Android Auto APK is installed.

On the phone:

1. Open **Android Auto settings**, not the system **App info** page.
2. Tap the Android Auto version information 10 times and confirm that you want to enable developer mode.
3. Open **Developer settings** from the three-dot menu and enable **Unknown sources** so Android Auto can discover a sideloaded OmniBeat build.
4. Choose **Start headunit server** from the Android Auto menu. Check that the server notification appears.
5. Keep the phone unlocked during the first connection and accept any setup or permission prompts. Enable **Add new cars to Android Auto** if that option is available.

These settings belong to Android Auto on the phone. See Google's [DHU setup guide](https://developer.android.com/training/cars/testing/dhu) for the connection procedure.

List the connected devices before running any device-specific commands:

```powershell
adb devices
```

The examples assume `adb` is on your `PATH`. Otherwise, run it from the SDK's Platform Tools directory:

```powershell
Set-Location "$env:LOCALAPPDATA\Android\Sdk\platform-tools"
.\adb.exe devices
```

Use `.\adb.exe` instead of `adb` in the remaining commands if you take this approach. PowerShell requires the `.\` prefix to run an executable from the current directory.

## Option 1: Desktop Head Unit

### Install DHU

In Android Studio, open **SDK Manager → SDK Tools** and install **Android Auto Desktop Head Unit Emulator**.

With the default Windows SDK location, DHU is installed under:

```text
%LOCALAPPDATA%\Android\Sdk\extras\google\auto
```

### Connect

Start the head unit server on the phone, then forward a port from the PC to that phone. This example uses `emulator-5556` as the phone:

```powershell
adb -s emulator-5556 forward tcp:5277 tcp:5277
```

For a physical phone, replace `emulator-5556` with its serial number from `adb devices`. Forward port `5277` to only one phone at a time.

ADB may print `5277` after creating the rule. To inspect the forwarding rules:

```powershell
adb forward --list
```

You should see an entry such as:

```text
emulator-5556 tcp:5277 tcp:5277
```

The entry confirms that the tunnel exists; it does not confirm that the Android Auto server is running.

Start DHU:

```powershell
Set-Location "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto"
.\desktop-head-unit.exe --adb=5277
```

The sequence is **start the server on the phone → forward the port → launch DHU**. Once the car interface loads, open OmniBeat from the app launcher.

### Disconnect

Close DHU, stop the head unit server from Android Auto's menu, and remove the forwarding rule:

```powershell
adb -s emulator-5556 forward --remove tcp:5277
```

Disconnect DHU before using Open Headunit with the same phone.

## Option 2: Open Headunit with two emulators

This example uses the following devices:

| Device | Role | Installed apps |
|---|---|---|
| `emulator-5554` | Head unit | Open Headunit |
| `emulator-5556` | Phone | Android Auto and OmniBeat |

Emulator serial numbers can change when you restart devices. Check `adb devices` and adjust the commands accordingly.

### Prepare Open Headunit

Install [Open Headunit](https://github.com/andreknieriem/open-headunit/releases) on `emulator-5554` and complete its initial setup. Use its **Headunit Server** connection option for this test. Native Mode and Wireless Helper use different connection paths.

On `emulator-5556`, choose **Start headunit server** in Android Auto.

The project's [connection instructions](https://github.com/andreknieriem/open-headunit#how-to-use) describe Headunit Server and the `headunit://connect` intent used below.

### Create the tunnel and connect

Choose one of the following methods. Both use `forward` on the phone to make its Android Auto server available through port `5277` on the PC.

#### Method 1: Connect to the PC through `10.0.2.2`

For standard Android Studio emulators, use this method first. `10.0.2.2` is the emulator's built-in alias for the PC's loopback interface, so no `adb reverse` rule is needed.

Run these commands on the PC, in order:

```powershell
adb -s emulator-5556 forward tcp:5277 tcp:5277
adb -s emulator-5554 shell am start -a android.intent.action.VIEW -d "headunit://connect?ip=10.0.2.2"
```

The connection follows this path:

```text
Open Headunit on 5554
    → 10.0.2.2:5277
    → 127.0.0.1:5277 on the PC
    → adb forward
    → Android Auto server on 5556
```

`10.0.2.2` is a fixed emulator address, not the PC's Wi-Fi or Ethernet IP address. You do not need to discover it with `ipconfig`. It is documented in Android's [emulator network address table](https://developer.android.com/studio/run/emulator-networking-address) and has no special meaning on a physical Android device.

#### Method 2: Connect through `127.0.0.1` with `adb reverse`

`127.0.0.1` always refers to the device making the connection. On the head unit emulator, it refers to that emulator, not the PC. An additional `reverse` rule makes its local port reach the PC.

Run these commands on the PC, in order:

```powershell
adb -s emulator-5556 forward tcp:5277 tcp:5277
adb -s emulator-5554 reverse tcp:5277 tcp:5277
adb -s emulator-5554 shell am start -a android.intent.action.VIEW -d "headunit://connect?ip=127.0.0.1"
```

Each command serves a different device:

1. `forward` on the **phone, 5556**, routes connections from port `5277` on the PC to the phone's Android Auto server.
2. `reverse` on the **head unit, 5554**, routes connections from its local port `5277` back to port `5277` on the PC.
3. The intent runs on the **head unit, 5554**, telling Open Headunit to connect to its local address. The tunnel carries that connection through the PC to the phone.

```text
Open Headunit on 5554
    → 127.0.0.1:5277 on 5554
    → adb reverse
    → 127.0.0.1:5277 on the PC
    → adb forward
    → Android Auto server on 5556
```

Keep the device assignments as shown. Without `reverse`, `127.0.0.1` points only to the head unit emulator itself.

Both methods connect to Android Auto's developer server through the PC. They exercise the media interface and controls, but do not test the handshake used by a physical USB connection or wireless Android Auto with Bluetooth and Wi-Fi Direct. See the [ADB reference](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/user/adb.1.md) for forwarding and reverse forwarding.

### Inspect and remove the rules

```powershell
adb forward --list
```

If you used Method 2, also inspect the reverse rule:

```powershell
adb -s emulator-5554 reverse --list
```

When finished, disconnect in Open Headunit, stop the server on the phone, and remove the forwarding rule:

```powershell
adb -s emulator-5556 forward --remove tcp:5277
```

If you used Method 2, remove its reverse rule as well:

```powershell
adb -s emulator-5554 reverse --remove tcp:5277
```

After restarting ADB or either emulator, check the rules and recreate them if needed.

## What to check

On the **phone**, finish OmniBeat's onboarding and add several stations with distinct names. Mark at least two as favorites. You should be able to select and start a station from Android Auto without first starting playback on the phone.

Check the following with both DHU and Open Headunit:

* OmniBeat appears in Android Auto's media app launcher.
* Favorites, All stations, Tags, and Recently played open correctly, including empty sections.
* All stations and Favorites receive the order saved for each list in OmniBeat. Account for any additional sorting selected in the car interface.
* Selecting a station plays that station, including the second item in Favorites.
* Play/Pause and Previous/Next work from the playback screen.
* Switching stations keeps the full-screen player open and updates the station name.
* Track metadata updates on both screens when the stream provides it.
* OmniBeat's Android Auto settings control artwork and automatic playback as expected. Also check Android Auto's own autoplay setting.
* Disconnecting and reconnecting leaves playback state consistent between the phone and the car interface.

Run the checks separately on each head unit. They may handle media queues and session commands differently.

## Troubleshooting

### Android Auto has no developer menu

Make sure you are in Android Auto's settings, not **App info**. Replace a `-stub` installation with a full Android Auto package that supports the emulator's architecture.

An APKM file is a bundle of split APKs. Use a split APK installer, or extract the required APKs and install them with `adb install-multiple`. Passing an APKM file directly to `adb install` will not work.

### DHU stays on “Waiting for phone”

Check the phone's serial number, the forwarding rule, the head unit server notification, and any pending setup prompts. The DHU message `[I]: connected` confirms a TCP connection; Android Auto may still be completing its startup sequence.

If the server has stopped, restart it before reconnecting. A missing `headunit.ini` warning alone does not identify the problem: DHU can run with its default configuration.

### The connection drops after a few seconds

`Failed to read from transport - disconnect` indicates that the transport closed, not why it closed. Disconnect any other head unit and check the server and first-time setup prompts.

For further diagnosis, start recording logs **before** reconnecting. Run each command in its own terminal:

```powershell
adb -s emulator-5556 logcat -v time > phone-aa-log.txt
```

When testing Open Headunit, also record the head unit's log:

```powershell
adb -s emulator-5554 logcat -v time > headunit-log.txt
```

Reproduce the disconnect, then press `Ctrl+C` to stop recording. These logs help distinguish an Android Auto failure, a head unit failure, and a transport problem.

### OmniBeat does not appear in the launcher

Open OmniBeat on the phone and finish onboarding. Check **Unknown sources** in Android Auto's developer settings and **Customize launcher**, if available, then reconnect.

Android Auto's **Unknown sources** setting controls which apps it displays. Android's separate **Install unknown apps** permission controls APK installation.
