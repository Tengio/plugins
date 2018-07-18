// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'package:flutter/material.dart';
import 'package:share/share.dart';

void main() {
  runApp(new DemoApp());
}

class DemoApp extends StatefulWidget {
  @override
  _DemoAppState createState() => new _DemoAppState();
}

class _DemoAppState extends State<DemoApp> {
  String text = '';
  String availableAppsText = '';
  List<String> knownAvailableSharingApps = <String>[];

  @override
  void initState() {
    Share.listKnownAvailableSharingApps().then((List<String> apps) {
      availableAppsText = 'Known sharing apps:\n';
      apps.forEach((String app) => availableAppsText += '$app, ');
      availableAppsText = availableAppsText.substring(
          0, availableAppsText.length - 2); // Remove trailing comma.
      if (mounted) setState(() => knownAvailableSharingApps = apps);
    });
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      title: 'Share Plugin Demo',
      home: new Scaffold(
          appBar: new AppBar(
            title: const Text('Share Plugin Demo'),
          ),
          body: new Padding(
            padding: const EdgeInsets.all(24.0),
            child: new Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                new Text(availableAppsText, textAlign: TextAlign.center),
                const Padding(padding: const EdgeInsets.only(top: 24.0)),
                new TextField(
                  decoration: const InputDecoration(
                    labelText: 'Share:',
                    hintText: 'Enter some text and/or link to share',
                  ),
                  maxLines: 1,
                  onChanged: (String value) => setState(() {
                        text = value;
                      }),
                ),
                const Padding(padding: const EdgeInsets.only(top: 24.0)),
                new Builder(
                  builder: (BuildContext context) {
                    return new RaisedButton(
                      child: const Text('Open share dialog'),
                      onPressed: text.isEmpty
                          ? null
                          : () {
                              // A builder is used to retrieve the context immediately
                              // surrounding the RaisedButton.
                              //
                              // The context's `findRenderObject` returns the first
                              // RenderObject in its descendent tree when it's not
                              // a RenderObjectWidget. The RaisedButton's RenderObject
                              // has its position and size after it's built.
                              final RenderBox box = context.findRenderObject();
                              Share.openShareDialog(
                                text,
                                sharePositionOrigin:
                                    box.localToGlobal(Offset.zero) & box.size,
                              );
                            },
                    );
                  },
                ),
                const Padding(padding: const EdgeInsets.only(top: 12.0)),
                new RaisedButton(
                    child: const Text('Share with Twitter'),
                    onPressed: !knownAvailableSharingApps.contains("twitter")
                        ? null
                        : () => Share.shareWithTwitter(text: text).catchError(
                            (ShareException e) =>
                                print("Share error: ${e.description}"))),
                const Padding(padding: const EdgeInsets.only(top: 12.0)),
                new RaisedButton(
                    child: const Text('Share with Telegram'),
                    onPressed: !knownAvailableSharingApps.contains("telegram")
                        ? null
                        : () => Share.shareWithTelegram(text: text).catchError(
                            (ShareException e) =>
                                print("Share error: ${e.description}"))),
                const Padding(padding: const EdgeInsets.only(top: 12.0)),
                new RaisedButton(
                    child: const Text('Share with WhatsApp'),
                    onPressed: !knownAvailableSharingApps.contains("whatsapp")
                        ? null
                        : () => Share.shareWithWhatsApp(text: text).catchError(
                            (ShareException e) =>
                                print("Share error: ${e.description}"))),
                const Padding(padding: const EdgeInsets.only(top: 12.0)),
                new RaisedButton(
                    child: const Text('Share with email'),
                    onPressed: !knownAvailableSharingApps.contains("email")
                        ? null
                        : () => Share.shareWithEmail(text: text).catchError(
                            (ShareException e) =>
                                print("Share error: ${e.description}"))),
              ],
            ),
          )),
    );
  }
}
