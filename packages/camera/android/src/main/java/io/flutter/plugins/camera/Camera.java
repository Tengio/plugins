package io.flutter.plugins.camera;

import android.hardware.camera2.CameraAccessException;
import android.os.Handler;

import io.flutter.plugin.common.MethodChannel;

interface Camera {

    void open(MethodChannel.Result result);

    void takePicture(int rotation, String filePath, MethodChannel.Result result);

    void startVideoRecording(int rotation, String filePath, MethodChannel.Result result);

    void stopVideoRecording(MethodChannel.Result result);

    void startPreview() throws CameraAccessException, CameraException;

    void close();

    void dispose();

}
