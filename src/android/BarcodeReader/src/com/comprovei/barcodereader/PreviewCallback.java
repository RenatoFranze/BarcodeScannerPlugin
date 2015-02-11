package com.comprovei.barcodereader;

import com.comprovei.barcodereader.camera.CameraConfigManager;

import android.graphics.Point;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public final class PreviewCallback implements Camera.PreviewCallback {
	private static final String TAG = PreviewCallback.class.getSimpleName();

	private final CameraConfigManager mConfigManager;
	private Handler mPreviewHandler;
	private int mPreviewMessage;

	public PreviewCallback(CameraConfigManager configManager) {
		this.mConfigManager = configManager;
	}

	public void setHandler(Handler previewHandler, int previewMessage) {
		this.mPreviewHandler = previewHandler;
		this.mPreviewMessage = previewMessage;
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		Point cameraResolution = this.mConfigManager.getCameraResolution();
		Handler thePreviewHandler = this.mPreviewHandler;
		if (cameraResolution != null && thePreviewHandler != null) {
			Message message = thePreviewHandler.obtainMessage(this.mPreviewMessage, cameraResolution.x, cameraResolution.y, data);
			message.sendToTarget();
			this.mPreviewHandler = null;
		} else {
			Log.d(TAG, "Callbakc definido, mas sem handler ou resolução!");
		}
	}
}
