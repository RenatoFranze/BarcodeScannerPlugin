package com.comprovei.barcodereader.camera;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.Log;

public final class CameraConfigUtils {	
	private static final String TAG = CameraConfigUtils.class.getSimpleName();
	private static final int MIN_PREVIEW_PIXELS = 480 * 320;
	private static final int MIN_FPS = 10;
	private static final int MAX_FPS = 20;
	private static final float MAX_EXPOSURE_COMPENSATION = 1.5f;
	private static final float MIN_EXPOSURE_COMPENSATION = 0.0f;
	private static final double MAX_ASPECT_DISTORTION = 0.10;
	private static final int AREA_PER_1000 = 400;
	
	private CameraConfigUtils() {
	}
	
	public static void setFocus(Camera.Parameters parameters, boolean autoFocus, boolean disableContinuous, boolean safeMode) {
		List<String> supportedFocusModes = parameters.getSupportedFocusModes();
		String focusMode = null;
		if (autoFocus) {
			if (safeMode || disableContinuous) {
				focusMode = findSettableValue("focus mode", 
											 supportedFocusModes, 
											 Camera.Parameters.FOCUS_MODE_AUTO);
			} else {
				focusMode = findSettableValue("focus mode", 
											 supportedFocusModes, 
											 Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
											 Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
											 Camera.Parameters.FOCUS_MODE_AUTO);
			}
		}
		// Maybe selected auto-focus but not available, so fall through here:
		if (!safeMode && focusMode == null) {
			focusMode = findSettableValue("focus mode",
		                  				 supportedFocusModes,
		                  				 Camera.Parameters.FOCUS_MODE_MACRO,
		                  				 Camera.Parameters.FOCUS_MODE_EDOF);
		}
		if (focusMode != null) {
			if (focusMode.equals(parameters.getFocusMode())) {
				Log.i(TAG, "Modo do focus já está setado como " + focusMode);
			} else {
				parameters.setFocusMode(focusMode);
			}
		}
	}
	
	public static void setTorch(Camera.Parameters parameters, boolean on) {
	    List<String> supportedFlashModes = parameters.getSupportedFlashModes();
	    String flashMode;
	    if (on) {
	      flashMode = findSettableValue("flash mode",
	                                    supportedFlashModes,
	                                    Camera.Parameters.FLASH_MODE_TORCH,
	                                    Camera.Parameters.FLASH_MODE_ON);
	    } else {
	      flashMode = findSettableValue("flash mode",
	                                    supportedFlashModes,
	                                    Camera.Parameters.FLASH_MODE_OFF);
	    }
	    if (flashMode != null) {
	    	if (flashMode.equals(parameters.getFlashMode())) {
	    		Log.i(TAG, "Flash já setado como " + flashMode);
	    	} else {
	    		Log.i(TAG, "Mudando flash para " + flashMode);
	    		parameters.setFlashMode(flashMode);
	    	}
	    }
	}
	
	public static void setBestExposure(Camera.Parameters parameters, boolean lightOn){
		int minExposure = parameters.getMinExposureCompensation();
	    int maxExposure = parameters.getMaxExposureCompensation();
	    float step = parameters.getExposureCompensationStep();
	    if ((minExposure != 0 || maxExposure != 0) && step > 0.0f) {
	    	float targetCompensation = lightOn ? MIN_EXPOSURE_COMPENSATION : MAX_EXPOSURE_COMPENSATION;
	    	int compensationSteps = Math.round(targetCompensation / step);
	    	float actualCompensation = step * compensationSteps;
	    	// Clamp value:
	    	compensationSteps = Math.max(Math.min(compensationSteps, maxExposure), minExposure);
	    	if (parameters.getExposureCompensation() == compensationSteps) {
	    		Log.i(TAG, "Compensação da exposição já setada para " + compensationSteps + " / " + actualCompensation);
	    	} else {
	    		Log.i(TAG, "Setando compensação da exposição para " + compensationSteps + " / " + actualCompensation);
	    		parameters.setExposureCompensation(compensationSteps);
	    	}
	    } else {
	      Log.i(TAG, "Dispositivo não suporta compensação da exposição");
	    }
	}
	
	public static void setBarcodeSceneMode(Camera.Parameters parameters) {
		if (Camera.Parameters.SCENE_MODE_BARCODE.equals(parameters.getSceneMode())) {
			Log.i(TAG, "Modo de codigo de barras já setado");
			return;
	    }
	    String sceneMode = findSettableValue("scene mode",
	                                         parameters.getSupportedSceneModes(),
	                                         Camera.Parameters.SCENE_MODE_BARCODE);
	    if (sceneMode != null) {
	    	parameters.setSceneMode(sceneMode);
	    }
	}

