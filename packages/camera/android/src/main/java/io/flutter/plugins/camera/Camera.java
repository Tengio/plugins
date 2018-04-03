package io.flutter.plugins.camera;

import android.hardware.camera2.CameraAccessException;
import android.os.Handler;

import io.flutter.plugin.common.MethodChannel;

interface Camera {

    void startPreview() throws CameraAccessException, CameraException;

    void open(MethodChannel.Result result);

    void takePicture(String filePath, MethodChannel.Result result);

    void startVideoRecording(String filePath, MethodChannel.Result result);

    void stopVideoRecording(MethodChannel.Result result);

    void close();

    void dispose();

}
