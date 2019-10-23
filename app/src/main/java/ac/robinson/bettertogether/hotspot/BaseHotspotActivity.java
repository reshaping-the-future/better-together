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

package ac.robinson.bettertogether.hotspot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.Map;

import ac.robinson.bettertogether.PluginHostActivity;
import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.api.messaging.PluginIntent;
import ac.robinson.bettertogether.host.Plugin;
import ac.robinson.bettertogether.host.PluginFinder;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseHotspotActivity extends AppCompatActivity implements HotspotManagerServiceCommunicator.HotspotServiceCallback {

	private static final String TAG = "BaseHotspotActivity";

	private static final String HOTSPOT_URL = "hotspot_url";

	private HotspotManagerServiceCommunicator mServiceCommunicator;

	private String mHotspotUrl;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mServiceCommunicator = new HotspotManagerServiceCommunicator(BaseHotspotActivity.this);
		mServiceCommunicator.bindService(BaseHotspotActivity.this);

		// track package (apk) add/remove events to update plugins
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
		intentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
		intentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
		intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		intentFilter.addDataScheme("package");
		registerReceiver(mPackageEventReceiver, intentFilter);

		if (savedInstanceState != null) {
			mHotspotUrl = savedInstanceState.getString("mHotspotUrl");
		} else {
			Bundle extras = getIntent().getExtras();
			if (extras != null) {
				mHotspotUrl = extras.getString(HOTSPOT_URL);
			}
		}

		sendSystemMessage(HotspotManagerService.MSG_REQUEST_STATUS, null); // check we haven't missed a connect/disconnect msg
	}

	private BroadcastReceiver mPackageEventReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			switch (action) {
				case Intent.ACTION_PACKAGE_ADDED: // TODO: do we get both REPLACED and ADDED when replacing?
				case Intent.ACTION_PACKAGE_CHANGED:
				case Intent.ACTION_PACKAGE_REPLACED:
				case Intent.ACTION_PACKAGE_REMOVED:
					// Log.d(TAG, "Package change" + intent.getDataString());
					String pluginPackage = intent.getDataString();
					if (!TextUtils.isEmpty(pluginPackage)) {
						pluginPackage = pluginPackage.replace("package:", "").trim();
						if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
							// always update on removal - we can't tell if it is a valid package without maintaining a cached
							// list of plugins (as it no-longer exists for discovery after removal)
							pluginUpdated(pluginPackage);
						} else {
							Map<String, Plugin> plugins = PluginFinder.getValidPlugins(BaseHotspotActivity.this, pluginPackage);
							if (plugins.containsKey(pluginPackage)) {
								// the updated package is a valid plugin - update our lists - note that this will not work for
								// inbuilt plugins, as their keys don't match the package name... but we never get add/remove
								// events for them, so this is ok
								pluginUpdated(pluginPackage);
							}
						}
					}
					break;

				default:
					break;
			}
		}
	};

	protected void pluginUpdated(String pluginPackage) {
		// nothing to do here, but subclassing activities may need to update
	}

	protected void launchPluginAndFinish(String hotspotUrl, boolean isInternalPlugin) {
		Log.d(TAG, "Launching plugin: " + hotspotUrl);

		Intent intent = new Intent(BaseHotspotActivity.this, PluginHostActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(PluginHostActivity.EXTRA_HOTSPOT_URL, hotspotUrl);

		try {
			ConnectionOptions currentConnectionOptions = null;
			boolean validatedInternalPlugin = isInternalPlugin;
			if (isInternalPlugin) {
				currentConnectionOptions = ConnectionOptions.fromHotspotUrl(hotspotUrl);
				if (currentConnectionOptions != null) {
					currentConnectionOptions.mPluginPackage = PluginIntent.HOST_PACKAGE;
				} else {
					validatedInternalPlugin = false; // TODO: can we do anything better?
				}
			}

			sendSystemMessage(HotspotManagerService.MSG_UPDATE_HOTSPOT, validatedInternalPlugin ?
					currentConnectionOptions.getHotspotUrl() : hotspotUrl); // update plugin package in service - for inbuilt
			// plugins need host package
			startActivity(intent);
			finish(); // TODO: on slower devices do we need to wait until the new activity is definitely connected before this?
		} catch (Exception ignored) {
			Toast.makeText(BaseHotspotActivity.this, R.string.hint_error_launching_plugin, Toast.LENGTH_SHORT).show();
		}
	}

	protected void launchGetPluginsActivity() {
		Intent intent = new Intent(Intent.ACTION_VIEW, PluginIntent.MARKET_PLUGIN_SEARCH);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		try {
			startActivity(intent);
		} catch (Exception ignored) {
			Toast.makeText(BaseHotspotActivity.this, R.string.hint_error_launching_play_store, Toast.LENGTH_SHORT).show();
		}
	}

	protected void setHotspotUrl(String hotspotUrl) {
		mHotspotUrl = hotspotUrl;
	}

	protected String getHotspotUrl() {
		return mHotspotUrl;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString("mHotspotUrl", mHotspotUrl);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mPackageEventReceiver);
		if (mServiceCommunicator != null) {
			mServiceCommunicator.unbindService(BaseHotspotActivity.this, !isFinishing());  // don't kill service on rotation
		}
	}

	protected void sendSystemMessage(int type, String data) {
		mServiceCommunicator.sendSystemMessage(type, data);
	}

	@Override
	public void onSystemMessageReceived(int type, String data) {
		// nothing to do here - overriding activities may need to finish() or update UI on some system messages
	}

	protected void sendBroadcastMessage(BroadcastMessage message) {
		mServiceCommunicator.sendBroadcastMessage(message);
	}

	@Override
	public abstract void onBroadcastMessageReceived(BroadcastMessage message);
}
