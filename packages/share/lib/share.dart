// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:async';
import 'dart:ui';

import 'package:flutter/services.dart';
import 'package:meta/meta.dart' show visibleForTesting;

/// Plugin for summoning a platform share sheet.
class Share {
  /// [MethodChannel] used to communicate with the platform side.
  @visibleForTesting
  static const MethodChannel channel =
      const MethodChannel('plugins.flutter.io/share');

  static const String twitter = "twitter";
  static const String telegram = "telegram";
  static const String whatsApp = "whatsapp";
  static const String email = "email";

  /// Summons the platform's share sheet to share text.
  ///
  /// Wraps the platform's native share dialog. Can share a text and/or a URL.
  /// It uses the ACTION_SEND Intent on Android and UIActivityViewController
  /// on iOS.
  ///
  /// The optional `sharePositionOrigin` parameter can be used to specify a global
  /// origin rect for the share sheet to popover from on iPads. It has no effect
  /// on non-iPads.
  ///
  /// May throw [PlatformException] or [FormatException]
  /// from [MethodChannel].
  static Future<void> openShareDialog(String text, {Rect sharePositionOrigin}) {
    assert(text != null);
    assert(text.isNotEmpty);
    final Map<String, dynamic> params = <String, dynamic>{
      'text': text,
    };

    if (sharePositionOrigin != null) {
      params['originX'] = sharePositionOrigin.left;
      params['originY'] = sharePositionOrigin.top;
      params['originWidth'] = sharePositionOrigin.width;
      params['originHeight'] = sharePositionOrigin.height;
    }

    return channel.invokeMethod('openShareDialog', params);
  }

  /// Returns a list of all the known sharing apps available on the device.
  ///
  /// More apps could be available but not known. All available apps will always appear in the share dialog.
  /// On IOS even if an app is installed it will not be available for sharing unless you add the corresponding URL Scheme to the list of LSApplicationQueriesSchemes in the Runner/Info.plist
  ///
  /// Possible results:
  ///   - [twitter]
  ///   - [telegram]
  ///   - [whatsApp]
  ///   - [email]
  static Future<List<String>> listKnownAvailableSharingApps() async {
    final List<dynamic> availableApps =
        await channel.invokeMethod('listKnownAvailableSharingApps');
    return availableApps.cast<String>();
  }

  /// Tries to open Twitter app with a post pre-filled with [text].
  ///
  /// For this to work on IOS you need to add 'twitter' to the list of LSApplicationQueriesSchemes in the Runner/Info.plist
  ///
  /// Will throw a [ShareException] if the app is not installed or fails to open.
  static Future<void> shareWithTwitter({String text}) async {
    try {
      return await channel.invokeMethod(
        'shareWithTwitter',
        <String, dynamic>{"text": text ?? ""},
      );
    } on PlatformException catch (e) {
      throw new ShareException(e.code, e.message);
    }
  }

  /// Tries to open Telegram app with a message pre-filled with [text].
  ///
  /// For this to work on IOS you need to add 'tg' to the list of LSApplicationQueriesSchemes in the Runner/Info.plist
  ///
  /// Will throw a [ShareException] if the app is not installed or fails to open.
  static Future<void> shareWithTelegram({String text}) async {
    try {
      return await channel.invokeMethod(
        'shareWithTelegram',
        <String, dynamic>{"text": text ?? ""},
      );
    } on PlatformException catch (e) {
      throw new ShareException(e.code, e.message);
    }
  }

  /// Tries to open WhatsApp app with a message pre-filled with [text].
  ///
  /// For this to work on IOS you need to add 'whatsapp' to the list of LSApplicationQueriesSchemes in the Runner/Info.plist
  ///
  /// Will throw a [ShareException] if the app is not installed or fails to open.
  static Future<void> shareWithWhatsApp({String text}) async {
    try {
      return await channel.invokeMethod(
        'shareWithWhatsApp',
        <String, dynamic>{"text": text ?? ""},
      );
    } on PlatformException catch (e) {
      throw new ShareException(e.code, e.message);
    }
  }

  /// Tries to open the default email app with a message pre-filled with [text], [recipient] and [subject].
  ///
  /// For this to work on IOS you need to add 'mailto' to the list of LSApplicationQueriesSchemes in the Runner/Info.plist
  ///
  /// Will throw a [ShareException] if the app is not installed or fails to open.
  static Future<void> shareWithEmail(
      {String text, String recipient, String subject}) async {
    try {
      return await channel.invokeMethod(
        'shareWithEmail',
        <String, dynamic>{
          "text": text ?? "",
          "recipient": recipient ?? "",
          "subject": subject ?? "",
        },
      );
    } on PlatformException catch (e) {
      throw new ShareException(e.code, e.message);
    }
  }
}

/// This is thrown when the plugin reports an error.
class ShareException implements Exception {
  String code;
  String description;

  ShareException(this.code, this.description);

  @override
  String toString() => '$runtimeType($code, $description)';
}
