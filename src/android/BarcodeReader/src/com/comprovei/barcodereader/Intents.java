package com.comprovei.barcodereader;

public class Intents {
	private Intents(){
		
	}
	
	public static final class Scan{
		public static final String ACTION = "com.comprovei.plugins.barcodescanner.SCAN";
		//public static final String ACTION = "com.comprovei.barcodereader.SCAN";
		
		public static final String PROMPT_MESSAGE = "PROMPT_MESSAGE";		
		
		public static final String RESULT_DISPLAY_DURATION_MS = "RESULT_DISPLAY_DURATION_MS";
		
		public static final String RESULT = "SCAN_RESULT";
		
		// Seta leitura em massa (padrao = false)		
		public static final String BULK_MODE = "BULK_MODE";
		
		// Seta a verificação da existência dos barcodes (padrao = false)
		public static final String CHECK_BARCODES = "CHECK_BARCODES";
		
		// Lista de barcodes para serem lidos
		public static final String BARCODES = "BARCODES";
		
		private Scan(){
			
		}
	}
}
