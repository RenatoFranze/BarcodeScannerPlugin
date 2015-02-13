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
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
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
	int mValue; // Começa em 0
	public void increment () { ++this.mValue;      }
	public int  get ()       { return this.mValue; }
	public void set (int value) {this.mValue = value; }
}

class Pair<A, B> implements Serializable{
	A mFirst = null;
	B mSecond = null;
	
	Pair(A first, B second){
		this.mFirst = first;
		this.mSecond = second;
	}
	
	public A getFirst(){
		return this.mFirst;
	}
	
	public void setFirst(A first){
		this.mFirst = first;
	}
	
	public B getSecond(){
		return this.mSecond;
	}
	
	public void setSecond(B second){
		this.mSecond = second;
	}
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
    private boolean mBulkMode = true;
    private boolean mCheckBarcodes = false;
    private Map<String, Pair<Integer, Integer>> mAllBarcodes;
    
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
        
        // Armazena todos os codigos de barras lido
        this.mAllBarcodes = new HashMap<String, Pair<Integer, Integer>>();
        
        if(intent != null){
        	String action = intent.getAction();
        	Log.i(TAG, "Ação enviada: " + action);
        	if(Intents.Scan.ACTION.equals(action)){
        		Log.i(TAG, "Ação igual");
        		this.mSource = IntentSource.NATIVE_APP_INTENT;
        		this.mBulkMode = intent.getBooleanExtra(Intents.Scan.BULK_MODE, false);
        		this.mCheckBarcodes = intent.getBooleanExtra(Intents.Scan.CHECK_BARCODES, false);
        		
        		if(this.mCheckBarcodes){
        			// Carrega a lista de barcodes a serem verificados
        			String barcodes = intent.getStringExtra(Intents.Scan.BARCODES);
            		
            		if(barcodes != null){
            			String[] arrayBarcodes = barcodes.split(";");
            			int count = arrayBarcodes.length;
            			
            			String barcode = "";
            			Pair<Integer, Integer> values;
            			int readQuantity, total, separatorPosition1, separatorPosition2 = 0;   
            			
            			for(int i = count - 1; i >= 0; i--){
            				separatorPosition1 = arrayBarcodes[i].indexOf(":");
            				separatorPosition2 = arrayBarcodes[i].indexOf("/");
            				
            				// Pega o código de barras
            				barcode = arrayBarcodes[i].substring(0, separatorPosition1);
            				
            				// Pega a quantidade já lida
            				readQuantity = Integer.parseInt(arrayBarcodes[i].substring(separatorPosition1 + 1, separatorPosition2));
            				
            				// Pega a quantidade total a ser lida
            				total = Integer.parseInt(arrayBarcodes[i].substring(separatorPosition2 + 1));
            				
            				// Verifica se o barcode já existe na lista
            				values = this.mAllBarcodes.get(barcode);
            				
            				if(values == null){
            					values = new Pair(readQuantity, total);
            					this.mAllBarcodes.put(barcode, values);        					
            				}else{
            					readQuantity += values.getFirst();
            					values.setFirst(readQuantity);      
            					total += values.getSecond();
            					values.setSecond(total);
            				}
            			}
            		}        			
        		}
        		
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
					if(this.mAllBarcodes.isEmpty()){
						setResult(RESULT_CANCELED);	
						finish();
						return true;						
					}else{
						handleIntentReturn();	
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
	
	public void handleDecode(Result rawResult, Bitmap barcodeImage, float scaleFactor){
		this.mLastResult = rawResult;
		
		boolean fromLiveScan = barcodeImage != null;
	    if (fromLiveScan) {	    	
	    	drawResultPoints(barcodeImage, scaleFactor, rawResult);	    	
	    }
	    
		checkBarcode(rawResult.getText());	
		
		// Leitura unica ou em massa
		if(this.mBulkMode == false){		
			if(this.mSource == IntentSource.NATIVE_APP_INTENT){
				handleIntentReturn();					
			}else{
				resetStatusView();
			}
		}else{
			restartPreviewAfterDelay(BULK_MODE_SCAN_DELAY_MS);
		}	
	}
	
	private void checkBarcode(String barcode){
		Pair<Integer, Integer> values = this.mAllBarcodes.get(barcode);
		int quantity = 1;
		
		// Verifica se o barcode está na lista dos disponíveis
		// Senão, insere na lista lida
		if(this.mCheckBarcodes){			
			// Barcode não está na lista, alertar usuario
			// Senão, verificar se a quantidade lida é menor que o total e incrementar
			if(values == null){
				this.mBeepManager.playBeepSoundAndVibrate(R.raw.failbeep);
				alertHandler("Código de barras lido " + barcode + " não faz parte da lista.", "Código inválido");
			}else{
				quantity = values.getFirst();
				int total = values.getSecond();
				if(quantity < total){
					this.mBeepManager.playBeepSoundAndVibrate(R.raw.beep);
					quantity++;
					values.setFirst(quantity);			
					Toast.makeText(getApplicationContext(), "Barcode: " + barcode + " Quantidade lida/Total: " + quantity + "/" + total, Toast.LENGTH_SHORT).show();
				}else{
					this.mBeepManager.playBeepSoundAndVibrate(R.raw.failbeep);
					alertHandler("Código de barras lido " + barcode + " já atingiu a quantidade necessária.", "Código inválido");
				}
			}
		}else{
			this.mBeepManager.playBeepSoundAndVibrate(R.raw.beep);
			if(values == null){
				values = new Pair(quantity, 0);
				this.mAllBarcodes.put(barcode, values); 				
			}else{
				quantity = values.getFirst();
				quantity++;
				values.setFirst(quantity);	
			}
			Toast.makeText(getApplicationContext(), "Barcode: " + barcode + " Quantidade lida: " + quantity, Toast.LENGTH_SHORT).show();
		}		
	}
	
	private void alertHandler(String message, String title){
		AlertDialog.Builder builder1 = new AlertDialog.Builder(BarcodeReader.this);	
		builder1.setTitle(title);
		builder1.setMessage(message);
		builder1.setCancelable(true);
        builder1.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();				
			}
		});
        
		AlertDialog alert1 = builder1.create();
		alert1.show();		
	}
	
	private void handleIntentReturn(){
		long resultDurationMS;
		if(getIntent() == null){
			resultDurationMS = DEFAULT_INTENT_RESULT_DURATION_MS;
		}else{
			resultDurationMS = getIntent().getLongExtra(Intents.Scan.RESULT_DISPLAY_DURATION_MS,
                                                  		DEFAULT_INTENT_RESULT_DURATION_MS);
		}
		
		Intent intent = new Intent(getIntent().getAction());
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		
		String contents = "";
		int readQuantity = 0;
		for(String key: this.mAllBarcodes.keySet()){
			readQuantity = this.mAllBarcodes.get(key).getFirst();
			if (readQuantity > 0) contents = contents.concat(key + ":" + readQuantity  + ";");
		}
		
		if(null != contents && contents.length() > 0) contents = contents.substring(0, contents.length()-1);						
		
		intent.putExtra(Intents.Scan.RESULT, contents);
		
		sendReplyMessage(R.id.return_scan_result, intent, resultDurationMS);			
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
	    //this.mStatusView.setText(R.string.msg_default_status);
	    this.mStatusView.setVisibility(View.VISIBLE);
	    this.mViewFinderView.setVisibility(View.VISIBLE);
	    this.mLastResult = null;
	}
	
	public void drawViewfinder() {
	    this.mViewFinderView.drawViewfinder();
	}
}
