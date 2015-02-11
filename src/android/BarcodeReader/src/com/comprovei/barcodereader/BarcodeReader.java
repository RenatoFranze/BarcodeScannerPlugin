package com.comprovei.barcodereader;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.comprovei.barcodereader.R;
import com.comprovei.barcodereader.camera.CameraManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

class MutableInt implements Serializable{
	int mValue = 1; // Começa em 1
	public void increment () { ++this.mValue;      }
	public int  get ()       { return this.mValue; }
}

public class BarcodeReader extends Activity implements SurfaceHolder.Callback{	
	private static final String TAG = BarcodeReader.class.getSimpleName();
	private static final long DEFAULT_INTENT_RESULT_DURATION_MS = 1500L;
	private static final long BULK_MODE_SCAN_DELAY_MS = 1000L;
	
	// Controlar se o aplicativo foi chamado nativamente ou por Intent
	private enum IntentSource{
		NATIVE_APP_INTENT,
		NONE
	}
	    
    private CameraManager mCameraManager;
    private BarcodeReaderHandler mHandler;
    private Result mSavedResultToShow;
    private ViewfinderView mViewFinderView;
    private TextView mStatusView;
    private Result mLastResult;
    private IntentSource mSource;
    private boolean mHasSurface;
    private Collection<BarcodeFormat> mDecodeFormats;
    private Map<DecodeHintType,?> mDecodeHints;
    private String mCharacterSet;
    private BeepManager mBeepManager;
    private boolean mBulkMode = false;
    private HashMap<String, MutableInt> mBulkReading;
    
    ViewfinderView getViewfinderView() {
        return this.mViewFinderView;
    }
    
    public Handler getHandler() {
        return this.mHandler;
    }

