package com.comprovei.barcodereader;

import com.comprovei.barcodereader.camera.CameraManager;
import com.google.zxing.ResultPoint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public final class ViewfinderView extends View {
	private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
	private static final long ANIMATION_DELAY = 80L;
	private static final int CURRENT_POINT_OPACITY = 0xA0;
	private static final int MAX_RESULT_POINTS = 20;
	private static final int POINT_SIZE = 6;

	private CameraManager mCameraManager;
	private final Paint mPaint;
	private Bitmap mResultBitmap;
	private final int mMaskColor;
	private final int mResultColor;
	private final int mLaserColor;
	private final int mResultPointColor;
	private int mScannerAlpha;
	private List<ResultPoint> mPossibleResultPoints;
	private List<ResultPoint> mLastPossibleResultPoints;

	public ViewfinderView(Context context, AttributeSet attrs) {
		super(context, attrs);

		this.mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		Resources resources = getResources();
		this.mMaskColor = resources.getColor(R.color.viewfinder_mask);
		this.mResultColor = resources.getColor(R.color.result_view);
		this.mLaserColor = resources.getColor(R.color.viewfinder_laser);
		this.mResultPointColor = resources.getColor(R.color.possible_result_points);
		this.mScannerAlpha = 0;
		this.mPossibleResultPoints = new ArrayList<>(5);
		this.mLastPossibleResultPoints = null;
	}

	public void setCameraManager(CameraManager cameraManager) {
		this.mCameraManager = cameraManager;
	}

	@SuppressLint("DrawAllocation")
	@Override
	public void onDraw(Canvas canvas) {
		if (this.mCameraManager == null) {
			return; 
		}
		Rect frame = this.mCameraManager.getFramingRect();
		Rect previewFrame = this.mCameraManager.getFramingRectInPreview();    
		if (frame == null || previewFrame == null) {
			return;
		}
		int width = canvas.getWidth();
		int height = canvas.getHeight();

		// Desenha o exterior escurecido do quadro de visualização
		this.mPaint.setColor(this.mResultBitmap != null ? this.mResultColor : this.mMaskColor);
		canvas.drawRect(0, 0, width, frame.top, this.mPaint);
		canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, this.mPaint);
		canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, this.mPaint);
		canvas.drawRect(0, frame.bottom + 1, width, height, this.mPaint);

		if (this.mResultBitmap != null) {
			// Desenha a imagem opaca do resultado sobre o retangulo de visualização (scanner)
			this.mPaint.setAlpha(CURRENT_POINT_OPACITY);
			canvas.drawBitmap(mResultBitmap, null, frame, this.mPaint);
		} else {
			// Desenha a linha vermelha do scanner no meio do quadro de visualização
			this.mPaint.setColor(this.mLaserColor);
			this.mPaint.setAlpha(SCANNER_ALPHA[this.mScannerAlpha]);
			this.mScannerAlpha = (this.mScannerAlpha + 1) % SCANNER_ALPHA.length;
			int middle = frame.height() / 2 + frame.top;
			canvas.drawRect(frame.left + 2, middle - 1, frame.right - 1, middle + 2, this.mPaint);
      
			float scaleX = frame.width() / (float) previewFrame.width();
			float scaleY = frame.height() / (float) previewFrame.height();

			List<ResultPoint> currentPossible = this.mPossibleResultPoints;
			List<ResultPoint> currentLast = this.mLastPossibleResultPoints;
			int frameLeft = frame.left;
			int frameTop = frame.top;
			if (currentPossible.isEmpty()) {
				this.mLastPossibleResultPoints = null;
			} else {
				this.mPossibleResultPoints = new ArrayList<>(5);
				this.mLastPossibleResultPoints = currentPossible;
				this.mPaint.setAlpha(CURRENT_POINT_OPACITY);
				this.mPaint.setColor(this.mResultPointColor);
				synchronized (currentPossible) {
					for (ResultPoint point : currentPossible) {
					  canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                              frameTop + (int) (point.getY() * scaleY),
                              POINT_SIZE, this.mPaint);
					}
				}
			}
			if (currentLast != null) {
				this.mPaint.setAlpha(CURRENT_POINT_OPACITY / 2);
				this.mPaint.setColor(mResultPointColor);
				synchronized (currentLast) {
					float radius = POINT_SIZE / 2.0f;
					for (ResultPoint point : currentLast) {
						canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                              frameTop + (int) (point.getY() * scaleY),
                              radius, this.mPaint);
					}
				}
			}

			postInvalidateDelayed(ANIMATION_DELAY,
                            frame.left - POINT_SIZE,
                            frame.top - POINT_SIZE,
                            frame.right + POINT_SIZE,
                            frame.bottom + POINT_SIZE);
		}
	}

	public void drawViewfinder() {
		Bitmap resultBitmap = this.mResultBitmap;
		this.mResultBitmap = null;
		if (resultBitmap != null) {
			resultBitmap.recycle();
		}
		invalidate();
	}

	/**
	 * Desenha uma imagem com os pontos do resultado destacado ao inves do scanner.
	 *
	 * @param barcode Imagem do codigo de barras decodificado
   	*/
	public void drawResultBitmap(Bitmap barcode) {
		this.mResultBitmap = barcode;
		invalidate();
	}

	public void addPossibleResultPoint(ResultPoint point) {
		List<ResultPoint> points = mPossibleResultPoints;
		synchronized (points) {
			points.add(point);
			int size = points.size();
			if (size > MAX_RESULT_POINTS) {
				points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
			}
		}
	}
}
