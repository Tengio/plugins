package io.flutter.plugins.camera;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.view.FlutterView;

import static android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE;
import static android.hardware.camera2.CaptureRequest.JPEG_ORIENTATION;

final class MediaRecorderCamera implements Camera {

    private static final String TAG = "CameraPlugin";

    private final FlutterView.SurfaceTextureEntry textureEntry;
    private final CameraServiceFacade cameraManagerService;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private EventChannel.EventSink eventSink;
    private ImageReader imageReader;
    private String cameraName;
    private CaptureRequest.Builder captureRequestBuilder;
    private MediaRecorder mediaRecorder;
    private CameraServiceFacade.CameraInformation cameraInformation;
    private boolean recordingVideo;

    @SuppressWarnings("ConstantConditions") MediaRecorderCamera(final PluginRegistry.Registrar registrar,
            final String cameraName,
            final String resolutionPreset,
            final MethodChannel.Result result) {
        this.cameraName = cameraName;
        cameraManagerService = new CameraServiceFacade(registrar);
        textureEntry = registrar.view().createSurfaceTexture();
        registerEventChannel(registrar);
        try {
            cameraInformation = cameraManagerService.computeCameraInformation(cameraName, resolutionPreset);
            Log.e(TAG, "CameraInformation: " + cameraInformation);
        } catch (CameraAccessException e) {
            result.error("CameraAccessException", e.getMessage(), null);
        } catch (IllegalArgumentException e) {
            result.error("IllegalArgumentException", e.getMessage(), null);
        }
    }

    @Override
    public void startPreview() throws CameraAccessException, CameraException {
        closeCaptureSession();
        List<Surface> surfaces = prepareCaptureRequestBuilder(CameraDevice.TEMPLATE_PREVIEW);

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
                            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
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

    @Override
    public void open(final MethodChannel.Result result) {
        try {
            imageReader = ImageReader.newInstance(cameraInformation.getCaptureSize().getWidth(),
                    cameraInformation.getCaptureSize().getHeight(), ImageFormat.JPEG, 2);
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
                                reply.put("previewWidth", cameraInformation.getPreviewSize().getWidth());
                                reply.put("previewHeight", cameraInformation.getPreviewSize().getHeight());
                                reply.put("rotation", cameraManagerService.getDisplayRotation());
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
                                        errorDescription
                                                = "The camera device could not be opened due to a device policy.";
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

    @Override
    public void takePicture(String filePath, final MethodChannel.Result result) {
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
            final CaptureRequest.Builder cb = cameraDevice.createCaptureRequest(TEMPLATE_STILL_CAPTURE);
            cb.addTarget(imageReader.getSurface());
            cb.set(JPEG_ORIENTATION, cameraInformation.getOrientationHint(cameraManagerService.getDisplayRotation()));
            cameraCaptureSession.capture(
                    cb.build(),
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
    public void startVideoRecording(String filePath, final MethodChannel.Result result) {
        if (cameraDevice == null) {
            result.error("configureFailed", "MediaRecorderCamera was closed during configuration.", null);
            return;
        }
        try {
            closeCaptureSession();

            prepareMediaRecorder(filePath);
            recordingVideo = true;

            List<Surface> surfaces = prepareCaptureRequestBuilder(CameraDevice.TEMPLATE_RECORD);

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

    private void closeCaptureSession() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
    }

    @NonNull
    private List<Surface> prepareCaptureRequestBuilder(final int templateRecord) throws CameraAccessException {
        SurfaceTexture surfaceTexture = textureEntry.surfaceTexture();
        surfaceTexture.setDefaultBufferSize(cameraInformation.getPreviewSize().getWidth(),
                cameraInformation.getPreviewSize().getHeight());
        captureRequestBuilder = cameraDevice.createCaptureRequest(templateRecord);

        List<Surface> surfaces = new ArrayList<>();
        Surface previewSurface = new Surface(surfaceTexture);
        surfaces.add(previewSurface);
        captureRequestBuilder.addTarget(previewSurface);
        return surfaces;
    }

    private void writeToFile(ByteBuffer buffer, File file) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            while (0 < buffer.remaining()) {
                outputStream.getChannel().write(buffer);
            }
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

    private void prepareMediaRecorder(String outputFilePath) throws IOException {
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
        mediaRecorder.setVideoSize(cameraInformation.getVideoSize().getWidth(),
                cameraInformation.getVideoSize().getHeight());
        mediaRecorder.setOutputFile(outputFilePath);
        int rotation = cameraManagerService.getDisplayRotation();
        mediaRecorder.setOrientationHint(cameraInformation.getOrientationHint(rotation));
        mediaRecorder.prepare();
    }
}
