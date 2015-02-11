package com.comprovei.barcodereader;

import android.graphics.BitmapFactory;

import com.comprovei.barcodereader.camera.CameraManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.Collection;
import java.util.Map;

public final class BarcodeReaderHandler extends Handler {
	private static final String TAG = BarcodeReaderHandler.class.getSimpleName();

	private final BarcodeReader mActivity;
	private final DecodeThread mDecodeThread;
	private State mState;
	private final CameraManager mCameraManager;

	private enum State {
		PREVIEW,
		SUCCESS,
		DONE
	}

	BarcodeReaderHandler(BarcodeReader activity, Collection<BarcodeFormat> decodeFormats, Map<DecodeHintType,?> baseHints, String characterSet, CameraManager cameraManager) {
		this.mActivity = activity;
		this.mDecodeThread = new DecodeThread(activity, decodeFormats, baseHints, characterSet, new ViewfinderResultPointCallback(activity.getViewfinderView()));
		this.mDecodeThread.start();
		this.mState = State.SUCCESS;

		// Inicia a captura de preview e a decodificação
		this.mCameraManager = cameraManager;
		cameraManager.startDoingPreview();
		restartPreviewAndDecode();
	}

	@Override
	public void handleMessage(Message message) {
		switch (message.what) {
			case R.id.restart_preview:
				restartPreviewAndDecode();
				break;
			case R.id.decode_succeeded:
				this.mState = State.SUCCESS;
				Bundle bundle = message.getData();
				Bitmap barcode = null;
				float scaleFactor = 1.0f;
				if (bundle != null) {
					byte[] compressedBitmap = bundle.getByteArray(DecodeThread.BARCODE_BITMAP);
					if (compressedBitmap != null) {
						barcode = BitmapFactory.decodeByteArray(compressedBitmap, 0, compressedBitmap.length, null);
						barcode = barcode.copy(Bitmap.Config.ARGB_8888, true);
					}
					scaleFactor = bundle.getFloat(DecodeThread.BARCODE_SCALED_FACTOR);          
				}
				this.mActivity.handleDecode((Result) message.obj, barcode, scaleFactor);
				break;
			case R.id.decode_failed:
				// Para acelerar a decodificação, se uma falhar, inicia outra automaticamente
				this.mState = State.PREVIEW;
				this.mCameraManager.requestPreviewFrame(this.mDecodeThread.getHandler(), R.id.decode);
				break;
			case R.id.return_scan_result:
				this.mActivity.setResult(Activity.RESULT_OK, (Intent) message.obj);
				this.mActivity.finish();
				break;
		}
	}

	public void quitSynchronously() {
		this.mState = State.DONE;
		this.mCameraManager.stopDoingPreview();
		Message quit = Message.obtain(this.mDecodeThread.getHandler(), R.id.quit);
		quit.sendToTarget();
		try {
			// Espera meio segundo
			this.mDecodeThread.join(500L);
		} catch (InterruptedException e) {
			Log.w(TAG, e);
		}

		// Remove as mensagens que estavam na fila
		removeMessages(R.id.decode_succeeded);
		removeMessages(R.id.decode_failed);
	}

	private void restartPreviewAndDecode() {
		if (this.mState == State.SUCCESS) {
			this.mState = State.PREVIEW;
			this.mCameraManager.requestPreviewFrame(this.mDecodeThread.getHandler(), R.id.decode);
			this.mActivity.drawViewfinder();
		}
	}
}
