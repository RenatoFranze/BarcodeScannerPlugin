package com.comprovei.barcodereader;

import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;

final class ViewfinderResultPointCallback implements ResultPointCallback {
	private final ViewfinderView mViewfinderView;

	ViewfinderResultPointCallback(ViewfinderView viewfinderView) {
		this.mViewfinderView = viewfinderView;
	}

	@Override
	public void foundPossibleResultPoint(ResultPoint point) {
		this.mViewfinderView.addPossibleResultPoint(point);
	}
}
