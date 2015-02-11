package com.comprovei.barcodereader.camera;

import java.io.IOException;

import com.comprovei.barcodereader.OpenCameraInterface;
import com.comprovei.barcodereader.PreviewCallback;
import com.google.zxing.PlanarYUVLuminanceSource;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

public final class CameraManager {
	private static final String TAG = CameraManager.class.getSimpleName();
	
	private static final int MIN_FRAME_WIDTH = 240;
	private static final int MIN_FRAME_HEIGHT = 240;
	private static final int MAX_FRAME_WIDTH = 1200; // = 5/8 * 1920
	private static final int MAX_FRAME_HEIGHT = 675;
	
	private final Context mContext;
	private final CameraConfigManager mConfigManager;	
	private Camera mCamera;
	private Rect mFramingRect;
	private Rect mPreviewFramingRect;
	private boolean mInitialized;
	private boolean mPreviewing;
	private int mRequestedCameraId = OpenCameraInterface.NO_REQUESTED_CAMERA;
	private int requestedFramingRectWidth;
	private int requestedFramingRectHeight;
	
	private final PreviewCallback mPreviewCallback;
		
	public CameraManager(Context context) {
		this.mContext = context;
		this.mConfigManager = new CameraConfigManager(this.mContext);
		this.mPreviewCallback = new PreviewCallback(this.mConfigManager);
	}
	
	public synchronized void openCamera(SurfaceHolder holder) throws IOException{
		Camera camera = this.mCamera;
		
		if(camera == null){
			camera = OpenCameraInterface.open(mRequestedCameraId);
			if(camera == null){
				throw new IOException();
			}
			this.mCamera = camera;
		}
		camera.setPreviewDisplay(holder);		
		
		if(!mInitialized){
			mInitialized = true;
			this.mConfigManager.initFromCameraParameters(camera);
			if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
		        setManualFramingRect(requestedFramingRectWidth, requestedFramingRectHeight);
		        requestedFramingRectWidth = 0;
		        requestedFramingRectHeight = 0;
		    }
		}
		
		Camera.Parameters param = camera.getParameters();
		String paramFlatten = param == null ? null : param.flatten();
		
		try{
			this.mConfigManager.setDesiredCameraParameters(camera, false);
		}catch (RuntimeException re){
			// Controlador da camera falhou
		    Log.w(TAG, "Camera rejeitou os parametros. Setando os parametros de modo de segurança");
		    Log.i(TAG, "Resetando para os parametros antigos: " + paramFlatten);	
		    
		    if(paramFlatten != null){
		    	param = camera.getParameters();
		    	param.unflatten(paramFlatten);
		    	try{
		    		camera.setParameters(param);
		    		this.mConfigManager.setDesiredCameraParameters(camera, true);
		    	}catch(RuntimeException re2){
		    		Log.w(TAG, "Camera rejeitou os parametros de modo seguro! Sem configuração disponível.");
		    	}
		    }
		}
	}
	
	public synchronized boolean isOpen(){
		return this.mCamera != null;
	}
	
	public synchronized void closeCamera(){
		if(this.mCamera != null){
			this.mCamera.release();
			this.mCamera = null;
			this.mFramingRect = null;
			this.mPreviewFramingRect = null;
		}
	}

	public synchronized void startDoingPreview(){
		Camera camera = this.mCamera;
		if(camera != null && !mPreviewing){
			mPreviewing = true;
			camera.startPreview();			
		}
	}
	
	public synchronized void stopDoingPreview(){
		if(this.mCamera != null && this.mPreviewing){
			this.mCamera.stopPreview();
			this.mPreviewCallback.setHandler(null, 0);
			this.mPreviewing = false;
		}
	}
	
	public synchronized void requestPreviewFrame(Handler handler, int message){
		Camera camera = this.mCamera;
		if(camera != null && this.mPreviewing){
			this.mPreviewCallback.setHandler(handler, message);
			camera.setOneShotPreviewCallback(this.mPreviewCallback);
		}
	}

	public synchronized Rect getFramingRect(){
		if(this.mFramingRect == null){
			if(this.mCamera == null){
				return null;
			}
			Point screenResolution = this.mConfigManager.getScreenResolution();
			
			if(screenResolution == null){
				return null;
			}
			
			int width = findDesiredDimension(screenResolution.x, MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
			int height = findDesiredDimension(screenResolution.y, MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);
			
			// Criando uma margem
			int leftOffset = (screenResolution.x - width) / 2;
			int topOffset = (screenResolution.y - height) / 2;
			this.mFramingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
			Log.d(TAG, "Enquadramento calculado: " + this.mFramingRect);
		}
		return this.mFramingRect;
	}
	
	private static int findDesiredDimension(int resolution, int minimum, int maximum){
		int dimension = 5 * resolution / 8; // Make a 5:8 frame
		if(dimension < minimum){
			return minimum;
		}
		if(dimension > maximum){
			return maximum;
		}
		
		return dimension;
	}
	
	
	public synchronized Rect getFramingRectInPreview(){
		if(this.mPreviewFramingRect == null){
			if(getFramingRect() == null){
				return null;
			}
			Rect rect = new Rect(getFramingRect());
			Point cameraResolution = this.mConfigManager.getCameraResolution();
			Point screenResolution = this.mConfigManager.getScreenResolution();
			if(cameraResolution == null || screenResolution == null){
				return null;
			}
			
			int widthRatio = cameraResolution.x / screenResolution.x;
			int heightRation = cameraResolution.y / screenResolution.y;
			
			rect.left *= widthRatio;
			rect.right *= widthRatio;
			rect.top *= heightRation;
			rect.bottom *= heightRation;
			this.mPreviewFramingRect = rect;
		}		
		return this.mPreviewFramingRect;
	}
	
	public synchronized void setManualCameraId(int cameraId) {
	    this.mRequestedCameraId = cameraId;
	}
	
	public synchronized void setManualFramingRect(int width, int height) {
	    if (this.mInitialized) {
	    	Point screenResolution = this.mConfigManager.getScreenResolution();
	    	if (width > screenResolution.x) {
	    		width = screenResolution.x;
	    	}
	    	if (height > screenResolution.y) {
	    		height = screenResolution.y;
	    	}
	    	int leftOffset = (screenResolution.x - width) / 2;
	    	int topOffset = (screenResolution.y - height) / 2;
	    	this.mFramingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
	    	Log.d(TAG, "Enquadramento manual calculado: " + this.mFramingRect);
	      this.mPreviewFramingRect = null;
	    } else {
	    	requestedFramingRectWidth = width;
	    	requestedFramingRectHeight = height;
	    }
	}
	
	public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
	    Rect rect = getFramingRectInPreview();
	    if (rect == null) {
	      return null;
	    }
	    return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
	                                        rect.width(), rect.height(), false);
	}		
}
