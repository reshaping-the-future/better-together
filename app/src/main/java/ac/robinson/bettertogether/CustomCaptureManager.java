package ac.robinson.bettertogether;

import android.app.Activity;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.CompoundBarcodeView;

public class CustomCaptureManager extends CaptureManager {

	private BarcodeCallback mCallback;

	public CustomCaptureManager(Activity activity, CompoundBarcodeView barcodeView, BarcodeCallback callback) {
		super(activity, barcodeView);
		mCallback = callback;
	}

	@Override
	protected void returnResult(BarcodeResult rawResult) {
		mCallback.barcodeResult(rawResult);
	}
}
