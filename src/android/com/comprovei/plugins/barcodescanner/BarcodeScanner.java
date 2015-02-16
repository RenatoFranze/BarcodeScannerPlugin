package com.comprovei.plugins.barcodescanner;

import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

class RequestCodeGenerator{
	private static final AtomicInteger mSeed = new AtomicInteger();
	
	public static int getNewRequestCode(){
		return mSeed.incrementAndGet();
	}
}

public class BarcodeScanner extends CordovaPlugin{
	public static final int REQUEST_CODE = RequestCodeGenerator.getNewRequestCode();
	
	private static final String TAG = "BarcodeScanner";
	private static final String SCAN = "scan";
    private static final String TEXT = "text";
    private static final String SCAN_INTENT = "com.comprovei.plugins.barcodescanner.SCAN";
    private static final String BULK_MODE = "BULK_MODE";
 	private static final String CHECK_BARCODES = "CHECK_BARCODES";
 	private static final String BARCODES = "BARCODES";
    
    private CallbackContext mCallbackContext;
    
    public BarcodeScanner() {
    }
        
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
    	this.mCallbackContext = callbackContext;

        if (action.equals(SCAN)) { 
			JSONObject jsonObj = args.optJSONObject(0);    
			boolean bulkMode = jsonObj.optBoolean(BULK_MODE); 
			boolean checkBarcodes = jsonObj.optBoolean(CHECK_BARCODES);
			String allBarcodes = jsonObj.optString(BARCODES);
			scan(bulkMode, checkBarcodes, allBarcodes);   
		} else {
            return false;
        }
        return true;
    }

	public void scan(boolean bulkMode, boolean checkBarcodes, String allBarcodes) {
		Intent intentScan = new Intent(SCAN_INTENT);
		intentScan.addCategory(Intent.CATEGORY_DEFAULT);		
		intentScan.putExtra(BULK_MODE, bulkMode);
		intentScan.putExtra(CHECK_BARCODES, checkBarcodes);
		intentScan.putExtra(BARCODES, allBarcodes);
		
		intentScan.setPackage(this.cordova.getActivity().getApplicationContext().getPackageName());
		this.cordova.startActivityForResult((CordovaPlugin) this, intentScan, REQUEST_CODE);
	}    
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if(requestCode == REQUEST_CODE){
			if(resultCode == Activity.RESULT_OK){
				JSONObject jsonObj = new JSONObject();
				try{
					jsonObj.put(TEXT, intent.getStringExtra("SCAN_RESULT"));				
				}catch (JSONException je){
					Log.d(TAG, "Erro inesperado na leitura");
				}
				this.mCallbackContext.success(jsonObj);
			}else if(resultCode == Activity.RESULT_CANCELED){
				JSONObject jsonObj = new JSONObject();
				try{
					jsonObj.put(TEXT, "Nenhum codigo lido");							
				}catch (JSONException je){
					Log.d(TAG, "Erro inesperado na leitura");
				}
				this.mCallbackContext.success(jsonObj);
			}else{
				this.mCallbackContext.error("Erro inesperado");
			}
		}
	}
}