	public static void setVideoStabilization(Camera.Parameters parameters) {
		if (parameters.isVideoStabilizationSupported()) {
			if (parameters.getVideoStabilization()) {
				Log.i(TAG, "Estabilização do video já está habilitado");
			} else {
				Log.i(TAG, "Ativando estabilização do video...");
				parameters.setVideoStabilization(true);
			}
		} else {
			Log.i(TAG, "Dispositivo não suporta estabilização do video");
		}
	}

	public static void setFocusArea(Camera.Parameters parameters) {
		if (parameters.getMaxNumFocusAreas() > 0) {
			Log.i(TAG, "Antigas areas de focus: " + toString(parameters.getFocusAreas()));
			List<Camera.Area> middleArea = buildMiddleArea(AREA_PER_1000);
			Log.i(TAG, "Setando area de focus para: " + toString(middleArea));
			parameters.setFocusAreas(middleArea);
		} else {
			Log.i(TAG, "Dispositivo não suporta areas de focus");
		}
	}
	
	public static void setMetering(Camera.Parameters parameters) {
		if (parameters.getMaxNumMeteringAreas() > 0) {
			Log.i(TAG, "Antigas areas de medição: " + parameters.getMeteringAreas());
			List<Camera.Area> middleArea = buildMiddleArea(AREA_PER_1000);
			Log.i(TAG, "Setando novas areas de medição : " + toString(middleArea));
			parameters.setMeteringAreas(middleArea);
		} else {
			Log.i(TAG, "Dispositivo não suporta areas de medição");
		}
	}
	
	public static void setBestPreviewFPS(Camera.Parameters parameters) {
	    setBestPreviewFPS(parameters, MIN_FPS, MAX_FPS);
	}
	
	public static void setBestPreviewFPS(Camera.Parameters parameters, int minFPS, int maxFPS) {
		List<int[]> supportedPreviewFpsRanges = parameters.getSupportedPreviewFpsRange();
	    Log.i(TAG, "Intervalos de QPS suportados: " + toString(supportedPreviewFpsRanges));
	    if (supportedPreviewFpsRanges != null && !supportedPreviewFpsRanges.isEmpty()) {
	    	int[] suitableFPSRange = null;
	    	for (int[] fpsRange : supportedPreviewFpsRanges) {
	    		int thisMin = fpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
	    		int thisMax = fpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
	    		if (thisMin >= minFPS * 1000 && thisMax <= maxFPS * 1000) {
	    			suitableFPSRange = fpsRange;
	    			break;
	    		}
	    	}
	    	if (suitableFPSRange == null) {
	    		Log.i(TAG, "Sem intervalo de QPS adequado?");
	    	} else {
	    		int[] currentFpsRange = new int[2];
	    		parameters.getPreviewFpsRange(currentFpsRange);
	    		if (Arrays.equals(currentFpsRange, suitableFPSRange)) {
	    			Log.i(TAG, "Intervalo de QPS já setado para " + Arrays.toString(suitableFPSRange));
	    		} else {
	    			Log.i(TAG, "Setando intervalo de QPS para " + Arrays.toString(suitableFPSRange));
	    			parameters.setPreviewFpsRange(suitableFPSRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
	                                        suitableFPSRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
	    		}
	    	}
	    }
	}

	public static Point findBestPreviewSizeValue(Camera.Parameters parameters, Point screenResolution) {
	    List<Camera.Size> rawSupportedSizes = parameters.getSupportedPreviewSizes();
	    if (rawSupportedSizes == null) {
	    	Log.w(TAG, "Dispositivo retornou que não suporta tamanhos de visualização; usando o padrão");
	    	Camera.Size defaultSize = parameters.getPreviewSize();
	    	if (defaultSize == null) {
	    		throw new IllegalStateException("Parametros não contem tamanhdo de visualização!");
	    	}
	    	return new Point(defaultSize.width, defaultSize.height);
	    }

	    // Ordena, de forma descendente, por tamanho
	    List<Camera.Size> supportedPreviewSizes = new ArrayList<>(rawSupportedSizes);
	    Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>() {
	    	@Override
	    	public int compare(Camera.Size a, Camera.Size b) {
	    		int aPixels = a.height * a.width;
	    		int bPixels = b.height * b.width;
	    		if (bPixels < aPixels) {
	    			return -1;
	    		}
	    		if (bPixels > aPixels) {
	    			return 1;
	    		}
	    		return 0;
	    	}
	    });

	    if (Log.isLoggable(TAG, Log.INFO)) {
	    	StringBuilder previewSizesString = new StringBuilder();
	    	for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
	    		previewSizesString.append(supportedPreviewSize.width).append('x')
	            	.append(supportedPreviewSize.height).append(' ');
	    	}
	    	Log.i(TAG, "Tamanhos de visualização suportados: " + previewSizesString);
	    }