    CameraManager getCameraManager() {
    	return this.mCameraManager;
    }

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.main);
		
		this.mBeepManager = new BeepManager(this);
	}
	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		this.mCameraManager = new CameraManager(getApplication());
        this.mViewFinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        this.mViewFinderView.setCameraManager(this.mCameraManager);
        
        this.mStatusView = (TextView) findViewById(R.id.status_view);
        
        this.mHandler = null;
        this.mLastResult = null;
        
        resetStatusView();
        
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        
        if(this.mHasSurface){
        	initCamera(surfaceHolder);
        } else {
        	surfaceHolder.addCallback(this);
        }
        
        this.mBeepManager.updatePrefs();
        
        Intent intent = getIntent();
        
        this.mSource = IntentSource.NONE;
        this.mDecodeFormats = null;
        this.mCharacterSet = null;
        
        if(intent != null){
        	String action = intent.getAction();
        	Log.i(TAG, "Ação enviada: " + action);
        	if(Intents.Scan.ACTION.equals(action)){
        		Log.i(TAG, "Ação igual");
        		this.mSource = IntentSource.NATIVE_APP_INTENT;
        		this.mBulkMode = intent.getBooleanExtra("BULK_MODE", false);
        		
        		if(this.mBulkMode) this.mBulkReading = new HashMap<String, MutableInt>();
        		
        		String customPromptMessage = intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE);
        		if(customPromptMessage != null){
        			this.mStatusView.setText(customPromptMessage);
        		}
        	}else{
        		Log.i(TAG, "Ação não foi igual. Ação da classe: " + Intents.Scan.ACTION);
        	}
        } else {
        	Log.i(TAG, "Intent nulo");
        }
	}
	
	private int getCurrentOrientation() {
		int rotation = getWindowManager().getDefaultDisplay().getRotation();
	    switch (rotation) {
	    case Surface.ROTATION_0:
	    case Surface.ROTATION_90:
	    	return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
	    default:
	    	return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
    	}
	}
	
	@Override
    protected void onPause() {
        if (this.mHandler != null) {
        	this.mHandler.quitSynchronously();
        	this.mHandler = null;
        }
        this.mBeepManager.close();
        this.mCameraManager.closeCamera();
        if (!this.mHasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
		
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch(keyCode){
		case KeyEvent.KEYCODE_BACK:
			if(this.mSource == IntentSource.NATIVE_APP_INTENT){
				if(this.mBulkMode){
					if(this.mBulkReading.isEmpty()){
						setResult(RESULT_CANCELED);	
						finish();
						return true;						
					}else{
						Intent intent = new Intent(getIntent().getAction());
						intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
						
						String contents = "";
						for(String key: this.mBulkReading.keySet()){
							contents = contents.concat("{" + key + ":" + this.mBulkReading.get(key).mValue + "}");
                		}
						
						intent.putExtra(Intents.Scan.RESULT, contents);
						
						sendReplyMessage(R.id.return_scan_result, intent, DEFAULT_INTENT_RESULT_DURATION_MS);	
						return true;
					}
				}else{
					setResult(RESULT_CANCELED);	
					finish();
					return true;					
				}
			}
			if(this.mSource == IntentSource.NONE && this.mLastResult != null){
				restartPreviewAfterDelay(0L);
				return true;
			}
			break;
		case KeyEvent.KEYCODE_FOCUS:
		case KeyEvent.KEYCODE_CAMERA:
			return true;			
		}
		// TODO Auto-generated method stub
		return super.onKeyDown(keyCode, event);
	}
	
	private void sendReplyMessage(int id, Object arg, long delayMS) {
		if (this.mHandler != null) {
			Message message = Message.obtain(this.mHandler, id, arg);
			if (delayMS > 0L) {
				this.mHandler.sendMessageDelayed(message, delayMS);
			}else{
				this.mHandler.sendMessage(message);
			}
		}
	}
	
	private void initCamera(SurfaceHolder surfaceHolder){
		if(surfaceHolder == null){
			throw new IllegalStateException("Controlador de superficie nao foi providenciado");
		}
		if(this.mCameraManager.isOpen()){
			Log.w(TAG, "initCamera() já foi chamado");
			return;
		}
		try{
			this.mCameraManager.openCamera(surfaceHolder);
			if(this.mHandler == null){
				this.mHandler = new BarcodeReaderHandler(this, this.mDecodeFormats, this.mDecodeHints, this.mCharacterSet, this.mCameraManager);
			}
			decodeOrStoreSavedBitmap(null, null);
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			displayFrameworkBugMessageAndExit();		
		} catch (RuntimeException e) {
			Log.w(TAG, "Erro inesperado ao inicializar a camera", e);
		    displayFrameworkBugMessageAndExit();
		}
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
			Log.e(TAG, "surfaceCreated() retornou um controlador de superficie nulo!");
	    }
	    if (!this.mHasSurface) {
	    	this.mHasSurface = true;
	    	initCamera(holder);
	    }
	}
	
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
	    this.mHasSurface = false;
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	}
	
	public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor){
		this.mLastResult = rawResult;
		
		boolean fromLiveScan = barcode != null;
	    if (fromLiveScan) {
	    	this.mBeepManager.playBeepSoundAndVibrate();
	    	drawResultPoints(barcode, scaleFactor, rawResult);	    	
	    }
	    
	    switch (this.mSource) {
		case NATIVE_APP_INTENT:
			if(this.mBulkMode){
				handleBulkModeExternally(rawResult, barcode);
			}else{
				handleDecodeExternally(rawResult, barcode);
			}			
			break;
		case NONE:
		    if (fromLiveScan && this.mBulkMode) {
		    	Toast.makeText(getApplicationContext(),
		                         getResources().getString(R.string.msg_bulk_mode_scanned) + " (" + rawResult.getText() + ')',
		                         Toast.LENGTH_SHORT).show();
		        // Wait a moment or else it will scan the same barcode continuously about 3 times
		    	restartPreviewAfterDelay(BULK_MODE_SCAN_DELAY_MS);
		    } else {
		    	//handleDecodeInternally(rawResult, resultHandler, barcode);
		    }	
		    break;
		}
	}
	
	private void handleBulkModeExternally(Result rawResult, Bitmap barcodeImage) {
		String barcode = rawResult.getText();
		
		MutableInt count = this.mBulkReading.get(barcode);
		if(count == null){
			// Barcode ainda não existe no hashtable
			this.mBulkReading.put(barcode, new MutableInt());
		}else{
			// Incrementa a quantidade do barcode lido
			count.increment();
		}	
		Toast.makeText(getApplicationContext(), "Barcode: " + barcode + " Quantidade lida: " + this.mBulkReading.get(barcode).mValue, Toast.LENGTH_SHORT).show();
		restartPreviewAfterDelay(BULK_MODE_SCAN_DELAY_MS);
	}

	private void handleDecodeInternally(Result rawResult, Bitmap barcode) {
		
	}
	
	private void handleDecodeExternally(Result rawResult, Bitmap barcode) {
		if(barcode != null){
			this.mViewFinderView.drawResultBitmap(barcode);
		}
		
		long resultDurationMS;
		if(getIntent() == null){
			resultDurationMS = DEFAULT_INTENT_RESULT_DURATION_MS;
		}else{
			resultDurationMS = getIntent().getLongExtra(Intents.Scan.RESULT_DISPLAY_DURATION_MS,
                                                  		DEFAULT_INTENT_RESULT_DURATION_MS);
		}
		
		if(this.mSource == IntentSource.NATIVE_APP_INTENT){
			Intent intent = new Intent(getIntent().getAction());
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			intent.putExtra(Intents.Scan.RESULT, rawResult.toString());
			
			sendReplyMessage(R.id.return_scan_result, intent, resultDurationMS);
		}
	}
	
	private void drawResultPoints(Bitmap barcode, float scaleFactor, Result rawResult) {
		ResultPoint[] points = rawResult.getResultPoints();
	    if (points != null && points.length > 0) {
	    	Canvas canvas = new Canvas(barcode);
	    	Paint paint = new Paint();
	    	paint.setColor(getResources().getColor(R.color.result_points));
	    	if (points.length == 2) {
	    		paint.setStrokeWidth(4.0f);
	    		drawLine(canvas, paint, points[0], points[1], scaleFactor);
	    	} else if (points.length == 4 && (rawResult.getBarcodeFormat() == BarcodeFormat.UPC_A || rawResult.getBarcodeFormat() == BarcodeFormat.EAN_13)) {
	    		drawLine(canvas, paint, points[0], points[1], scaleFactor);
	    		drawLine(canvas, paint, points[2], points[3], scaleFactor);
	    	} else {
	    		paint.setStrokeWidth(10.0f);
	    		for (ResultPoint point : points) {
	    			if (point != null) {
	    				canvas.drawPoint(scaleFactor * point.getX(), scaleFactor * point.getY(), paint);
	    			}
	    		}
	    	}
	    }
	}
	
	private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b, float scaleFactor) {
		if (a != null && b != null) {
			canvas.drawLine(scaleFactor * a.getX(), scaleFactor * a.getY(), scaleFactor * b.getX(), scaleFactor * b.getY(), paint);
	    }
	}
		
	private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
		if (this.mHandler == null) {
			this.mSavedResultToShow = result;
	    } else {
	    	if (result != null) {
	    		this.mSavedResultToShow = result;
	    	}
	    	if (this.mSavedResultToShow != null) {
	    		Message message = Message.obtain(this.mHandler, R.id.decode_succeeded, this.mSavedResultToShow);
	    		this.mHandler.sendMessage(message);
	    	}
	    	this.mSavedResultToShow = null;
	    }
	}

	private void displayFrameworkBugMessageAndExit() {
	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setTitle(getString(R.string.app_name));
	    builder.setMessage(getString(R.string.msg_camera_framework_bug));
	    builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
	    builder.setOnCancelListener(new FinishListener(this));
	    builder.show();
	}

	public void restartPreviewAfterDelay(long delayMS) {
		if (this.mHandler != null) {
			this.mHandler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
	    }
	    resetStatusView();
	}
	
	private void resetStatusView() {
	    this.mStatusView.setText(R.string.msg_default_status);
	    this.mStatusView.setVisibility(View.VISIBLE);
	    this.mViewFinderView.setVisibility(View.VISIBLE);
	    this.mLastResult = null;
	}
	
	public void drawViewfinder() {
	    this.mViewFinderView.drawViewfinder();
	}
}
