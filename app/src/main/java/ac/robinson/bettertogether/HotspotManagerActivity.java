package ac.robinson.bettertogether;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.ResultPoint;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.CompoundBarcodeView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.event.EventType;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivity;
import ac.robinson.bettertogether.hotspot.HotspotManagerService;
import ac.robinson.bettertogether.mode.KeyboardActivity;
import ac.robinson.bettertogether.mode.TextActivity;
import ac.robinson.bettertogether.mode.YouTubeCommentsActivity;
import ac.robinson.bettertogether.mode.YouTubeControlsActivity;
import ac.robinson.bettertogether.mode.YouTubeDataActivity;
import ac.robinson.bettertogether.mode.YouTubePlaylistActivity;
import ac.robinson.bettertogether.mode.YouTubeRelatedVideoActivity;
import ac.robinson.bettertogether.mode.YouTubeSearchActivity;

public class HotspotManagerActivity extends BaseHotspotActivity {

	private static final int COARSE_LOCATION_PERMISSION_RESULT = 1;

	private boolean mHotspotMode = false;
	private boolean mClientConnecting = false;
	private boolean mSetupCompleted = false;
	private boolean mInternetAccessAvailable = false;

	// for scanning
	private CaptureManager mCaptureManager;
	private CompoundBarcodeView mBarcodeScannerView;

	// for setting up hotspots
	private LinearLayout mCreateHotspotView;
	private ImageView mQRView;
	private TextView mFooter;
	private GridView mChooseModeView;
	private LinearLayout mConnectionProgressView;
	private TextView mConnectionProgressUpdate;

	public static class Mode {
		public Mode(Class cls, int icon, boolean needsInternetAccess) {
			mClass = cls;
			mIcon = icon;
			mNeedsInternetAccess = needsInternetAccess;
		}

		public Class mClass;
		public int mIcon;
		public boolean mNeedsInternetAccess;
	}

	private static ArrayList<Mode> sModes = new ArrayList<>();

	// add new modes here
	static {
		// demo modes
		sModes.add(new Mode(KeyboardActivity.class, R.drawable.ic_keyboard_red_600_48dp, false));
		sModes.add(new Mode(TextActivity.class, R.drawable.ic_subject_red_600_48dp, false));

		// YouTube
		sModes.add(new Mode(YouTubeDataActivity.class, R.drawable.ic_movie_red_600_48dp, true)); // video player
		sModes.add(new Mode(YouTubeControlsActivity.class, R.drawable.ic_play_arrow_red_600_48dp, false)); // playback controls
		sModes.add(new Mode(YouTubeSearchActivity.class, R.drawable.ic_search_red_600_48dp, false)); // search
		sModes.add(new Mode(YouTubePlaylistActivity.class, R.drawable.ic_playlist_play_red_600_48dp, false)); // playlist
		sModes.add(new Mode(YouTubeCommentsActivity.class, R.drawable.ic_question_answer_red_600_48dp, false)); // video comments
		sModes.add(new Mode(YouTubeRelatedVideoActivity.class, R.drawable.ic_subscriptions_red_600_48dp, false)); // related
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState == null) {
			checkInternetConnectionAvailable(); // do this as early as possible
		}
		setContentView(R.layout.activity_hotspot_manager);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		mBarcodeScannerView = (CompoundBarcodeView) findViewById(R.id.barcode_scanner);
		mCreateHotspotView = (LinearLayout) findViewById(R.id.create_hotspot_view);
		mQRView = (ImageView) findViewById(R.id.qr_image);
		mFooter = (TextView) findViewById(R.id.footer);
		mChooseModeView = (GridView) findViewById(R.id.choose_mode_view);
		mConnectionProgressView = (LinearLayout) findViewById(R.id.connecting_hotspot_progress_indicator);
		mConnectionProgressUpdate = (TextView) findViewById(R.id.connecting_hotspot_progress_update_text);

