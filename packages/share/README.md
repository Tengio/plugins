# Share plugin

[![pub package](https://img.shields.io/pub/v/share.svg)](https://pub.dartlang.org/packages/share)

A Flutter plugin to share content from your Flutter app via the platform's
share dialog.

Wraps the ACTION_SEND Intent on Android and UIActivityViewController
on iOS.

Can also open directly (without the share dialog) some common sharing apps (using URL Scheme).
Currently supported apps:

    - Twitter
    - Telegram
    - WhatsApp
    - Email

## Usage
To use this plugin, add `share` as a [dependency in your pubspec.yaml file](https://flutter.io/platform-plugins/).

On IOS, to open the supported sharing apps without the share dialog, you need to add the corresponding  URL Schemes to the list of `LSApplicationQueriesSchemes` in the `Runner/Info.plist`.

    - Twitter: `twitter`
    - Telegram: `tg`
    - WhatsApp: `whatsapp`
    - Email: `mailto`

Example:
```
 <key>LSApplicationQueriesSchemes</key>
    <array>
       <string>tg</string>
       <string>whatsapp</string>
       <string>twitter</string>
       <string>mailto</string>
    </array>
```


## Example

Import the library via
``` dart
import 'package:share/share.dart';
```

### To open the native share dialog:
Invoke the static `openShareDialog` method anywhere in your Dart code.
``` dart
Share.openShareDialog('check out my website https://example.com');
```

### To get a list of all the known available sharing apps:
Invoke the static `listKnownAvailableSharingApps` method anywhere in your Dart code.

### To open one of the specific sharing app:
First check the app is installed and available with `listKnownAvailableSharingApps`.

Then call the corresponding function:
``` dart
Share.shareWithTwitter('check out my website https://example.com');
```
Or
``` dart
Share.shareWithTelegram('check out my website https://example.com');
```
...