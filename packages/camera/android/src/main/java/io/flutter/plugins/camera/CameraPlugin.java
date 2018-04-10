package io.flutter.plugins.camera;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

public class CameraPlugin implements MethodCallHandler {

    private static final String PLUGIN_NAME = "plugins.flutter.io/camera";

    private CameraManager cameraManager;

    private CameraPlugin(Registrar registrar) {
        cameraManager = new CameraManager(registrar);
    }

    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), PLUGIN_NAME);
        channel.setMethodCallHandler(new CameraPlugin(registrar));
    }

    @Override
    public void onMethodCall(MethodCall call, final Result result) {
        switch (Methods.valueOf(call.method)) {
            case init:
                cameraManager.init();
                result.success(null);
                break;
            case getAllAvailableCameras:
                cameraManager.getAvailableCameras(result);
                break;
            case openCamera: {
                String cameraName = call.argument("cameraName");
                String resolutionPreset = call.argument("resolutionPreset");
                cameraManager.openCamera(cameraName, resolutionPreset, result);
                break;
            }
            case takePicture: {
                cameraManager.takePicture((String) call.argument("path"), result);
                break;
            }
            case startVideoRecording: {
                final String filePath = call.argument("filePath");
                cameraManager.startVideoRecording(filePath, result);
                break;
            }
            case stopVideoRecording: {
                cameraManager.stopVideoRecording(result);
                break;
            }
            case closeCamera: {
                cameraManager.dispose();
                result.success(null);
                break;
            }
            default:
                result.notImplemented();
                break;
        }
    }

    enum Methods {
        init,
        getAllAvailableCameras,
        openCamera,
        takePicture,
        startVideoRecording,
        stopVideoRecording,
        closeCamera
    }
}