		if (savedInstanceState != null) {
			mHotspotMode = savedInstanceState.getBoolean("mHotspotMode", false);
			mClientConnecting = savedInstanceState.getBoolean("mClientConnecting", false);
			mSetupCompleted = savedInstanceState.getBoolean("mSetupCompleted", false);
			mInternetAccessAvailable = savedInstanceState.getBoolean("mInternetAccessAvailable", false);
			if (getHotspotUrl() != null) {
				mQRView.setImageBitmap(HotspotManagerService.generateQrCode(getHotspotUrl())); // restore previous hotspot code
			}
			if (mClientConnecting) {
				mBarcodeScannerView.pause();
				mBarcodeScannerView.setVisibility(View.GONE);
				mConnectionProgressView.setVisibility(View.VISIBLE);
				mFooter.setText(R.string.connecting_hotspot);
			}
		}
		if (mSetupCompleted) {
			updateUIOnSetupCompleted();
		} else {
			if (mHotspotMode) {
				mBarcodeScannerView.pause();
				mBarcodeScannerView.setVisibility(View.GONE);
				mCreateHotspotView.setVisibility(View.VISIBLE);
				mFooter.setText(R.string.host_hotspot);
			} else {
				IntentIntegrator integrator = new IntentIntegrator(this);
				integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
				integrator.setCaptureActivity(HotspotManagerActivity.class);
				integrator.setOrientationLocked(false);
				integrator.setBeepEnabled(false);
				integrator.setPrompt("");
				mCaptureManager = new CustomCaptureManager(this, mBarcodeScannerView, mBarcodeCallback);
				mCaptureManager.initializeFromIntent(integrator.createScanIntent(), savedInstanceState);
				mCaptureManager.decode();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (!mSetupCompleted && !mClientConnecting) {
			getMenuInflater().inflate(R.menu.menu_manager, menu);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_create_hotspot:
				if (!mHotspotMode) {
					createHotspot();
				}
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void createHotspot() {
		mHotspotMode = true;

		mBarcodeScannerView.pause();
		mBarcodeScannerView.setVisibility(View.GONE);
		mCreateHotspotView.setVisibility(View.VISIBLE);
		mFooter.setText(R.string.host_hotspot);

		SharedPreferences settings = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
		String hotspotUrl = null;
		// TODO: re-add when connection is fixed: hotspotUrl = settings.getString(getString(R.string.saved_hotspot_url), null);
		if (TextUtils.isEmpty(hotspotUrl)) {
			String hotspotName = HotspotManagerService.getHotspotName(getString(R.string.app_name_short), HotspotManagerService
					.getRandomShortUUID());
			String hotspotPassword = HotspotManagerService.getRandomShortUUID();
			hotspotUrl = HotspotManagerService.getHotspotUrl(hotspotName, hotspotPassword);
		}

		setHotspotUrl(hotspotUrl);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(getString(R.string.saved_hotspot_url), hotspotUrl);
		editor.apply();

		sendServiceMessage(HotspotManagerService.MSG_ENABLE_HOTSPOT, hotspotUrl);

		mQRView.setImageBitmap(HotspotManagerService.generateQrCode(hotspotUrl));
	}

	private BarcodeCallback mBarcodeCallback = new BarcodeCallback() {
		@Override
		public void barcodeResult(BarcodeResult rawResult) {
			if (rawResult.getText() != null) {
				// a roundabout way of doing this, but it avoids writing our own methods in custom capture manager
				Intent intent = CaptureManager.resultIntent(rawResult, null);
				IntentResult result = IntentIntegrator.parseActivityResult(IntentIntegrator.REQUEST_CODE, Activity.RESULT_OK,
						intent);
				setHotspotUrl(result.getContents());

				mBarcodeScannerView.pause();
				mBarcodeScannerView.setVisibility(View.GONE);
				mConnectionProgressView.setVisibility(View.VISIBLE);
				mFooter.setText(R.string.connecting_hotspot);
				mClientConnecting = true;
				invalidateOptionsMenu();

				if (locationPermissionGranted()) {
					requestHotspotConnectionWithInternetStatus();
				}
			}
		}

		@Override
		public void possibleResultPoints(List<ResultPoint> resultPoints) {
			// nothing to do - not called in our custom capture manager
		}
	};

	private void requestHotspotConnectionWithInternetStatus() {
		// TODO: bit of a hacky way to choose WiFi or Bluetooth connection - ideally, do this based on requirements
		// TODO: CURRENTLY SET TO ALWAYS PREFER BLUETOOTH FOR RELIABILITY ON OLDER DEVICES
		String connectionUrl = getHotspotUrl() + "&ia=1"; // + (mInternetAccessAvailable ? "1" : "0");
		sendServiceMessage(HotspotManagerService.MSG_JOIN_HOTSPOT, connectionUrl);
	}

	public static void showQRDialog(Context context, final String hotspotUrl) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setPositiveButton(R.string.button_done, null);

		final AlertDialog qrDialog = builder.create();
		View dialogLayout = LayoutInflater.from(context).inflate(R.layout.dialog_qr_code, null);
		qrDialog.setView(dialogLayout);

		// remove spacing - see: http://stackoverflow.com/q/27954561
		qrDialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface d) {
				ImageView imageView = (ImageView) qrDialog.findViewById(R.id.dialog_qr_image);
				Bitmap qrCode = HotspotManagerService.generateQrCode(hotspotUrl);
				if (imageView != null && qrCode != null) {
					imageView.setImageBitmap(qrCode);
					float imageWidthInPX = (float) imageView.getWidth();
					LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Math.round(imageWidthInPX), Math
							.round(imageWidthInPX * (float) qrCode.getHeight() / (float) qrCode.getWidth()));
					imageView.setLayoutParams(layoutParams);
				}
			}
		});

		qrDialog.show();
	}

	@Override
	public void onBroadcastMessageReceived(BroadcastMessage message) {
		// nothing to do
	}

	@Override
	public void onServiceMessageReceived(int type, String data) {
		switch (type) {
			case HotspotManagerService.EVENT_NEW_DEVICE_CONNECTED:
				if (!mSetupCompleted) {
					mSetupCompleted = true;
					EventType.Type connectionType = EventType.Type.valueOf(data);
					if ((connectionType != EventType.Type.BLUETOOTH || mHotspotMode) && mInternetAccessAvailable) {
						// need to recheck whether we have internet if we didn't use Bluetooth to connect or we're the hotspot
						checkInternetConnectionAvailable();
					} else {
						updateUIOnSetupCompleted();
						invalidateOptionsMenu(); // so we change the available menu items
					}
				}
				break;

			case HotspotManagerService.EVENT_CONNECTION_STATUS_UPDATE:
				mConnectionProgressUpdate.setText(data);
				break;

			case HotspotManagerService.EVENT_REMOTE_CLIENT_ERROR:
				// TODO: handle this if necessary - a single remote client connection failed
				break;

			case HotspotManagerService.EVENT_LOCAL_CLIENT_ERROR:
				// our connection to the server failed - will reconnect automatically, but can't interact while doing so
				mSetupCompleted = false;
				mClientConnecting = true;
				mBarcodeScannerView.pause();
				mBarcodeScannerView.setVisibility(View.GONE);
				mChooseModeView.setVisibility(View.GONE);
				mConnectionProgressView.setVisibility(View.VISIBLE);
				mFooter.setText(R.string.connecting_hotspot);
				break;

			case HotspotManagerService.EVENT_SETTINGS_PERMISSION_ERROR:
				checkSettingsAccess();
				break;
		}
	}

	private void updateUIOnSetupCompleted() {
		mClientConnecting = false;
		mConnectionProgressView.setVisibility(View.GONE);
		mBarcodeScannerView.pause();
		mBarcodeScannerView.setVisibility(View.GONE);
		mCreateHotspotView.setVisibility(View.GONE);
		mChooseModeView.setAdapter(new GridAdapter(sModes));
		mChooseModeView.setVisibility(View.VISIBLE);
		mChooseModeView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				Mode clickedMode = (Mode) parent.getAdapter().getItem(position);
				launchModeActivity(clickedMode.mClass);
			}
		});
		mFooter.setText(R.string.choose_mode);
	}

	private class GridAdapter extends BaseAdapter {
		final ArrayList<Mode> mItems;

		private GridAdapter(final ArrayList<Mode> modes) {
			mItems = new ArrayList<>();
			for (Mode mode : modes) {
				if (mode.mNeedsInternetAccess) {
					if (mInternetAccessAvailable) {
						mItems.add(mode);
					}
				} else {
					mItems.add(mode);
				}
			}
		}

		@Override
		public int getCount() {
			return mItems.size();
		}

		@Override
		public Object getItem(final int position) {
			return mItems.get(position);
		}

		@Override
		public long getItemId(final int position) {
			return position;
		}

		@Override
		public View getView(final int position, final View convertView, final ViewGroup parent) {
			View view = convertView;
			if (view == null) {
				view = LayoutInflater.from(parent.getContext()).inflate(R.layout.mode_selector_item, parent, false);
			}
			ImageView icon = (ImageView) view.findViewById(R.id.mode_icon);
			icon.setImageResource(mItems.get(position).mIcon);
			return view;
		}
	}

	// TODO: improve all permissions aspects (including camera)
	private boolean canWriteSettings() {
		//noinspection SimplifiableIfStatement
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			return true;
		}
		return Settings.System.canWrite(HotspotManagerActivity.this);
	}

	// after API 23, we need to handle on-demand permissions requests
	private boolean checkSettingsAccess() {
		// TODO: by default, applications are not granted access, but the settings switch shows access is allowed - need to
		// TODO: toggle this switch to actually grant access. Need to improve this interaction generally
		if (!canWriteSettings()) {
			AlertDialog.Builder builder = new AlertDialog.Builder(HotspotManagerActivity.this);
			builder.setTitle(R.string.hint_settings_access).setMessage(R.string.hint_enable_settings_access)
					.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					// note: dismiss rather than cancel so we always take this action (pos or neg result)
//					if (checkSettingsAccess()) {
//						if (!mHotspotMode) {
//							createHotspot();
//						}
//					}
				}
			}).setPositiveButton(R.string.hint_edit_settings_access, new DialogInterface.OnClickListener() {
				@TargetApi(Build.VERSION_CODES.M)
				@Override
				public void onClick(DialogInterface dialog, int which) {
					try {
						Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
						settingsIntent.setData(Uri.parse("package:" + HotspotManagerActivity.this.getPackageName()));
						startActivity(settingsIntent);
					} catch (ActivityNotFoundException e) {
						Toast.makeText(HotspotManagerActivity.this, R.string.error_editing_settings, Toast.LENGTH_LONG).show();
					}
					dialog.dismiss();
				}
			}).setNeutralButton(R.string.button_done, null);
			builder.show();

			mHotspotMode = false;
			setHotspotUrl(null);

			mBarcodeScannerView.resume();
			mBarcodeScannerView.setVisibility(View.VISIBLE);
			mCreateHotspotView.setVisibility(View.GONE);
			mFooter.setText(R.string.join_hotspot);
			return false;
		}
		return true;
	}

	// after API 23, we need to handle on-demand permissions requests
	private boolean locationPermissionGranted() {
		if (ContextCompat.checkSelfPermission(HotspotManagerActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
				PackageManager.PERMISSION_GRANTED) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(HotspotManagerActivity.this, Manifest.permission
					.ACCESS_COARSE_LOCATION)) {
				// TODO: handle this via UI
			} else {
				ActivityCompat.requestPermissions(HotspotManagerActivity.this, new String[]{Manifest.permission
						.ACCESS_COARSE_LOCATION}, COARSE_LOCATION_PERMISSION_RESULT);
			}
			return false;
		}
		return true;
	}

	private void checkInternetConnectionAvailable() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			new InternetConnectionDetectionTask().execute();
		} else {
			setInternetConnectionAvailability(hasInternetConnection(HotspotManagerActivity.this));
		}
	}

	private void setInternetConnectionAvailability(boolean internetAvailable) {
		// TODO: if they scan too fast then we'll end up assuming no connection regardless of this value
		mInternetAccessAvailable = internetAvailable;
		if (mSetupCompleted) {
			// show the mode views that apply
			updateUIOnSetupCompleted();
			invalidateOptionsMenu(); // so we change the available menu items
		}
	}

	private static boolean hasInternetConnection() {
		try {
			HttpURLConnection connection = (HttpURLConnection) (new URL("http://clients3.google.com/generate_204")
					.openConnection());
			connection.setRequestProperty("User-Agent", "Android");
			connection.setRequestProperty("Connection", "close");
			connection.setConnectTimeout(1500);
			connection.connect();
			return connection.getResponseCode() == 204 && connection.getContentLength() == 0;
		} catch (IOException ignored) {
		}
		return false;
	}

	@TargetApi(Build.VERSION_CODES.M)
	private static boolean hasInternetConnection(final Context context) {
		ConnectivityManager connectivityManager = (ConnectivityManager) context.
				getSystemService(Context.CONNECTIVITY_SERVICE);
		Network network = connectivityManager.getActiveNetwork();
		NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
		return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && capabilities
				.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
	}

	private class InternetConnectionDetectionTask extends AsyncTask<Void, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Void... params) {
			return hasInternetConnection();
		}

		@Override
		protected void onPostExecute(Boolean hasConnection) {
			setInternetConnectionAvailability(hasConnection);
		}
	}

	private void launchModeActivity(Class<?> cls) {
		Intent launchIntent = new Intent(HotspotManagerActivity.this, cls);
		launchIntent.putExtra(BaseHotspotActivity.HOTSPOT_URL, getHotspotUrl());
		startActivity(launchIntent);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mCaptureManager != null) {
			mCaptureManager.onResume();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mCaptureManager != null) {
			mCaptureManager.onPause();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mCaptureManager != null) {
			mCaptureManager.onDestroy();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		if (mCaptureManager != null) {
			mCaptureManager.onSaveInstanceState(outState);
		}
		outState.putBoolean("mHotspotMode", mHotspotMode);
		outState.putBoolean("mClientConnecting", mClientConnecting);
		outState.putBoolean("mSetupCompleted", mSetupCompleted);
		outState.putBoolean("mInternetAccessAvailable", mInternetAccessAvailable);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
		switch (requestCode) {
			case COARSE_LOCATION_PERMISSION_RESULT:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					requestHotspotConnectionWithInternetStatus();
				} else {
					// TODO: permission denied - handle
				}
				break;

			default:
				// TODO: improve this (customise UI to allow re-request of permission)
				if (mCaptureManager != null) {
					mCaptureManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
				}
				break;
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (mCaptureManager != null) {
			return mBarcodeScannerView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
		}
		return super.onKeyDown(keyCode, event);
	}
}
