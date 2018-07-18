// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.share;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Plugin method host for presenting a share sheet via Intent */
public class SharePlugin implements MethodChannel.MethodCallHandler {

  private static final String CHANNEL = "plugins.flutter.io/share";

  /// The URL is used by [listKnownAvailableSharingApps] to test if the app is available on the device.
  private static final Map<String, String> KNOWN_SHARING_APPS =
      new HashMap<String, String>() {
        {
          put("twitter", "twitter://post?message=");
          put("telegram", "tg://msg?text=");
          put("whatsapp", "whatsapp://send?text=");
          put("email", "mailto:foo@bar?body=");
        }
      };

  public static void registerWith(Registrar registrar) {
    MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL);
    SharePlugin instance = new SharePlugin(registrar);
    channel.setMethodCallHandler(instance);
  }

  private final Registrar mRegistrar;

  private SharePlugin(Registrar registrar) {
    this.mRegistrar = registrar;
  }

  @Override
  public void onMethodCall(MethodCall call, MethodChannel.Result result) {
    if (call.method.equals("openShareDialog")) {
      if (!(call.arguments instanceof Map)) {
        throw new IllegalArgumentException("Map argument expected");
      }
      // Android does not support showing the share sheet at a particular point on screen.
      openShareDialog((String) call.argument("text"));
      result.success(null);
    } else if (call.method.equals("listKnownAvailableSharingApps")) {
      listKnownAvailableSharingApps(result);
    } else if (call.method.equals("shareWithTwitter")) {
      shareWithTwitter(result, (String) call.argument("text"));
    } else if (call.method.equals("shareWithTelegram")) {
      shareWithTelegram(result, (String) call.argument("text"));
    } else if (call.method.equals("shareWithWhatsApp")) {
      shareWithWhatsApp(result, (String) call.argument("text"));
    } else if (call.method.equals("shareWithEmail")) {
      shareWithEmail(
          result,
          (String) call.argument("text"),
          (String) call.argument("recipient"),
          (String) call.argument("subject"));
    } else {
      result.notImplemented();
    }
  }

  private void openShareDialog(String text) {
    if (text == null || text.isEmpty()) {
      throw new IllegalArgumentException("Non-empty text expected");
    }

    Intent shareIntent = new Intent();
    shareIntent.setAction(Intent.ACTION_SEND);
    shareIntent.putExtra(Intent.EXTRA_TEXT, text);
    shareIntent.setType("text/plain");
    Intent chooserIntent = Intent.createChooser(shareIntent, null /* dialog title optional */);
    if (mRegistrar.activity() != null) {
      mRegistrar.activity().startActivity(chooserIntent);
    } else {
      chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      mRegistrar.context().startActivity(chooserIntent);
    }
  }

  private void listKnownAvailableSharingApps(MethodChannel.Result result) {
    PackageManager packageManager = mRegistrar.activity().getPackageManager();
    ArrayList<String> availableApps = new ArrayList<>();
    for (Map.Entry<String, String> entry : KNOWN_SHARING_APPS.entrySet()) {
      Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(entry.getValue()));
      if (intent.resolveActivity(packageManager) != null) {
        availableApps.add(entry.getKey());
      }
    }
    result.success(availableApps);
  }

  private void shareWithTwitter(MethodChannel.Result result, String text) {
    openUrl(result, Uri.parse("twitter://post?message=" + text));
  }

  private void shareWithTelegram(MethodChannel.Result result, String text) {
    openUrl(result, Uri.parse("tg://msg?text=" + text));
  }

  private void shareWithWhatsApp(MethodChannel.Result result, String text) {
    openUrl(result, Uri.parse("whatsapp://send?text=" + text));
  }

  private void shareWithEmail(
      MethodChannel.Result result, String text, String recipient, String subject) {
    openUrl(result, Uri.parse("mailto:" + recipient + "?subject=" + subject + "&body=" + text));
  }

  private void openUrl(MethodChannel.Result result, Uri uri) {
    try {
      mRegistrar.activity().startActivity(new Intent(Intent.ACTION_VIEW, uri));
    } catch (ActivityNotFoundException e) {
      result.error("shareError", e.getMessage(), null);
      return;
    }
    result.success(null);
  }
}
