package com.comprovei.barcodereader;

import android.graphics.Bitmap;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.Map;

final class DecodeHandler extends Handler {	
	private static final String TAG = DecodeHandler.class.getSimpleName();

	private final BarcodeReader mActivity;
	private final MultiFormatReader mMultiFormatReader;
	private boolean mRunning = true;

	DecodeHandler(BarcodeReader activity, Map<DecodeHintType,Object> hints) {
		this.mMultiFormatReader = new MultiFormatReader();
		this.mMultiFormatReader.setHints(hints);
		this.mActivity = activity;
	}

	@Override
	public void handleMessage(Message message) {
		if (!this.mRunning) {
			return;
		}
		switch (message.what) {
			case R.id.decode:
				decode((byte[]) message.obj, message.arg1, message.arg2);
				break;
			case R.id.quit:
				this.mRunning = false;
				Looper.myLooper().quit();
				break;
		}
	}

	/**
	 * Decodifica o dado dentro do quadro de visualização e verifica quanto tempo levou. Para melhorar a eficiencia,
	 * reusa os mesmos cabeçalhos de uma decodificação para a outra.
	 *
	 * @param data   O YUV do quadro de visualização.
	 * @param width  Largura do quadro de visualização.
	 * @param height Altura do quadro de visualização.
	 */
	private void decode(byte[] data, int width, int height) {
		long start = System.currentTimeMillis();
		Result rawResult = null;
		PlanarYUVLuminanceSource source = this.mActivity.getCameraManager().buildLuminanceSource(data, width, height);
		if (source != null) {
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
			try {
				rawResult = this.mMultiFormatReader.decodeWithState(bitmap);
			} catch (ReaderException re) {
				// continue
			} finally {
				this.mMultiFormatReader.reset();
			}
		}

		Handler handler = this.mActivity.getHandler();
		if (rawResult != null) {
			long end = System.currentTimeMillis();
			Log.d(TAG, "Codigo de barras lido em " + (end - start) + " ms");
			if (handler != null) {
				Message message = Message.obtain(handler, R.id.decode_succeeded, rawResult);
				Bundle bundle = new Bundle();
				bundleThumbnail(source, bundle);        
				message.setData(bundle);
				message.sendToTarget();
			}
		} else {
			if (handler != null) {
				Message message = Message.obtain(handler, R.id.decode_failed);
				message.sendToTarget();
			}
		}
	}

	private static void bundleThumbnail(PlanarYUVLuminanceSource source, Bundle bundle) {
		int[] pixels = source.renderThumbnail();
		int width = source.getThumbnailWidth();
		int height = source.getThumbnailHeight();
		Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
		ByteArrayOutputStream out = new ByteArrayOutputStream();    
		bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
		bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
		bundle.putFloat(DecodeThread.BARCODE_SCALED_FACTOR, (float) width / source.getWidth());
	}
}
