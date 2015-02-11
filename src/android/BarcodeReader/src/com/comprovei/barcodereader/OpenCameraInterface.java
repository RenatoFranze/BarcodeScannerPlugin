package com.comprovei.barcodereader;

import android.hardware.Camera;
import android.util.Log;

public final class OpenCameraInterface {
	private static final String TAG = OpenCameraInterface.class.getName();
	public static final int NO_REQUESTED_CAMERA = -1;

	private OpenCameraInterface() {
	}
	
	public static Camera open(int cameraId) {
    
		int numCameras = Camera.getNumberOfCameras();
		if (numCameras == 0) {
			Log.w(TAG, "No cameras!");
			return null;
		}

		boolean explicitRequest = cameraId >= 0;

		if (!explicitRequest) {
			int index = 0;
			while (index < numCameras) {
				Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
				Camera.getCameraInfo(index, cameraInfo);
				if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
					break;
				}
				index++;
			}
      
			cameraId = index;
		}

		Camera camera;
		if (cameraId < numCameras) {
			Log.i(TAG, "Opening camera #" + cameraId);
			camera = Camera.open(cameraId);
		} else {
			if (explicitRequest) {
				Log.w(TAG, "Requested camera does not exist: " + cameraId);
				camera = null;
			} else {
				Log.i(TAG, "No camera facing back; returning camera #0");
				camera = Camera.open(0);
			}
		}
    
		return camera;
	}  
}
