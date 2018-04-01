package io.flutter.plugins.camera;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.view.FlutterView;

final class MediaRecorderCamera implements Camera {

    private static final String TAG = "CameraPlugin";
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray() {
        {
            append(Surface.ROTATION_0, 0);
            append(Surface.ROTATION_90, 90);
            append(Surface.ROTATION_180, 180);
            append(Surface.ROTATION_270, 270);
        }
    };

    private final FlutterView.SurfaceTextureEntry textureEntry;
    private final CameraServiceFacade cameraManagerService;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private EventChannel.EventSink eventSink;
    private ImageReader imageReader;
    private int sensorOrientation;
    private boolean isFaceFrontingCamera;
    private String cameraName;
    private Size captureSize;
    private Size previewSize;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size videoSize;
    private MediaRecorder mediaRecorder;
    private boolean recordingVideo;

    MediaRecorderCamera(final PluginRegistry.Registrar registrar,
            final String cameraName,
            final String resolutionPreset,
            final MethodChannel.Result result) {
        this.cameraName = cameraName;
        cameraManagerService = new CameraServiceFacade(registrar);
        textureEntry = registrar.view().createSurfaceTexture();
        registerEventChannel(registrar);
        try {
            Size minPreviewSize;
            switch (resolutionPreset) {
                case "high":
                    minPreviewSize = new Size(1024, 768);
                    break;
                case "medium":
                    minPreviewSize = new Size(640, 480);
                    break;
                case "low":
                    minPreviewSize = new Size(320, 240);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown preset: " + resolutionPreset);
            }
            CameraCharacteristics characteristics = cameraManagerService.getCameraCharacteristics(cameraName);
            StreamConfigurationMap streamConfigurationMap =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            //noinspection ConstantConditions
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            //noinspection ConstantConditions
            isFaceFrontingCamera =
                    characteristics.get(CameraCharacteristics.LENS_FACING)
                            == CameraMetadata.LENS_FACING_FRONT;
            computeBestCaptureSize(streamConfigurationMap);
            computeBestPreviewAndRecordingSize(streamConfigurationMap, minPreviewSize, captureSize);
        } catch (CameraAccessException e) {
            result.error("CameraAccessException", e.getMessage(), null);
        } catch (IllegalArgumentException e) {
            result.error("IllegalArgumentException", e.getMessage(), null);
        }
    }

    private void registerEventChannel(final PluginRegistry.Registrar registrar) {
        new EventChannel(
                registrar.messenger(),
                "flutter.io/cameraPlugin/cameraEvents" + textureEntry.id()).setStreamHandler(
                new EventChannel.StreamHandler() {
                    @Override
                    public void onListen(Object arguments, EventChannel.EventSink eventSink) {
                        MediaRecorderCamera.this.eventSink = eventSink;
                    }

                    @Override
                    public void onCancel(Object arguments) {
                        MediaRecorderCamera.this.eventSink = null;
                    }
                });
    }

    private void computeBestPreviewAndRecordingSize(
            StreamConfigurationMap streamConfigurationMap, Size minPreviewSize, Size captureSize) {
        Size[] sizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
        float captureSizeRatio = (float) captureSize.getWidth() / captureSize.getHeight();
        List<Size> goodEnough = new ArrayList<>();
        for (Size s : sizes) {
            if ((float) s.getWidth() / s.getHeight() == captureSizeRatio
                    && minPreviewSize.getWidth() < s.getWidth()
                    && minPreviewSize.getHeight() < s.getHeight()) {
                goodEnough.add(s);
            }
        }

        Collections.sort(goodEnough, new CompareSizesByArea());

        if (goodEnough.isEmpty()) {
            previewSize = sizes[0];
            videoSize = sizes[0];
        } else {
            previewSize = goodEnough.get(0);

            // Video capture size should not be greater than 1080 because MediaRecorder cannot attachTo higher
            // resolutions.
            videoSize = goodEnough.get(0);
            for (int i = goodEnough.size() - 1; i >= 0; i--) {
                if (goodEnough.get(i).getHeight() <= 1080) {
                    videoSize = goodEnough.get(i);
                    break;
                }
            }
        }
    }

