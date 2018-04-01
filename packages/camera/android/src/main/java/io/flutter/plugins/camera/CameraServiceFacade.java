package io.flutter.plugins.camera;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.PluginRegistry;

class CameraServiceFacade {

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

    CameraCharacteristics getCameraCharacteristics(final String cameraName) throws CameraAccessException {
        return cameraManager.getCameraCharacteristics(cameraName);
    }

    void openCamera(final String cameraName, final CameraDevice.StateCallback stateCallback)
            throws CameraAccessException {
        cameraManager.openCamera(cameraName, stateCallback, null);
    }
}
