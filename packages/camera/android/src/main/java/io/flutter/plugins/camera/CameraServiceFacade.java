package io.flutter.plugins.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.PluginRegistry;

import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;
import static android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;
import static android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;

class CameraServiceFacade {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray() {
        {
            append(Surface.ROTATION_0, 0);
            append(Surface.ROTATION_90, 90);
            append(Surface.ROTATION_180, 180);
            append(Surface.ROTATION_270, 270);
        }
    };

    private CameraManager cameraManager;

    CameraServiceFacade(final PluginRegistry.Registrar registrar) {
        cameraManager = (CameraManager) registrar.activity().getSystemService(Context.CAMERA_SERVICE);
    }

    @SuppressWarnings("ConstantConditions")
    List<Map<String, Object>> getAvailableCameras() throws CameraAccessException {
        String[] cameraNames = cameraManager.getCameraIdList();
        List<Map<String, Object>> cameras = new ArrayList<>();
        for (String cameraName : cameraNames) {
            HashMap<String, Object> details = new HashMap<>();
            CameraCharacteristics characteristics =
                    cameraManager.getCameraCharacteristics(cameraName);
            details.put("name", cameraName);
            int lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            switch (lensFacing) {
                case CameraMetadata.LENS_FACING_FRONT:
                    details.put("lensFacing", "front");
                    break;
                case CameraMetadata.LENS_FACING_BACK:
                    details.put("lensFacing", "back");
                    break;
                case CameraMetadata.LENS_FACING_EXTERNAL:
                    details.put("lensFacing", "external");
                    break;
            }
            cameras.add(details);
        }
        return cameras;
    }

    void openCamera(final String cameraName, final CameraDevice.StateCallback stateCallback)
            throws CameraAccessException {
        cameraManager.openCamera(cameraName, stateCallback, null);
    }

    @SuppressWarnings("ConstantConditions")
    CameraInformation computeCameraInformation(final String cameraName, final String resolutionPreset)
            throws CameraAccessException {
        CameraInformation ci = new CameraInformation();
        ci.setMinPreviewSize(getResolution(resolutionPreset));

        CameraCharacteristics characteristics = getCameraCharacteristics(cameraName);
        StreamConfigurationMap streamConfigurationMap = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP);
        ci.setSensorOrientation(characteristics.get(SENSOR_ORIENTATION));
        ci.setFaceFrontingCamera(characteristics.get(LENS_FACING) == LENS_FACING_FRONT);
        ci.setCaptureSize(computeBestCaptureSize(streamConfigurationMap));

        return computePreviewAndVideoSize(ci, streamConfigurationMap);
    }

    private CameraInformation computePreviewAndVideoSize(final CameraInformation ci,
            final StreamConfigurationMap streamConfigurationMap) {
        Size[] sizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
        float captureSizeRatio = (float) ci.getCaptureSize().getWidth() / ci.getCaptureSize().getHeight();
        List<Size> goodEnough = new ArrayList<>();
        for (Size s : sizes) {
            if ((float) s.getWidth() / s.getHeight() == captureSizeRatio
                    && ci.getMinPreviewSize().getWidth() < s.getWidth()
                    && ci.getMinPreviewSize().getHeight() < s.getHeight()) {
                goodEnough.add(s);
            }
        }
        Collections.sort(goodEnough, new CompareSizesByArea());
        if (goodEnough.isEmpty()) {
            ci.setPreviewSize(sizes[0]);
            ci.setVideoSize(sizes[0]);
            return ci;
        }
        ci.setPreviewSize(goodEnough.get(0));
        ci.setVideoSize(goodEnough.get(0));
        for (int i = goodEnough.size() - 1; i >= 0; i--) {
            if (goodEnough.get(i).getHeight() <= 1080) {
                ci.setVideoSize(goodEnough.get(i));
                break;
            }
        }
        return ci;
    }

    private CameraCharacteristics getCameraCharacteristics(final String cameraName) throws CameraAccessException {
        return cameraManager.getCameraCharacteristics(cameraName);
    }

    private Size computeBestCaptureSize(StreamConfigurationMap streamConfigurationMap) {
        // For still image captures, we use the largest available size.
        return Collections.max(Arrays.asList(streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)),
                new CompareSizesByArea());
    }

    private Size getResolution(final String resolutionPreset) {
        switch (resolutionPreset) {
            case "high":
                return new Size(1024, 768);
            case "medium":
                return new Size(640, 480);
            case "low":
                return new Size(320, 240);
            default:
                throw new IllegalArgumentException("Unknown preset: " + resolutionPreset);
        }
    }

    static class CameraInformation {

        private int sensorOrientation;
        private boolean isFaceFrontingCamera;
        private Size captureSize;
        private Size minPreviewSize;
        private Size previewSize;
        private Size videoSize;

        public int getSensorOrientation() {
            return sensorOrientation;
        }

        public void setSensorOrientation(final int sensorOrientation) {
            this.sensorOrientation = sensorOrientation;
        }

        public boolean isFaceFrontingCamera() {
            return isFaceFrontingCamera;
        }

        public void setFaceFrontingCamera(final boolean faceFrontingCamera) {
            isFaceFrontingCamera = faceFrontingCamera;
        }

        public Size getCaptureSize() {
            return captureSize;
        }

        public void setCaptureSize(final Size captureSize) {
            this.captureSize = captureSize;
        }

        public Size getPreviewSize() {
            return previewSize;
        }

        public void setPreviewSize(final Size previewSize) {
            this.previewSize = previewSize;
        }

        public Size getVideoSize() {
            return videoSize;
        }

        public void setVideoSize(final Size videoSize) {
            this.videoSize = videoSize;
        }

        public Size getMinPreviewSize() {
            return minPreviewSize;
        }

        public void setMinPreviewSize(final Size minPreviewSize) {
            this.minPreviewSize = minPreviewSize;
        }

        public Integer getOrientationHint(final int rotation) {
            Integer displayOrientation = ORIENTATIONS.get(rotation);
            if (isFaceFrontingCamera()) {
                displayOrientation = -displayOrientation;
            }
            return (displayOrientation + sensorOrientation) % 360;
        }
    }
}
