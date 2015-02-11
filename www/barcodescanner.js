var ScannerLoader = function (require, exports, module) {
	var exec = require("cordova/exec");
	function BarcodeScanner(){
		console.log("BarcodeScanner.js is created");
	};
	
	BarcodeScanner.prototype.scan = function (successCallback, errorCallback, bulkMode) {
		if(errorCallback == null){
			errorCallback = function () {
	        };    		
		}
		
		if (typeof errorCallback != "function") {
	        console.log("BarcodeScanner.scan falha: errorCallback nao eh uma funcao");
	        return;
	    }
	
	    if (typeof successCallback != "function") {
	    	console.log("BarcodeScanner.scan falha: successCallback nao eh uma funcao");
	        return;
	    }
	
	    exec(successCallback, errorCallback, 'BarcodeScanner', 'scan', [{"BULK_MODE":bulkMode}]);
	}
	
	var barcodeScanner = new BarcodeScanner();
	module.exports = barcodeScanner;
}

ScannerLoader(require, exports, module);

cordova.define("cordova/plugin/BarcodeScanner", ScannerLoader);


