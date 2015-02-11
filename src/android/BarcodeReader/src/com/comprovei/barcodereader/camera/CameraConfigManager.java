package com.comprovei.barcodereader.camera;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

public final class CameraConfigManager {	
	private static final String TAG = CameraConfigManager.class.getSimpleName();

	private final Context context;
	private Point screenResolution;
	private Point cameraResolution;
	
	public CameraConfigManager(Context context) {
		this.context = context;
	}
	
	void initFromCameraParameters(Camera camera) {
	    Camera.Parameters parameters = camera.getParameters();
	    WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
	    Display display = manager.getDefaultDisplay();
	    Point theScreenResolution = new Point();
	    display.getSize(theScreenResolution);
	    this.screenResolution = theScreenResolution;
	    Log.i(TAG, "Screen resolution: " + this.screenResolution);
	    this.cameraResolution = CameraConfigUtils.findBestPreviewSizeValue(parameters, this.screenResolution);
	    Log.i(TAG, "Camera resolution: " + this.cameraResolution);
	}		
	
	void setDesiredCameraParameters(Camera camera, boolean safeMode) {
	    Camera.Parameters parameters = camera.getParameters();

	    if (parameters == null) {
	    	Log.w(TAG, "Erro no dispositivo: nenhum parametro de camera disponível. Prosseguindo sem configuração.");
	    	return;
	    }

	    Log.i(TAG, "Parametros inicias da camera: " + parameters.flatten());

	    if (safeMode) {
	    	Log.w(TAG, "Configuração da camera em modo de segurança -- maioria das opções serão ignoradas");
	    }	     

	    CameraConfigUtils.setFocus(parameters, true, false, safeMode);
	    
	    if (!safeMode) {
	    	CameraConfigUtils.setBarcodeSceneMode(parameters);
	    	CameraConfigUtils.setVideoStabilization(parameters);
	    	CameraConfigUtils.setFocusArea(parameters);
	    	CameraConfigUtils.setMetering(parameters);
	    }
	    
	    parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);

	    Log.i(TAG, "Parametros finais da camera: " + parameters.flatten());

	    camera.setParameters(parameters);

	    Camera.Parameters afterParameters = camera.getParameters();
	    Camera.Size afterSize = afterParameters.getPreviewSize();
	    if (afterSize!= null && (cameraResolution.x != afterSize.width || cameraResolution.y != afterSize.height)) {
	    	Log.w(TAG, "A camera disse que suportava o seguinte tamanho de visualização " + cameraResolution.x + 'x' + cameraResolution.y +
	                 	", mas apos setar esse tamanho, o tamanho da visualização ficou assim " + afterSize.width + 'x' + afterSize.height);
	    	cameraResolution.x = afterSize.width;
	    	cameraResolution.y = afterSize.height;
	    }
	}

	public Point getCameraResolution() {
	    return this.cameraResolution;
	}

	public Point getScreenResolution() {
	    return this.screenResolution;
	}
}
