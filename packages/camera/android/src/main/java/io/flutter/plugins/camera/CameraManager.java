package io.flutter.plugins.camera;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.os.Build;
import android.os.Bundle;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

class CameraManager {

    private static final int CAMERA_REQUEST_ID = 513469796;
    private Camera camera;
    private Runnable cameraPermissionContinuation;
    private PluginRegistry.Registrar registrar;
    private boolean requestingPermission;

    CameraManager(final PluginRegistry.Registrar registrar) {
        this.registrar = registrar;
        registrar.addRequestPermissionsResultListener(new CameraRequestPermissionsListener());
        registrar.activity().getApplication().registerActivityLifecycleCallbacks(new CameraActivityLifecycleListener());
    }

    void init() {
        if (camera != null) {
            camera.close();
        }
    }

    void openCamera(final String cameraName, final String resolutionPreset, final MethodChannel.Result result) {
        if (camera != null) {
            camera.close();
        }
        if (cameraPermissionContinuation != null) {
            result.error("cameraPermission", "Camera permission request ongoing", null);
        }
        cameraPermissionContinuation = new Runnable() {
            @Override
            public void run() {
                cameraPermissionContinuation = null;
                if (!hasCameraPermission()) {
                    if (result != null) {
                        result.error("cameraPermission", "MediaRecorderCamera permission not granted", null);
                        return;
                    }
                }
                if (!hasAudioPermission()) {
                    if (result != null) {
                        result.error("cameraPermission", "MediaRecorderAudio permission not granted", null);
                        return;
                    }
                }
                open(cameraName, resolutionPreset, result);
            }
        };
        requestingPermission = false;
        if (hasCameraPermission() && hasAudioPermission()) {
            cameraPermissionContinuation.run();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestingPermission = true;
                registrar.activity().requestPermissions(new String[] {
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                }, CAMERA_REQUEST_ID);
            }
        }
    }

    private void open(final String cameraName, final String resolutionPreset, final MethodChannel.Result result) {
        camera = new MediaRecorderCamera(registrar, cameraName, resolutionPreset, result);
        camera.open(result);
    }

    void stopVideoRecording(final MethodChannel.Result result) {
        camera.stopVideoRecording(result);
    }

    void startVideoRecording(final String filePath, final MethodChannel.Result result) {
        camera.startVideoRecording(filePath, result);
    }

    void takePicture(final String path, final MethodChannel.Result result) {
        camera.takePicture(path, result);
    }

    void dispose() {
        if (camera != null) {
            camera.dispose();
        }
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || registrar.activity().checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || registrar.activity().checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    void getAvailableCameras(final MethodChannel.Result result) {
        try {
            result.success(new CameraServiceFacade(registrar).getAvailableCameras());
        } catch (CameraAccessException e) {
            result.error("cameraAccess", e.getMessage(), null);
        }
    }

    private class CameraRequestPermissionsListener
            implements PluginRegistry.RequestPermissionsResultListener {

        @Override
        public boolean onRequestPermissionsResult(int id, String[] permissions, int[] grantResults) {
            if (id == CAMERA_REQUEST_ID) {
                cameraPermissionContinuation.run();
                return true;
            }
            return false;
        }
    }

    private class CameraActivityLifecycleListener implements Application.ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (requestingPermission) {
                requestingPermission = false;
                return;
            }
            if (activity == registrar.activity()) {
                if (camera != null) {
                    camera.open(null);
                }
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if (activity == registrar.activity()) {
                if (camera != null) {
                    camera.close();
                }
            }
        }

        @Override
        public void onActivityStopped(Activity activity) {
            if (activity == registrar.activity()) {
                if (camera != null) {
                    camera.close();
                }
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    }
}