    private void computeBestCaptureSize(StreamConfigurationMap streamConfigurationMap) {
        // For still image captures, we use the largest available size.
        captureSize = Collections.max(
                Arrays.asList(streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)),
                new CompareSizesByArea());
    }

    private void prepareMediaRecorder(int rotation, String outputFilePath) throws IOException {
        if (mediaRecorder != null) {
            mediaRecorder.release();
        }
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoEncodingBitRate(1024 * 1000);
        mediaRecorder.setAudioSamplingRate(16000);
        mediaRecorder.setVideoFrameRate(27);
        mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorder.setOutputFile(outputFilePath);

        int displayOrientation = ORIENTATIONS.get(rotation);
        if (isFaceFrontingCamera) {
            displayOrientation = -displayOrientation;
        }
        mediaRecorder.setOrientationHint((displayOrientation + sensorOrientation) % 360);

        mediaRecorder.prepare();
    }

    @Override
    public void open(final MethodChannel.Result result) {
        try {
            imageReader = ImageReader.newInstance(captureSize.getWidth(), captureSize.getHeight(), ImageFormat.JPEG, 2);
            cameraManagerService.openCamera(cameraName,
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice cameraDevice) {
                            MediaRecorderCamera.this.cameraDevice = cameraDevice;

                            try {
                                startPreview();
                            } catch (CameraAccessException e) {
                                if (result != null) {
                                    result.error("CameraAccess", e.getMessage(), null);
                                }
                                e.printStackTrace();
                            } catch (CameraException e) {
                                if (result != null) {
                                    result.error("CameraException", e.getMessage(), null);
                                }
                                e.printStackTrace();
                            }

                            if (result != null) {
                                Map<String, Object> reply = new HashMap<>();
                                reply.put("textureId", textureEntry.id());
                                reply.put("previewWidth", previewSize.getWidth());
                                reply.put("previewHeight", previewSize.getHeight());
                                result.success(reply);
                            }
                        }

                        @Override
                        public void onClosed(@NonNull CameraDevice camera) {
                            if (eventSink != null) {
                                Map<String, String> event = new HashMap<>();
                                event.put("eventType", "cameraClosing");
                                eventSink.success(event);
                            }
                            super.onClosed(camera);
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                            cameraDevice.close();
                            MediaRecorderCamera.this.cameraDevice = null;
                            if (eventSink != null) {
                                Map<String, String> event = new HashMap<>();
                                event.put("eventType", "error");
                                event.put("errorDescription", "The camera was disconnected");
                                eventSink.success(event);
                            }
                        }

                        @Override
                        public void onError(@NonNull CameraDevice cameraDevice, int errorCode) {
                            cameraDevice.close();
                            MediaRecorderCamera.this.cameraDevice = null;
                            if (eventSink != null) {
                                String errorDescription;
                                switch (errorCode) {
                                    case ERROR_CAMERA_IN_USE:
                                        errorDescription = "The camera device is in use already.";
                                        break;
                                    case ERROR_MAX_CAMERAS_IN_USE:
                                        errorDescription = "Max cameras in use";
                                        break;
                                    case ERROR_CAMERA_DISABLED:
                                        errorDescription =
                                                "The camera device could not be opened due to a device policy.";
                                        break;
                                    case ERROR_CAMERA_DEVICE:
                                        errorDescription = "The camera device has encountered a fatal error";
                                        break;
                                    case ERROR_CAMERA_SERVICE:
                                        errorDescription = "The camera service has encountered a fatal error.";
                                        break;
                                    default:
                                        errorDescription = "Unknown camera error";
                                }
                                Map<String, String> event = new HashMap<>();
                                event.put("eventType", "error");
                                event.put("errorDescription", errorDescription);
                                eventSink.success(event);
                            }
                        }
                    });
        } catch (CameraAccessException e) {
            if (result != null) {
                result.error("cameraAccess", e.getMessage(), null);
            }
            e.printStackTrace();
        }
    }

    private void writeToFile(ByteBuffer buffer, File file) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            while (0 < buffer.remaining()) {
                outputStream.getChannel().write(buffer);
            }
        }
    }

    @Override
    public void takePicture(int rotation, String filePath, final MethodChannel.Result result) {
        final File file = new File(filePath);

        imageReader.setOnImageAvailableListener(
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        try (Image image = reader.acquireLatestImage()) {
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            writeToFile(buffer, file);
                            result.success(null);
                        } catch (IOException e) {
                            result.error("IOError", "Failed saving image", null);
                        }
                    }
                },
                null);

        try {
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            int displayOrientation = ORIENTATIONS.get(rotation);
            if (isFaceFrontingCamera) {
                displayOrientation = -displayOrientation;
            }
            captureBuilder.set(
                    CaptureRequest.JPEG_ORIENTATION, (-displayOrientation + sensorOrientation) % 360);

            cameraCaptureSession.capture(
                    captureBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureFailed(
                                @NonNull CameraCaptureSession session,
                                @NonNull CaptureRequest request,
                                @NonNull CaptureFailure failure) {
                            String reason;
                            switch (failure.getReason()) {
                                case CaptureFailure.REASON_ERROR:
                                    reason = "An error happened in the framework";
                                    break;
                                case CaptureFailure.REASON_FLUSHED:
                                    reason = "The capture has failed due to an abortCaptures() call";
                                    break;
                                default:
                                    reason = "Unknown reason";
                            }
                            result.error("captureFailure", reason, null);
                        }
                    },
                    null);
        } catch (CameraAccessException e) {
            result.error("cameraAccess", e.getMessage(), null);
        }
    }

    @Override
    public void startVideoRecording(int rotation, String filePath, final MethodChannel.Result result) {
        if (cameraDevice == null) {
            result.error("configureFailed", "MediaRecorderCamera was closed during configuration.", null);
            return;
        }
        try {
            closeCaptureSession();
            prepareMediaRecorder(rotation, filePath);

            recordingVideo = true;

            SurfaceTexture surfaceTexture = textureEntry.surfaceTexture();
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            List<Surface> surfaces = new ArrayList<>();

            Surface previewSurface = new Surface(surfaceTexture);
            surfaces.add(previewSurface);
            captureRequestBuilder.addTarget(previewSurface);

            Surface recorderSurface = mediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            captureRequestBuilder.addTarget(recorderSurface);

            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try {
                        if (cameraDevice == null) {
                            result.error("configureFailed",
                                    "MediaRecorderCamera was closed during configuration",
                                    null);
                            return;
                        }
                        MediaRecorderCamera.this.cameraCaptureSession = cameraCaptureSession;
                        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                        mediaRecorder.start();
                        result.success(null);
                    } catch (CameraAccessException e) {
                        result.error("cameraAccess", e.getMessage(), null);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    result.error("configureFailed", "Failed to configure camera session", null);
                }
            }, null);
        } catch (CameraAccessException | IOException e) {
            result.error("videoRecordingFailed", e.getMessage(), null);
        }
    }

    @Override
    public void stopVideoRecording(final MethodChannel.Result result) {
        if (!recordingVideo) {
            result.success("The video was not recording, nothing to stop.");
            return;
        }

        try {
            recordingVideo = false;
            mediaRecorder.stop();
            mediaRecorder.reset();
            startPreview();
            result.success("stopVideoRecording called successfully.");
        } catch (Exception e) {
            result.error("videoRecordingFailed", e.getMessage(), null);
        }
    }

    @Override
    public void startPreview() throws CameraAccessException, CameraException {
        closeCaptureSession();

        SurfaceTexture surfaceTexture = textureEntry.surfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        List<Surface> surfaces = new ArrayList<>();

        Surface previewSurface = new Surface(surfaceTexture);
        surfaces.add(previewSurface);
        captureRequestBuilder.addTarget(previewSurface);

        surfaces.add(imageReader.getSurface());

        cameraDevice.createCaptureSession(surfaces,
                new CameraCaptureSession.StateCallback() {

                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            if (cameraDevice == null) {
                                Log.e(TAG, "onConfigured: MediaRecorderCamera was closed during configuration.");
                                return;
                            }
                            cameraCaptureSession = session;
                            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                                    CameraMetadata.CONTROL_MODE_AUTO);
                            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Log.e(TAG, "onConfigureFailed: Failed to configure camera for preview.");
                    }
                }, null);
    }

    private void closeCaptureSession() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
    }

    @Override
    public void close() {
        closeCaptureSession();
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    @Override
    public void dispose() {
        close();
        textureEntry.release();
    }
}
