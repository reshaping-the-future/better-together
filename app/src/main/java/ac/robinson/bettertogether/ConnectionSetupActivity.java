/*
 * Copyright (C) 2017 The Better Together Toolkit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package ac.robinson.bettertogether;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper;
import com.google.zxing.ResultPoint;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.CompoundBarcodeView;

import java.util.List;
import java.util.Map;
import java.util.Set;

import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.api.messaging.PluginIntent;
import ac.robinson.bettertogether.host.Plugin;
import ac.robinson.bettertogether.host.PluginAdapter;
import ac.robinson.bettertogether.host.PluginClickListener;
import ac.robinson.bettertogether.host.PluginFinder;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivity;
import ac.robinson.bettertogether.hotspot.ConnectionOptions;
import ac.robinson.bettertogether.hotspot.HotspotManagerService;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ConnectionSetupActivity extends BaseHotspotActivity implements PluginClickListener {

	private static final String TAG = "ConnectionSetupActivity";

	private static final int CAMERA_PERMISSION_RESULT = 101;
	private static final int COARSE_LOCATION_PERMISSION_RESULT = 102;
	public static final String RECONNECT_EXISTING_HOTSPOT = "existing_hotspot";

	// for scanning
	private CaptureManager mCaptureManager;
	private CompoundBarcodeView mBarcodeScannerView;
	private boolean mHasRequestedCameraPermission = false;

	// for setting up hotspots
	private LinearLayout mCreateHotspotView;
	private PluginAdapter mPluginViewAdapter;
	private RecyclerView mPluginView;
	private ImageView mQRView;
	private TextView mFooterText;
	private LinearLayout mConnectionProgressView;
	private TextView mConnectionProgressUpdate;

	private ConnectionMode mConnectionMode;

	private enum ConnectionMode {
		SCANNING, CONNECTION_INITIATED_HOTSPOT, CONNECTION_INITIATED_CLIENT
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// only show this activity if actually intended (i.e., opened via launcher with no other activities in foreground);
		// ConnectionSetupActivity does not remain active while plugins are being used
		if (savedInstanceState == null) {
			Activity activity = ((BetterTogetherApplication) getApplication()).getActiveActivity();
			if (activity != null && activity != ConnectionSetupActivity.this) {
				Intent intent = getIntent();
				Set<String> categories = null;
				if (intent != null) {
					categories = intent.getCategories();
				}
				if (categories != null && categories.contains(Intent.CATEGORY_LAUNCHER)) {
					finish(); // the app has come to the front; now exit and leave the previous activity here
				}
			}
		}

		super.onCreate(savedInstanceState);

		if (isFinishing()) {
			return;
		}

		setContentView(R.layout.activity_connection_setup);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		mBarcodeScannerView = findViewById(R.id.barcode_scanner);
		mCreateHotspotView = findViewById(R.id.create_hotspot_view);

		mPluginViewAdapter = new PluginAdapter(ConnectionSetupActivity.this, ConnectionSetupActivity.this);
		mPluginView = findViewById(R.id.plugin_view);
		mPluginView.setLayoutManager(new LinearLayoutManager(ConnectionSetupActivity.this, LinearLayoutManager.HORIZONTAL,
				false));
		mPluginView.setHasFixedSize(true);
		mPluginView.setAdapter(mPluginViewAdapter);
		new GravitySnapHelper(Gravity.START, false, mPluginViewAdapter).attachToRecyclerView(mPluginView);

		mQRView = findViewById(R.id.qr_image);
		mFooterText = findViewById(R.id.footer_text);

		mConnectionProgressView = findViewById(R.id.connecting_hotspot_progress_indicator);
		mConnectionProgressUpdate = findViewById(R.id.connecting_hotspot_progress_update_text);

		mConnectionMode = ConnectionMode.SCANNING;
		if (savedInstanceState != null) {
			mConnectionMode = (ConnectionMode) savedInstanceState.getSerializable("mConnectionMode");
		}

		String existingHotspot = getIntent().getStringExtra(RECONNECT_EXISTING_HOTSPOT);
		if (existingHotspot != null) {
			setHotspotUrl(existingHotspot);
			mConnectionMode = ConnectionMode.CONNECTION_INITIATED_CLIENT; // reconnect to existing hotspot if requested
		}

		initialiseCaptureManager(savedInstanceState);

		switch (mConnectionMode) {
			case CONNECTION_INITIATED_HOTSPOT:
				setupHotspotUI();
				break;
			case CONNECTION_INITIATED_CLIENT:
				setupClientUI();
				break;
			case SCANNING:
				break; // nothing to do
			default:
				break;
		}

		updatePluginList();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// when launching from the API's DefaultActivity, we get an onNewIntent event if the ConnectionSetupActivity is already
		// running - in this case there is nothing to do as we have already been resumed; normally this is not the case, as the
		// main action happens within a plugin, in which case we bring that activity to the front (see start of onCreate)
		super.onNewIntent(intent);
		setIntent(intent);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		if (mCaptureManager != null) {
			mCaptureManager.onSaveInstanceState(outState);
		}
		outState.putSerializable("mConnectionMode", mConnectionMode);
		super.onSaveInstanceState(outState);
	}

	private void initialiseCaptureManager(Bundle savedInstanceState) {
		IntentIntegrator integrator = new IntentIntegrator(this);
		integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
		integrator.setCaptureActivity(ConnectionSetupActivity.class);
		integrator.setOrientationLocked(false);
		integrator.setBeepEnabled(false);
		integrator.setPrompt("");
		mCaptureManager = new CustomCaptureManager(this, mBarcodeScannerView, mBarcodeCallback);
		mCaptureManager.initializeFromIntent(integrator.createScanIntent(), savedInstanceState);
	}

	private void restartCaptureManager() {
		if (cameraPermissionGranted()) {
			mCaptureManager.onResume();
			mCaptureManager.decode();
		}
	}

	private void updatePluginList() {
		Map<String, Plugin> plugins = PluginFinder.getValidPlugins(ConnectionSetupActivity.this, null);
		mPluginViewAdapter.clearPlugins();
		for (Plugin plugin : plugins.values()) {
			if (plugin.getIcon(ConnectionSetupActivity.this) != null) {
				Log.d(TAG, "Added plugin " + plugin.getPackageName());
				mPluginViewAdapter.addPlugin(plugin);
			} else {
				Log.d(TAG, "Error loading icon for " + plugin.getPackageName());
			}
		}
	}

	@Override
	protected void pluginUpdated(String pluginPackage) {
		super.pluginUpdated(pluginPackage);
		updatePluginList();
	}

	@Override
	public void onClick(Plugin plugin) {
		if (plugin == null) { // null plugin is the "get more plugins" button
			launchGetPluginsActivity();
		} else {
			Log.d(TAG, "Plugin clicked: " + plugin.getRawPluginLabel());
			ConnectionOptions connectionOptions = new ConnectionOptions();
			connectionOptions.mName = ConnectionOptions.formatHotspotName(ConnectionOptions.DEFAULT_HOTSPOT_NAME_FORMAT,
					getString(R.string.app_name_short), BetterTogetherUtils
					.getRandomString(HotspotManagerService.MESSAGE_ID_SIZE));
			connectionOptions.mPassword = BetterTogetherUtils.getRandomString(HotspotManagerService.MESSAGE_ID_SIZE);
			connectionOptions.mPluginPackage = plugin.getPackageName();
			setHotspotUrl(connectionOptions.getHotspotUrl());

			createHotspot();
		}
	}

	private void setupDefaultUI() {
		mBarcodeScannerView.resume();
		mBarcodeScannerView.setVisibility(View.VISIBLE);
		mPluginView.setVisibility(View.VISIBLE);

		mCreateHotspotView.setVisibility(View.GONE);
		mConnectionProgressView.setVisibility(View.GONE);
		mFooterText.setText(R.string.join_hotspot);
	}

	private void createHotspot() {
		mConnectionMode = ConnectionMode.CONNECTION_INITIATED_HOTSPOT;
		sendSystemMessage(HotspotManagerService.MSG_ENABLE_HOTSPOT, getHotspotUrl());
		setupHotspotUI();
	}

	private void setupHotspotUI() {
		mBarcodeScannerView.pause();
		mBarcodeScannerView.setVisibility(View.GONE);
		mPluginView.setVisibility(View.GONE);

		mCreateHotspotView.setVisibility(View.VISIBLE);
		mFooterText.setText(R.string.host_hotspot);
		mQRView.setImageBitmap(BetterTogetherUtils.generateQrCode(getHotspotUrl()));
	}

	private void createClient() {
		if (locationPermissionGranted()) {
			mConnectionMode = ConnectionMode.CONNECTION_INITIATED_CLIENT;
			sendSystemMessage(HotspotManagerService.MSG_JOIN_HOTSPOT, getHotspotUrl());
			setupClientUI();
		}
	}

	private void setupClientUI() {
		mBarcodeScannerView.pause();
		mBarcodeScannerView.setVisibility(View.GONE);
		mPluginView.setVisibility(View.GONE);

		mConnectionProgressView.setVisibility(View.VISIBLE);
		mFooterText.setText(R.string.connecting_hotspot);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mCaptureManager != null) {
			restartCaptureManager();
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
	public void onBroadcastMessageReceived(BroadcastMessage message) {
		// nothing to do in this activity
	}

	@Override
	public void onSystemMessageReceived(int type, String data) {
		super.onSystemMessageReceived(type, data);

		switch (type) {
			case HotspotManagerService.EVENT_DEVICE_CONNECTED:
				Log.d(TAG, "New device connected");
				if (!isFinishing()) {
					String hotspotUrl = getHotspotUrl();
					if (TextUtils.isEmpty(hotspotUrl)) {
						Log.e(TAG, "Null hotspot URL - ignoring");
						break; // TODO: temporarily working around connectivity bug caused by relaunching activity
					}

					ConnectionOptions currentConnectionOptions = ConnectionOptions.fromHotspotUrl(hotspotUrl);
					boolean isInbuiltPlugin = false;
					if (currentConnectionOptions != null) {
						if (PluginFinder.INTERNAL_PLUGIN_PACKAGES.contains(currentConnectionOptions.mPluginPackage)) {
							isInbuiltPlugin = true;
						}
					}

					launchPluginAndFinish(hotspotUrl, isInbuiltPlugin);
				}
				break;

			case HotspotManagerService.EVENT_LOCAL_CLIENT_ERROR:
				Log.d(TAG, "Local client error");
				// our connection to the server failed - will attempt to reconnect automatically
				break;

			case HotspotManagerService.EVENT_REMOTE_CLIENT_ERROR:
				Log.d(TAG, "Remote client error");
				// a single remote client connection failed
				break;

			case HotspotManagerService.EVENT_DEVICE_DISCONNECTED:
				// nothing to do here
				break;

			case HotspotManagerService.EVENT_CONNECTION_STATUS_UPDATE:
				mConnectionProgressUpdate.setText(data);
				break;

			case HotspotManagerService.EVENT_CONNECTION_INVALID_URL:
				// TODO: show a Toast?
				break;

			case HotspotManagerService.EVENT_SETTINGS_PERMISSION_ERROR:
				Log.d(TAG, "Settings permission error");
				checkSettingsAccess();
				setupDefaultUI();
				break;

			default:
				break;
		}
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

				createClient();
			}
		}

		@Override
		public void possibleResultPoints(List<ResultPoint> resultPoints) {
			// nothing to do - not called in our custom capture manager
		}
	};

	// after API 23, we need to handle on-demand permissions requests
	// the scanner library handles this, but not very well (exits on failure), so we do it ourselves
	private boolean cameraPermissionGranted() {
		if (ContextCompat.checkSelfPermission(ConnectionSetupActivity.this, Manifest.permission.CAMERA) !=
				PackageManager.PERMISSION_GRANTED) {

			if (!mHasRequestedCameraPermission) {
				mHasRequestedCameraPermission = true;

				if (ActivityCompat.shouldShowRequestPermissionRationale(ConnectionSetupActivity.this,
						Manifest.permission.CAMERA)) {
					AlertDialog.Builder builder = new AlertDialog.Builder(ConnectionSetupActivity.this);
					builder.setTitle(R.string.title_camera_access);
					builder.setMessage(R.string.hint_enable_camera_access);
					builder.setPositiveButton(R.string.hint_ask_again_permissions, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							ActivityCompat.requestPermissions(ConnectionSetupActivity.this, new String[]{
									Manifest.permission.CAMERA
							}, CAMERA_PERMISSION_RESULT);
						}
					});
					builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Toast.makeText(ConnectionSetupActivity.this, R.string.error_accessing_camera, Toast.LENGTH_LONG)
									.show();
							restartCaptureManager(); // reset capture UI and try again
						}
					});
					builder.show();
				} else {
					ActivityCompat.requestPermissions(ConnectionSetupActivity.this, new String[]{ Manifest.permission.CAMERA },
							CAMERA_PERMISSION_RESULT);
				}
			}
			return false;
		}
		return true;
	}

	// after API 23, we need to handle on-demand permissions requests
	private boolean locationPermissionGranted() {
		if (ContextCompat.checkSelfPermission(ConnectionSetupActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
				PackageManager.PERMISSION_GRANTED) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(ConnectionSetupActivity.this,
					Manifest.permission.ACCESS_COARSE_LOCATION)) {

				AlertDialog.Builder builder = new AlertDialog.Builder(ConnectionSetupActivity.this);
				builder.setTitle(R.string.title_coarse_location_access);
				builder.setMessage(R.string.hint_enable_coarse_location_access);
				builder.setPositiveButton(R.string.hint_ask_again_permissions, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						ActivityCompat.requestPermissions(ConnectionSetupActivity.this, new String[]{
								Manifest.permission.ACCESS_COARSE_LOCATION
						}, COARSE_LOCATION_PERMISSION_RESULT);
					}
				});
				builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Toast.makeText(ConnectionSetupActivity.this, R.string.error_accessing_location, Toast.LENGTH_LONG).show();
						restartCaptureManager(); // reset capture UI and try again
					}
				});
				builder.show();
			} else {
				ActivityCompat.requestPermissions(ConnectionSetupActivity.this, new String[]{
						Manifest.permission.ACCESS_COARSE_LOCATION
				}, COARSE_LOCATION_PERMISSION_RESULT);
			}
			return false;
		}
		return true;
	}

	// TODO: improve all permissions aspects (including camera)
	private boolean canWriteSettings() {
		// TODO: differentiate between M/N? (N and after doesn't require this)... but shouldn't ever get asked by service...

		//noinspection SimplifiableIfStatement
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			return true;
		}
		return Settings.System.canWrite(ConnectionSetupActivity.this);
	}

	// after API 23, we need to handle on-demand permissions requests
	private boolean checkSettingsAccess() {
		// TODO: by default, applications are not granted access, but the settings switch shows access is allowed - need to
		// TODO: toggle this switch to actually grant access. Need to improve this interaction generally
		if (!canWriteSettings()) {
			AlertDialog.Builder builder = new AlertDialog.Builder(ConnectionSetupActivity.this);
			builder.setTitle(R.string.title_settings_access);
			builder.setMessage(R.string.hint_enable_settings_access);
			builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					// note: dismiss rather than cancel so we always take this action (pos or neg result)
					if (checkSettingsAccess()) {
						if (mConnectionMode == ConnectionMode.CONNECTION_INITIATED_HOTSPOT) {
							createHotspot();
							return;
						}
					}
					mConnectionMode = ConnectionMode.SCANNING;
					setupDefaultUI();
				}
			});
			builder.setPositiveButton(R.string.hint_edit_settings_access, new DialogInterface.OnClickListener() {
				@TargetApi(Build.VERSION_CODES.M)
				@Override
				public void onClick(DialogInterface dialog, int which) {
					try {
						Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
						settingsIntent.setData(Uri.parse("package:" + PluginIntent.HOST_PACKAGE));
						startActivity(settingsIntent);
					} catch (ActivityNotFoundException e) {
						Toast.makeText(ConnectionSetupActivity.this, R.string.error_editing_settings, Toast.LENGTH_LONG).show();
						// TODO: reset ui?
					}
					dialog.dismiss();
				}
			});
			builder.setNeutralButton(R.string.button_done, null);
			builder.show();
			return false;
		}
		return true;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch (requestCode) {
			case CAMERA_PERMISSION_RESULT:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					restartCaptureManager();
				} else {
					AlertDialog.Builder builder = new AlertDialog.Builder(ConnectionSetupActivity.this);
					builder.setTitle(R.string.title_camera_access);
					builder.setMessage(R.string.hint_enable_camera_access);
					builder.setPositiveButton(R.string.hint_edit_permissions, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Intent intent = new Intent();
							intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
							intent.setData(Uri.fromParts("package", ConnectionSetupActivity.this.getPackageName(), null));
							try {
								startActivity(intent);
							} catch (ActivityNotFoundException e) {
								// we've tried everything by this point!
								Log.d(TAG, "Camera permission denied and request failed - will not be able to scan codes");
								Toast.makeText(ConnectionSetupActivity.this, R.string.error_accessing_camera, Toast.LENGTH_LONG)
										.show();
							}
							restartCaptureManager();
						}
					});
					builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Toast.makeText(ConnectionSetupActivity.this, R.string.error_accessing_location, Toast.LENGTH_LONG)
									.show();
							restartCaptureManager(); // reset capture UI and try again
						}
					});
					builder.show();
				}
				break;

			case COARSE_LOCATION_PERMISSION_RESULT:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					createClient();
				} else {
					AlertDialog.Builder builder = new AlertDialog.Builder(ConnectionSetupActivity.this);
					builder.setTitle(R.string.title_coarse_location_access);
					builder.setMessage(R.string.hint_enable_coarse_location_access);
					builder.setPositiveButton(R.string.hint_edit_permissions, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Intent intent = new Intent();
							intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
							intent.setData(Uri.fromParts("package", ConnectionSetupActivity.this.getPackageName(), null));
							try {
								startActivity(intent);
							} catch (ActivityNotFoundException e) {
								// we've tried everything by this point!
								Log.d(TAG, "Coarse location permission denied and request failed - will not be able to connect");
								Toast.makeText(ConnectionSetupActivity.this, R.string.error_accessing_location,
										Toast.LENGTH_LONG)
										.show();
							}
							restartCaptureManager();
						}
					});
					builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Toast.makeText(ConnectionSetupActivity.this, R.string.error_accessing_location, Toast.LENGTH_LONG)
									.show();
							restartCaptureManager(); // reset capture UI and try again
						}
					});
					builder.show();
				}
				break;

			default:
				if (mCaptureManager != null && requestCode == CaptureManager.getCameraPermissionReqCode()) {
					// ignored - CaptureManager's default is to exit on permission denial, so we handle permissions ourselves
					// mCaptureManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
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

	@Override
	public void onBackPressed() {
		switch (mConnectionMode) {
			case CONNECTION_INITIATED_HOTSPOT:
				mConnectionMode = ConnectionMode.SCANNING;
				sendSystemMessage(HotspotManagerService.MSG_DISABLE_HOTSPOT, null);
				setHotspotUrl(null);
				setupDefaultUI();
				break;
			case CONNECTION_INITIATED_CLIENT:
				mConnectionMode = ConnectionMode.SCANNING;
				setHotspotUrl(null);
				setupDefaultUI();
				break;
			case SCANNING:
				super.onBackPressed();
				break;
			default:
				break;
		}
	}

	private static class CustomCaptureManager extends CaptureManager {

		private BarcodeCallback mCallback;

		CustomCaptureManager(Activity activity, CompoundBarcodeView barcodeView, BarcodeCallback callback) {
			super(activity, barcodeView);
			mCallback = callback;
		}

		@Override
		protected void returnResult(BarcodeResult rawResult) {
			mCallback.barcodeResult(rawResult);
		}
	}
}
