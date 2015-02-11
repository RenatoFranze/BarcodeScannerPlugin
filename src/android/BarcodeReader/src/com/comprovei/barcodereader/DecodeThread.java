package com.comprovei.barcodereader;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

final class DecodeThread extends Thread {
	private static final String TAG = DecodeThread.class.getSimpleName();
	public static final String BARCODE_BITMAP = "barcode_bitmap";
	public static final String BARCODE_SCALED_FACTOR = "barcode_scaled_factor";

	private final BarcodeReader mActivity;
	private final Map<DecodeHintType,Object> mHints;
	private Handler mHandler;
	private final CountDownLatch mHandlerInitLatch;

	DecodeThread(BarcodeReader activity, Collection<BarcodeFormat> decodeFormats, Map<DecodeHintType,?> baseHints, String characterSet, ResultPointCallback resultPointCallback) {
		this.mActivity = activity;
		this.mHandlerInitLatch = new CountDownLatch(1);

		this.mHints = new EnumMap<>(DecodeHintType.class);
		if (baseHints != null) {
			this.mHints.putAll(baseHints);
		}

		if (decodeFormats == null || decodeFormats.isEmpty()) {
			decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
			decodeFormats.addAll(DecodeFormatManager.PRODUCT_FORMATS);
			decodeFormats.addAll(DecodeFormatManager.INDUSTRIAL_FORMATS);
			decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
			decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
			decodeFormats.addAll(DecodeFormatManager.AZTEC_FORMATS);
			decodeFormats.addAll(DecodeFormatManager.PDF417_FORMATS);
		}
		
		this.mHints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);

		if (characterSet != null) {
			this.mHints.put(DecodeHintType.CHARACTER_SET, characterSet);
		}
		this.mHints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
		Log.i(TAG, "Hints: " + mHints);
	}

	Handler getHandler() {
		try {
			this.mHandlerInitLatch.await();
		} catch (InterruptedException ie) {
			// continue?
		}
		return mHandler;
	}

	@Override
	public void run() {
		Looper.prepare();
		this.mHandler = new DecodeHandler(mActivity, mHints);
		this.mHandlerInitLatch.countDown();
		Looper.loop();
	}
}
