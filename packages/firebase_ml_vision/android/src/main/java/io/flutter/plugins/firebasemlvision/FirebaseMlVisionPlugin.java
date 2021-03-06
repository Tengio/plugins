package io.flutter.plugins.firebasemlvision;

import android.net.Uri;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/** FirebaseMlVisionPlugin */
public class FirebaseMlVisionPlugin implements MethodCallHandler {
  private Registrar registrar;

  private FirebaseMlVisionPlugin(Registrar registrar) {
    this.registrar = registrar;
  }

  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel =
        new MethodChannel(registrar.messenger(), "plugins.flutter.io/firebase_ml_vision");
    channel.setMethodCallHandler(new FirebaseMlVisionPlugin(registrar));
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    Map<String, Object> options = call.argument("options");
    FirebaseVisionImage image;
    switch (call.method) {
      case "BarcodeDetector#detectInImage":
        try {
          image = filePathToVisionImage((String) call.argument("path"));
          BarcodeDetector.instance.handleDetection(image, options, result);
        } catch (IOException e) {
          result.error("barcodeDetectorIOError", e.getLocalizedMessage(), null);
        } catch (Exception e) {
          result.error("barcodeDetectorError", e.getLocalizedMessage(), null);
        }
        break;
      case "FaceDetector#detectInImage":
        break;
      case "LabelDetector#detectInImage":
        break;
      case "TextDetector#detectInImage":
        try {
          image = filePathToVisionImage((String) call.argument("path"));
          TextDetector.instance.handleDetection(image, options, result);
        } catch (IOException e) {
          result.error("textDetectorIOError", e.getLocalizedMessage(), null);
        } catch (Exception e) {
          result.error("textDetectorError", e.getLocalizedMessage(), null);
        }
        break;
      default:
        result.notImplemented();
    }
  }

  private FirebaseVisionImage filePathToVisionImage(String path) throws IOException {
    File file = new File(path);
    return FirebaseVisionImage.fromFilePath(registrar.context(), Uri.fromFile(file));
  }
}
