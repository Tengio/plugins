// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "SharePlugin.h"

@interface NSError (FlutterError)
@property(readonly, nonatomic) FlutterError *flutterError;
@end

@implementation NSError (FlutterError)
- (FlutterError *)flutterError {
  return [FlutterError errorWithCode:[NSString stringWithFormat:@"Error %d", (int)self.code]
                             message:self.domain
                             details:self.localizedDescription];
}
@end

static NSString *const PLATFORM_CHANNEL = @"plugins.flutter.io/share";

@implementation FLTSharePlugin

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar> *)registrar {
  FlutterMethodChannel *shareChannel =
      [FlutterMethodChannel methodChannelWithName:PLATFORM_CHANNEL
                                  binaryMessenger:registrar.messenger];

  [shareChannel setMethodCallHandler:^(FlutterMethodCall *call, FlutterResult result) {
    NSDictionary *arguments = [call arguments];
    if ([@"openShareDialog" isEqualToString:call.method]) {
      NSString *shareText = arguments[@"text"];

      if (shareText.length == 0) {
        result(
            [FlutterError errorWithCode:@"error" message:@"Non-empty text expected" details:nil]);
        return;
      }

      NSNumber *originX = arguments[@"originX"];
      NSNumber *originY = arguments[@"originY"];
      NSNumber *originWidth = arguments[@"originWidth"];
      NSNumber *originHeight = arguments[@"originHeight"];

      CGRect originRect;
      if (originX != nil && originY != nil && originWidth != nil && originHeight != nil) {
        originRect = CGRectMake([originX doubleValue], [originY doubleValue],
                                [originWidth doubleValue], [originHeight doubleValue]);
      }

      [self openShareDialog:shareText
             withController:[UIApplication sharedApplication].keyWindow.rootViewController
                   atSource:originRect];
      result(nil);
    } else if ([@"listKnownAvailableSharingApps" isEqualToString:call.method]) {
      [self listKnownAvailableSharingApps:result];
    } else if ([@"shareWithTwitter" isEqualToString:call.method]) {
      [self shareWithTwitter:result text:arguments[@"text"]];
    } else if ([@"shareWithTelegram" isEqualToString:call.method]) {
      [self shareWithTelegram:result text:arguments[@"text"]];
    } else if ([@"shareWithWhatsApp" isEqualToString:call.method]) {
      [self shareWithWhatsApp:result text:arguments[@"text"]];
    } else if ([@"shareWithEmail" isEqualToString:call.method]) {
      [self shareWithEmail:result
                      text:arguments[@"text"]
                 recipient:arguments[@"recipient"]
                   subject:arguments[@"subject"]];
    } else {
      result(FlutterMethodNotImplemented);
    }
  }];
}

+ (void)openShareDialog:(id)sharedItems
         withController:(UIViewController *)controller
               atSource:(CGRect)origin {
  UIActivityViewController *activityViewController =
      [[UIActivityViewController alloc] initWithActivityItems:@[ sharedItems ]
                                        applicationActivities:nil];
  activityViewController.popoverPresentationController.sourceView = controller.view;
  if (!CGRectIsEmpty(origin)) {
    activityViewController.popoverPresentationController.sourceRect = origin;
  }
  [controller presentViewController:activityViewController animated:YES completion:nil];
}

+ (void)listKnownAvailableSharingApps:(FlutterResult)result {
  UIApplication *application = [UIApplication sharedApplication];
  NSMutableArray *availableApps = [NSMutableArray new];
  if ([application canOpenURL:[NSURL URLWithString:@"twitter://post?message="]]) {
    [availableApps addObject:@"twitter"];
  }
  if ([application canOpenURL:[NSURL URLWithString:@"tg://msg?text="]]) {
    [availableApps addObject:@"telegram"];
  }
  if ([application canOpenURL:[NSURL URLWithString:@"whatsapp://send?text="]]) {
    [availableApps addObject:@"whatsapp"];
  }
  if ([application canOpenURL:[NSURL URLWithString:@"mailto:foo@bar?body="]]) {
    [availableApps addObject:@"email"];
  }
  result(availableApps);
}

+ (void)shareWithTwitter:(FlutterResult)result text:(NSString *)text {
  NSString *urlString = [[NSString stringWithFormat:@"twitter://post?message=%@", text]
      stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
  [self openUrl:result url:[NSURL URLWithString:urlString]];
}

+ (void)shareWithTelegram:(FlutterResult)result text:(NSString *)text {
  NSString *urlString = [[NSString stringWithFormat:@"tg://msg?text=%@", text]
      stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
  [self openUrl:result url:[NSURL URLWithString:urlString]];
}

+ (void)shareWithWhatsApp:(FlutterResult)result text:(NSString *)text {
  NSString *urlString = [[NSString stringWithFormat:@"whatsapp://send?text=%@", text]
      stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
  [self openUrl:result url:[NSURL URLWithString:urlString]];
}

+ (void)shareWithEmail:(FlutterResult)result
                  text:(NSString *)text
             recipient:(NSString *)recipient
               subject:(NSString *)subject {
  NSString *urlString =
      [[NSString stringWithFormat:@"mailto:%@?subject=%@&body=%@", recipient, subject, text]
          stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
  [self openUrl:result url:[NSURL URLWithString:urlString]];
}

+ (void)openUrl:(FlutterResult)result url:(NSURL *)url {
  UIApplication *application = [UIApplication sharedApplication];
  if ([application canOpenURL:url]) {
    [application openURL:url];
    result(nil);
  } else {
    result([FlutterError
        errorWithCode:@"shareError"
              message:[NSString stringWithFormat:@"Couldn't open app with url = %@", url]
              details:nil]);
  }
}
@end