	    double screenAspectRatio = (double) screenResolution.x / (double) screenResolution.y;

	    // Remove os tamanhos que não são adequados
	    Iterator<Camera.Size> it = supportedPreviewSizes.iterator();
	    while (it.hasNext()) {
	    	Camera.Size supportedPreviewSize = it.next();
	    	int realWidth = supportedPreviewSize.width;
	    	int realHeight = supportedPreviewSize.height;
	    	if (realWidth * realHeight < MIN_PREVIEW_PIXELS) {
	    		it.remove();
	    		continue;
	    	}

	    	boolean isCandidatePortrait = realWidth < realHeight;
	    	int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
	    	int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;
	    	double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
	    	double distortion = Math.abs(aspectRatio - screenAspectRatio);
	    	if (distortion > MAX_ASPECT_DISTORTION) {
	    		it.remove();
	    		continue;
	    	}

	    	if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
	    		Point exactPoint = new Point(realWidth, realHeight);
	    		Log.i(TAG, "Encontrado tamanho de visualização que combina corretamente com o tamanho da tela: " + exactPoint);
	    		return exactPoint;
	    	}
	    }

	    // Se não tiver uma combinação exata, usa o maior tamanho disponível
	    if (!supportedPreviewSizes.isEmpty()) {
	    	Camera.Size largestPreview = supportedPreviewSizes.get(0);
	    	Point largestSize = new Point(largestPreview.width, largestPreview.height);
	    	Log.i(TAG, "Usando o tamanho mais adequado para a visualização: " + largestSize);
	    	return largestSize;
	    }

	    // Se nenhum tamanho for adequado, retorna o tamanho padrão
	    Camera.Size defaultPreview = parameters.getPreviewSize();
	    if (defaultPreview == null) {
	    	throw new IllegalStateException("Parametros não contem tamanhdo de visualização!");
	    }
	    Point defaultSize = new Point(defaultPreview.width, defaultPreview.height);
	    Log.i(TAG, "Sem tamanho adequado para a visualização, usando o padrão: " + defaultSize);
	    return defaultSize;
	}
	
	private static String findSettableValue(String name, Collection<String> supportedValues, String... desiredValues) {
		Log.i(TAG, "Requisitando valores de " + name + ": " + Arrays.toString(desiredValues));
		Log.i(TAG, "Valores de " + name + " suportados: " + supportedValues);
		if (supportedValues != null) {
			for (String desiredValue : desiredValues) {
				if (supportedValues.contains(desiredValue)) {
					Log.i(TAG, "Podemos utilizar o seguinte valor de" + name + ": " + desiredValue);
					return desiredValue;
				}
			}
		}
		Log.i(TAG, "Nenhum valor suportado encontrado!");
		return null;
	}
	
	private static List<Camera.Area> buildMiddleArea(int areaPer1000) {
		return Collections.singletonList(new Camera.Area(new Rect(-areaPer1000, -areaPer1000, areaPer1000, areaPer1000), 1));
	}
	
	private static String toString(Collection<int[]> arrays) {
	    if (arrays == null || arrays.isEmpty()) {
	    	return "[]";
	    }
	    StringBuilder buffer = new StringBuilder();
	    buffer.append('[');
	    Iterator<int[]> it = arrays.iterator();
	    while (it.hasNext()) {
	    	buffer.append(Arrays.toString(it.next()));
	    	if (it.hasNext()) {
	    		buffer.append(", ");
	    	}
	    }
	    buffer.append(']');
	    return buffer.toString();
	}
	
	private static String toString(Iterable<Camera.Area> areas) {
	    if (areas == null) {
	    	return null;
	    }
	    StringBuilder result = new StringBuilder();
	    for (Camera.Area area : areas) {
	    	result.append(area.rect).append(':').append(area.weight).append(' ');
	    }
	    return result.toString();
	}
}
