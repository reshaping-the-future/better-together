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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.UUID;

import ac.robinson.bettertogether.BetterTogetherUtils;
import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.api.messaging.PluginIntent;
import ac.robinson.bettertogether.event.ClientConnectionErrorEvent;
import ac.robinson.bettertogether.event.ClientConnectionSuccessEvent;
import ac.robinson.bettertogether.event.ClientMessageErrorEvent;
import ac.robinson.bettertogether.event.MessageReceivedEvent;
import ac.robinson.bettertogether.event.ServerConnectionSuccessEvent;
import ac.robinson.bettertogether.event.ServerErrorEvent;
import ac.robinson.bettertogether.event.ServerMessageErrorEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class HotspotManagerService extends Service {

	private static final String TAG = "HotspotManagerService";

	// with Android N and later, apps can't start access points - see: https://code.google.com/p/android/issues/detail?id=234003
	private static final boolean CREATE_WIFI_HOTSPOT_SUPPORTED = Build.VERSION.SDK_INT < Build.VERSION_CODES.N;
	private static final String HOTSPOT_STATE_FILTER = "android.net.wifi.WIFI_AP_STATE_CHANGED"; // private access in source

	// for tracking server/client state (note: MESSAGE_ID_SIZE minimum of 8 is for Wifi password length requirements)
	public static final int MESSAGE_ID_SIZE = 8; // number of digits to use for hotspot / server / message id - * minimum: 8 *
	public static final String SERVER_MESSAGE_ID = "BTplhost"; // must be constant over all clients
	public static final UUID BLUETOOTH_SERVER_UUID = UUID.fromString("07eb2627-3de9-4ae4-b6a9-cbb282a6363f"); // must be constant
	public static final int MESSAGE_BUFFER_SIZE = 2048;
	public static final int MESSAGE_PART_COUNT_SIZE = 2; // up to 10^n parts of MESSAGE_BUFFER_SIZE (when Base64-encoded)
	public static final int MESSAGE_HEADER_SIZE = MESSAGE_ID_SIZE + (2 * MESSAGE_PART_COUNT_SIZE); // id; part count; part num
	public static final int MESSAGE_PAYLOAD_SIZE = MESSAGE_BUFFER_SIZE - MESSAGE_HEADER_SIZE - 1; // 1 char for \n
	public static final byte MESSAGE_DELIMITER_BYTE = (byte) '\f'; // have to use \f as \r and \n are part of base64
	public static final String MESSAGE_DELIMITER_STRING = Character.toString((char) MESSAGE_DELIMITER_BYTE);

	// for managing wifi/bluetooth connections
	private WifiManager mWifiManager;
	private BluetoothAdapter mBluetoothAdapter;

	// for trying to restore the previous state on exit
	private boolean mOriginalBluetoothStatus = false;
	private String mOriginalBluetoothName = null;
	private boolean mOriginalWifiStatus = false;
	private ConnectionOptions mOriginalHotspotConfiguration = null;

	// for managing connection states
	private ConnectionOptions mConnectionOptions = null;
	private int mHotspotId = -1; // separate so we can unlink after destroying the hotspot
	private WifiServer mWifiServer;
	private WifiClientConnection mWifiClient = null;
	private BluetoothServer mBluetoothServer;
	private BluetoothClientConnection mBluetoothClient;
	private boolean mHotspotMode;
	private boolean mIsConnected;

	// for tracking errors
	private ErrorHandler mConnectionErrorHandler = new ErrorHandler();
	private int mWifiConnectionErrorCount = 0;
	private int mBluetoothConnectionErrorCount = 0;
	private static final int WIFI_CONNECTION_TIMEOUT = 15000; // milliseconds
	private static final int BLUETOOTH_CONNECTION_TIMEOUT = 18000; // milliseconds
	private static final float CONNECTION_DELAY_INCREMENT_MULTIPLIER = 1.3f; // multiply delay by this on every failure
	private static final int MSG_WIFI_CONNECTION_ERROR = 1;
	private static final int MSG_BLUETOOTH_CONNECTION_ERROR = 2;
	private int mWifiConnectionTimeout = WIFI_CONNECTION_TIMEOUT;
	private int mBluetoothConnectionTimeout = BLUETOOTH_CONNECTION_TIMEOUT;

	// service messages and communication
	private boolean mIsBound = false;
	private final Messenger mMessenger;
	private ArrayList<Messenger> mClients = new ArrayList<>();
	private HandlerThread mMessageThread;
	private Handler mMessageThreadHandler;

	public static final int MSG_REGISTER_CLIENT = 1; // service management
	public static final int MSG_UNREGISTER_CLIENT = 2; // service management

	public static final int MSG_ENABLE_HOTSPOT = 3; // hotspot management
	public static final int MSG_DISABLE_HOTSPOT = 4; // hotspot management
	public static final int MSG_JOIN_HOTSPOT = 5; // hotspot management
	public static final int MSG_UPDATE_HOTSPOT = 6; // hotspot management
	public static final int MSG_REQUEST_STATUS = 7; // hotspot management

	public static final int MSG_BROADCAST = 8; // messages to remote clients

	public static final int EVENT_DEVICE_CONNECTED = 9; // events sent as messages to local clients
	public static final int EVENT_LOCAL_CLIENT_ERROR = 10;
	public static final int EVENT_REMOTE_CLIENT_ERROR = 11;
	public static final int EVENT_DEVICE_DISCONNECTED = 12;

	public static final int EVENT_CONNECTION_STATUS_UPDATE = 13; // events sent as messages to local clients
	public static final int EVENT_CONNECTION_INVALID_URL = 14;
	public static final int EVENT_SETTINGS_PERMISSION_ERROR = 15;

	public static final String ROLE_SERVER = "server";
	public static final String ROLE_CLIENT = "client";
	public static final String SYSTEM_BROADCAST_EVENT_SHOW_QR_CODE = "show_qr";

	public HotspotManagerService() {
		// initialise the handler - actual service is initialised in onBind as we can't get a context here
		mMessenger = new Messenger(new IncomingHandler(HotspotManagerService.this));
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		if (!mIsBound) {
			//TODO: check mBluetoothAdapter not null and/or check bluetooth is available - BluetoothUtils.isBluetoothAvailable()
			mBluetoothAdapter = BluetoothUtils.getBluetoothAdapter(HotspotManagerService.this);
			mOriginalBluetoothStatus = mBluetoothAdapter.isEnabled();
			mOriginalBluetoothName = mBluetoothAdapter.getName();

			//TODO: check mWifiManager not null and/or check bluetooth is available - WifiUtils.isWifiAvailable()
			// note we need the WifiManager for connecting to other hotspots regardless of whether we can create our own
			mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

			// try to get the original state to restore later
			int wifiState = mWifiManager.getWifiState();
			switch (wifiState) {
				case WifiManager.WIFI_STATE_ENABLED:
				case WifiManager.WIFI_STATE_ENABLING:
					mOriginalWifiStatus = true;
					break;
				case WifiManager.WIFI_STATE_DISABLED:
				case WifiManager.WIFI_STATE_DISABLING:
				case WifiManager.WIFI_STATE_UNKNOWN:
					mOriginalWifiStatus = false;
					break;
				default:
					break;
			}

			// try to save the existing hotspot state
			if (CREATE_WIFI_HOTSPOT_SUPPORTED) {
				try {
					// TODO: is it possible to save/restore the original password? (WifiConfiguration doesn't hold the password)
					WifiConfiguration wifiConfiguration = WifiUtils.getWifiHotspotConfiguration(mWifiManager);
					mOriginalHotspotConfiguration = new ConnectionOptions();
					mOriginalHotspotConfiguration.mName = wifiConfiguration.SSID;
				} catch (Exception ignored) {
					// note - need to catch Exception rather than ReflectiveOperationException due to our API level (requires 19)
				}
			}

			// set up background thread for message sending - see: https://medium.com/@ali.muzaffar/dc8bf1540341
			mMessageThread = new HandlerThread("BTMessageThread");
			mMessageThread.start();
			mMessageThreadHandler = new Handler(mMessageThread.getLooper());

			// set up listeners for network/bluetooth state changes
			IntentFilter intentFilter = new IntentFilter();
			if (CREATE_WIFI_HOTSPOT_SUPPORTED) {
				intentFilter.addAction(HOTSPOT_STATE_FILTER); // Wifi hotspot states
			}
			intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION); // Wifi on/off
			intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION); // network connection/disconnection
			intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED); // Bluetooth on/off
			intentFilter.addAction(BluetoothDevice.ACTION_FOUND); // Bluetooth device found
			registerReceiver(mGlobalBroadcastReceiver, intentFilter);

			// listen for messages from our PluginMessageReceiver
			IntentFilter localIntentFilter = new IntentFilter();
			localIntentFilter.addAction(PluginIntent.ACTION_MESSAGE_RECEIVED);
			localIntentFilter.addAction(PluginIntent.ACTION_STOP_PLUGIN);
			LocalBroadcastManager.getInstance(HotspotManagerService.this)
					.registerReceiver(mLocalBroadcastReceiver, localIntentFilter);

			// listen for EventBus events (from wifi/bluetooth servers)
			if (!EventBus.getDefault().isRegistered(HotspotManagerService.this)) {
				EventBus.getDefault().register(HotspotManagerService.this);
			}

			mIsBound = true;
		}

		return mMessenger.getBinder();
	}

	@Override
	public void onDestroy() {
		// remove all event listeners
		EventBus.getDefault().unregister(HotspotManagerService.this);
		LocalBroadcastManager.getInstance(HotspotManagerService.this).unregisterReceiver(mLocalBroadcastReceiver);
		unregisterReceiver(mGlobalBroadcastReceiver);
		mMessageThread.quit(); // (we don't need quitSafely() as messages don't need to be delivered)

		destroyAllConnections();

		restoreOriginalWifiState();
		restoreOriginalBluetoothState();
	}

	private void restoreOriginalWifiState() {
		// we used to actually remove the network here - this is not done any more so that repeat wifi connections are faster
		try {
			mWifiManager.disableNetwork(mHotspotId); // on later Android versions, Wifi connections with no access do this anyway
			mWifiManager.setWifiEnabled(mOriginalWifiStatus);
			if (mOriginalWifiStatus) {
				mWifiManager.reconnect(); // TODO: do we need to save/restore the original network name, too?
				// TODO: (e.g., save current wifi connection ID before adding our own, then check its new ID and restore after)
			}
		} catch (SecurityException ignored) {
		}
	}

	private void restoreOriginalWifiHotspotState() {
		if (CREATE_WIFI_HOTSPOT_SUPPORTED) {
			// reset to original name; must leave disabled as we don't know the original password...
			if (WifiUtils.getHotspotEnabled(mWifiManager)) {
				try {
					WifiConfiguration wifiConfiguration = null;
					try {
						// see: https://stackoverflow.com/questions/2140133/
						wifiConfiguration = WifiUtils.getWifiHotspotConfiguration(mWifiManager);
						WifiUtils.setConfigurationAttributes(wifiConfiguration, mOriginalHotspotConfiguration.mName,
								mOriginalHotspotConfiguration.mPassword);
					} catch (Exception ignored) {
					}
					// separate try/catch so we at least disable the hotspot even if we can't set the configuration
					WifiUtils.setHotspotEnabled(mWifiManager, wifiConfiguration, false);
				} catch (Exception ignored) {
					// note - need to catch Exception rather than ReflectiveOperationException due to our API level (requires 19)
				}
			}
		}
	}

	private void restoreOriginalBluetoothState() {
		// disable bluetooth if necessary
		try {
			mBluetoothAdapter.setName(mOriginalBluetoothName);
			if (!mOriginalBluetoothStatus) {
				mBluetoothAdapter.disable();
			}
		} catch (SecurityException ignored) {
		}
	}

	private void configureAndStartBluetoothHotspot(ConnectionOptions connectionOptions) {
		mBluetoothAdapter.setName(connectionOptions.mName);
		BluetoothUtils.setDiscoverable(HotspotManagerService.this, mBluetoothAdapter);
		startBluetoothServer();
	}

	private void startBluetoothServer() {
		mBluetoothServer = new BluetoothServer(mBluetoothAdapter);
		new Thread(mBluetoothServer).start();
	}

	private void configureAndStartWifiHotspot(ConnectionOptions connectionOptions) throws NoSuchMethodException,
			InvocationTargetException, IllegalAccessException {
		WifiConfiguration wifiConfiguration = null;
		try {
			// see: https://stackoverflow.com/questions/2140133/
			wifiConfiguration = WifiUtils.getWifiHotspotConfiguration(mWifiManager);
			WifiUtils.setConfigurationAttributes(wifiConfiguration, connectionOptions.mName, connectionOptions.mPassword);
		} catch (Exception e) {
			e.printStackTrace(); // unable to get configuration
		}

		WifiUtils.setHotspotEnabled(mWifiManager, wifiConfiguration, true);
	}

	private void startWifiServer() {
		mWifiServer = new WifiServer(ConnectionOptions.DEFAULT_HOTSPOT_IP_ADDRESS, ConnectionOptions.DEFAULT_HOTSPOT_PORT);
		new Thread(mWifiServer).start();
	}

	// TODO: Wifi configuration seems to be ignored when turning off - does this matter?
	private void initialiseHotspots(@Nullable ConnectionOptions connectionOptions) throws NoSuchMethodException,
			InvocationTargetException, IllegalAccessException {

		mHotspotMode = true;

		if (CREATE_WIFI_HOTSPOT_SUPPORTED) {
			configureAndStartWifiHotspot(connectionOptions);
		}

		switch (mBluetoothAdapter.getState()) {
			case BluetoothAdapter.STATE_ON:
				configureAndStartBluetoothHotspot(connectionOptions);
				break;

			case BluetoothAdapter.STATE_TURNING_ON:
				break; // finish configuration in receiver

			case BluetoothAdapter.STATE_TURNING_OFF:
			case BluetoothAdapter.STATE_OFF:
			default:
				mBluetoothAdapter.enable(); // finish configuration in receiver
		}
	}

	private void destroyAllConnections() {
		mConnectionErrorHandler.removeMessages(MSG_WIFI_CONNECTION_ERROR);
		mConnectionErrorHandler.removeMessages(MSG_BLUETOOTH_CONNECTION_ERROR);
		mBluetoothAdapter.cancelDiscovery();
		mHotspotMode = false;
		mIsConnected = false;
		mWifiConnectionErrorCount = 0;
		mBluetoothConnectionErrorCount = 0;

		if (CREATE_WIFI_HOTSPOT_SUPPORTED) {
			// disconnect all servers/clients
			if (mWifiServer != null) {
				mWifiServer.closeAllConnections();
				mWifiServer = null;
			}
		}
		if (mWifiClient != null) {
			mWifiClient.closeConnection();
			mWifiClient = null;
		}
		if (mBluetoothServer != null) {
			mBluetoothServer.closeAllConnections();
			mBluetoothServer = null;
		}
		if (mBluetoothClient != null) {
			mBluetoothClient.closeConnection();
			mBluetoothClient = null;
		}

		restoreOriginalWifiHotspotState();

		mConnectionOptions = null;
	}

	private void connectWifiHotspot(@NonNull ConnectionOptions options) {
		Log.d(TAG, "Attempting connection via Wifi");
		switch (mWifiManager.getWifiState()) {
			case WifiManager.WIFI_STATE_ENABLED:
				Log.d(TAG, "Completing hotspot connection");
				finishConnectingWifiHotspot(options);
				break;

			case WifiManager.WIFI_STATE_ENABLING:
				Log.d(TAG, "Waiting for Wifi to be enabled");
				break; // will connect in receiver

			case WifiManager.WIFI_STATE_DISABLING:
			case WifiManager.WIFI_STATE_DISABLED:
			case WifiManager.WIFI_STATE_UNKNOWN:
			default:
				Log.d(TAG, "Enabling Wifi");
				mWifiManager.setWifiEnabled(true); // will connect in receiver
				break;
		}
	}

	private void connectBluetoothHotspot(@NonNull ConnectionOptions options) {
		Log.d(TAG, "Attempting connection via Bluetooth");
		switch (mBluetoothAdapter.getState()) {
			case BluetoothAdapter.STATE_ON:
				Log.d(TAG, "Starting Bluetooth discovery");
				mBluetoothAdapter.cancelDiscovery();
				mBluetoothAdapter.startDiscovery();
				setBluetoothErrorTimeout();
				break;

			case BluetoothAdapter.STATE_TURNING_ON:
				Log.d(TAG, "Waiting for Bluetooth to be enabled");
				break; // start discovery in receiver

			case BluetoothAdapter.STATE_OFF:
			case BluetoothAdapter.STATE_TURNING_OFF:
			default:
				Log.d(TAG, "Enabling Bluetooth / waiting for receiver to enable");
				mBluetoothAdapter.enable(); // start discovery in receiver
		}
	}

	private void finishConnectingWifiHotspot(@NonNull ConnectionOptions connectionOptions) {
		// set up new network - *must* be surrounded by " (see: https://stackoverflow.com/questions/2140133/)
		Log.d(TAG, "Connecting to Wifi network " + connectionOptions.mName);
		WifiConfiguration wifiConfiguration = new WifiConfiguration();
		WifiUtils.setConfigurationAttributes(wifiConfiguration,
				"\"" + connectionOptions.mName + "\"", "\"" + connectionOptions.mPassword + "\"");

		int savedNetworkId = WifiUtils.getWifiNetworkId(mWifiManager, wifiConfiguration.SSID);
		if (savedNetworkId >= 0) {
			Log.d(TAG, "Found saved Wifi network id");
			mHotspotId = savedNetworkId;
		} else {
			Log.d(TAG, "Adding Wifi network");
			mHotspotId = mWifiManager.addNetwork(wifiConfiguration);
			// mWifiManager.saveConfiguration(); // can change network IDs(!) - not really needed, so disabled
			if (mHotspotId < 0) {
				Log.d(TAG, "Couldn't add Wifi network");
				mWifiConnectionErrorCount += 1;
				retryWifiConnection();
				return;
			}
		}

		// if we're auto-connected to a previous network (unlikely!), continue straight away; if not, reconnect
		WifiInfo currentConnection = mWifiManager.getConnectionInfo();
		if (currentConnection != null && wifiConfiguration.SSID.equals(currentConnection.getSSID())) {
			Log.d(TAG, "Continuing with current Wifi connection");
			connectWifiClient(connectionOptions.mIPAddress, connectionOptions.mPort);
		} else {
			Log.d(TAG, "Enabling Wifi network");
			mWifiManager.disconnect();
			if (!mWifiManager.enableNetwork(mHotspotId, true)) { // connect to our network - handle connection in receiver
				Log.d(TAG, "Couldn't enable Wifi network");
				mWifiConnectionErrorCount += 1;
				retryWifiConnection();
			} else {
				Log.d(TAG, "Wifi network enabled");
				mWifiManager.reconnect();
				setWifiErrorTimeout();
			}
		}
	}

	private void connectWifiClient(@NonNull String ip, int port) {
		if (mWifiClient != null) {
			mWifiClient.closeConnection();
			mWifiClient = null;
		}

		sendSystemMessageToAllLocalClients(EVENT_CONNECTION_STATUS_UPDATE, "Connecting using Wifi...");
		Log.d(TAG, "Starting Wifi client connection");
		mWifiClient = new WifiClientConnection(ip, port);
		new Thread(mWifiClient).start();
		setWifiErrorTimeout(); // reset/increase the timeout to give the connection time to succeed
		Log.d(TAG, "Wifi client connection started");
	}

	private void connectBluetoothClient(@NonNull BluetoothDevice device) {
		mBluetoothAdapter.cancelDiscovery(); // no need to keep searching
		if (mBluetoothClient != null) {
			mBluetoothClient.closeConnection();
			mBluetoothClient = null;
		}

		sendSystemMessageToAllLocalClients(EVENT_CONNECTION_STATUS_UPDATE, "Connecting using Bluetooth...");
		Log.d(TAG, "Starting Bluetooth client connection");
		mBluetoothClient = new BluetoothClientConnection(device);
		new Thread(mBluetoothClient).start();
		setBluetoothErrorTimeout(); // reset/increase the timeout to give the connection time to succeed
		Log.d(TAG, "Bluetooth client connection started");
	}

	private BroadcastReceiver mGlobalBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			switch (intent.getAction()) {
				case HOTSPOT_STATE_FILTER: // see: http://stackoverflow.com/a/14681207
					int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
					if (state % 10 == WifiManager.WIFI_STATE_ENABLED) { // TODO: should we detect manual disable events?
						if (CREATE_WIFI_HOTSPOT_SUPPORTED) {
							startWifiServer();
						}
					}
					break;

				case WifiManager.WIFI_STATE_CHANGED_ACTION:
					Log.d(TAG, "Wifi state changed to: " +
							intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
					switch (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)) {
						case WifiManager.WIFI_STATE_DISABLED:
							// TODO: can we do this only when needed? (e.g., don't fight the user)
							if (!mIsConnected && mConnectionOptions != null) {
								mWifiManager.setWifiEnabled(true); // finish connection when turned on (below)
							}
							break;

						case WifiManager.WIFI_STATE_ENABLED:
							Log.d(TAG, "Wifi enabled");
							if (mHotspotMode) {
								// nothing do do in hotspot mode - in fact, this shouldn't happen as most devices require Wifi
								// to be off before starting a hotspot
							} else {
								if (!mIsConnected && mConnectionOptions != null) {
									finishConnectingWifiHotspot(mConnectionOptions);
								}
							}
							break;

						default:
							break;
					}
					break;

				case BluetoothAdapter.ACTION_STATE_CHANGED:
					Log.d(TAG, "Bluetooth state changed to: " + intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
					switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
						case BluetoothAdapter.STATE_OFF:
							// TODO: can we do this only when needed? (e.g., don't fight the user)
							if (!mIsConnected && mConnectionOptions != null) {
								mBluetoothAdapter.enable(); // start discovery when turned on (below)
							}
							break;

						case BluetoothAdapter.STATE_ON:
							Log.d(TAG, "Bluetooth enabled");
							if (mHotspotMode) {
								configureAndStartBluetoothHotspot(mConnectionOptions);
							} else {
								if (!mIsConnected && mConnectionOptions != null) {
									Log.d(TAG, "Starting Bluetooth discovery (2)");
									mBluetoothAdapter.cancelDiscovery();
									mBluetoothAdapter.startDiscovery();
									setBluetoothErrorTimeout();
								}
							}
							break;

						default:
							break;
					}
					break;

				case WifiManager.NETWORK_STATE_CHANGED_ACTION:
					Log.d(TAG, "Wifi network state changed");
					if (mHotspotMode) {
						// nothing to do
					} else if (!mIsConnected && mConnectionOptions != null) {
						boolean isConnected = false;
						String networkName1 = null;
						NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
						if (networkInfo != null) {
							networkName1 = BetterTogetherUtils.trimQuotes(networkInfo.getExtraInfo());
							isConnected = networkInfo.isConnected();
						}
						String networkName2 = null;
						WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
						if (wifiInfo != null) {
							networkName2 = BetterTogetherUtils.trimQuotes(wifiInfo.getSSID());
							// isConnected = true; // not necessarily the case - could be connecting
						}
						Log.d(TAG, "(State change for network: " + networkName1 + " / " + networkName2 + "; connected: " +
								isConnected + " - searching for " + mConnectionOptions.mName + ")");
						if (isConnected && (mConnectionOptions.mName.equals(networkName1) ||
								mConnectionOptions.mName.equals(networkName2)) && mWifiClient == null) {
							Log.d(TAG, "Continuing with current Wifi connection (2)");
							connectWifiClient(mConnectionOptions.mIPAddress, mConnectionOptions.mPort);
						}
					}
					break;

				case BluetoothDevice.ACTION_FOUND: // TODO: handle ACTION_BOND_STATE_CHANGED too?
					Log.d(TAG, "Bluetooth device found");
					if (mHotspotMode) {
						// nothing to do
					} else if (!mIsConnected && mConnectionOptions != null) { // client mode
						BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
						Log.d(TAG, "(Device name: " + device.getName() + " - searching for " + mConnectionOptions.mName + ")");
						if (mConnectionOptions.mName.equals(device.getName()) && mBluetoothClient == null) {
							Log.d(TAG, "Connecting to Bluetooth device");
							connectBluetoothClient(device);
						}
					}
					break;

				default:
					break;
			}
		}
	};

	private class ErrorHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MSG_WIFI_CONNECTION_ERROR:
					Log.d(TAG, "Wifi connection error");
					mWifiConnectionErrorCount += 1;
					retryWifiConnection();
					break;

				case MSG_BLUETOOTH_CONNECTION_ERROR:
					Log.d(TAG, "Bluetooth connection error");
					mBluetoothConnectionErrorCount += 1;
					retryBluetoothConnection();
					break;

				default:
					break;
			}
		}
	}

	private void setWifiErrorTimeout() {
		Message message = Message.obtain(null, MSG_WIFI_CONNECTION_ERROR);
		mConnectionErrorHandler.sendMessageDelayed(message, mWifiConnectionTimeout);
		mWifiConnectionTimeout *= CONNECTION_DELAY_INCREMENT_MULTIPLIER;
	}

	private void setBluetoothErrorTimeout() {
		Message message = Message.obtain(null, MSG_BLUETOOTH_CONNECTION_ERROR);
		mConnectionErrorHandler.sendMessageDelayed(message, mBluetoothConnectionTimeout);
		mBluetoothConnectionTimeout *= CONNECTION_DELAY_INCREMENT_MULTIPLIER;
	}

	private void retryWifiConnection() {
		sendSystemMessageToAllLocalClients(EVENT_CONNECTION_STATUS_UPDATE,
				"Couldn't connect using Wifi – retrying " + "connection...");
		Log.d(TAG, "Retrying remote Wifi connection");

		mIsConnected = false;
		mConnectionErrorHandler.removeMessages(MSG_WIFI_CONNECTION_ERROR);

		if (mWifiClient != null) {
			mWifiClient.closeConnection();
			mWifiClient = null;
		}

		// after one failure, try restarting the adapters
		if (mWifiConnectionErrorCount > 0) {
			if (mHotspotId >= 0) {
				mWifiManager.removeNetwork(mHotspotId); // sometimes saved networks won't connect - remove and retry
				mWifiManager.saveConfiguration();
			}
			mWifiManager.setWifiEnabled(false);
		}

		connectWifiHotspot(mConnectionOptions);
	}

	private void retryBluetoothConnection() {
		sendSystemMessageToAllLocalClients(EVENT_CONNECTION_STATUS_UPDATE,
				"Couldn't connect using Bluetooth – retrying " + "connection...");
		Log.d(TAG, "Retrying remote Bluetooth connection");

		mIsConnected = false;
		mConnectionErrorHandler.removeMessages(MSG_BLUETOOTH_CONNECTION_ERROR);

		if (mBluetoothClient != null) {
			mBluetoothClient.closeConnection();
			mBluetoothClient = null;
		}

		if (mBluetoothConnectionErrorCount > 0) {
			mBluetoothAdapter.disable();
		}

		connectBluetoothHotspot(mConnectionOptions);
	}

	// handler for messages from local clients (e.g., activities that have connected to this service)
	private static class IncomingHandler extends Handler {
		private final WeakReference<HotspotManagerService> mServiceReference; // so we don't prevent garbage collection

		IncomingHandler(HotspotManagerService instance) {
			mServiceReference = new WeakReference<>(instance);
		}

		@Override
		public void handleMessage(Message msg) {
			HotspotManagerService mService = mServiceReference.get();
			if (mService == null) {
				// TODO: anything to do here?
				return;
			}

			switch (msg.what) {
				case MSG_REGISTER_CLIENT:
					mService.mClients.add(msg.replyTo);
					break;

				case MSG_UNREGISTER_CLIENT:
					mService.mClients.remove(msg.replyTo);
					if (mService.mClients.size() <= 0 && msg.arg1 != 1) { // arg1 == 1 means keep alive (activity is rotating)
						mService.stopSelf(); // so we stop the hotspot and clean up when our final activity is killed
					}
					break;

				case MSG_ENABLE_HOTSPOT:
					mService.destroyAllConnections();
					String enableHotspotUrl = msg.getData().getString(PluginIntent.KEY_SERVICE_MESSAGE);
					if (!TextUtils.isEmpty(enableHotspotUrl)) {
						mService.mConnectionOptions = ConnectionOptions.fromHotspotUrl(enableHotspotUrl);
						if (mService.mConnectionOptions != null) {
							try {
								mService.initialiseHotspots(mService.mConnectionOptions);
								break;
							} catch (Exception e) {
								e.printStackTrace(); // unable to start access point
								mService.sendSystemMessageToAllLocalClients(EVENT_SETTINGS_PERMISSION_ERROR, null);
								break;
							}
						}
					}
					mService.sendSystemMessageToAllLocalClients(EVENT_CONNECTION_INVALID_URL, null); // shouldn't happen...
					break;

				case MSG_DISABLE_HOTSPOT:
					mService.destroyAllConnections();
					mService.sendSystemMessageToAllLocalClients(EVENT_DEVICE_DISCONNECTED, null);
					break;

				case MSG_JOIN_HOTSPOT:
					mService.destroyAllConnections();
					String joinHotspotUrl = msg.getData().getString(PluginIntent.KEY_SERVICE_MESSAGE);
					if (!TextUtils.isEmpty(joinHotspotUrl)) {
						mService.mConnectionOptions = ConnectionOptions.fromHotspotUrl(joinHotspotUrl);
						if (mService.mConnectionOptions != null) {
							mService.sendSystemMessageToAllLocalClients(EVENT_CONNECTION_STATUS_UPDATE, "Searching...");
							mService.connectBluetoothHotspot(mService.mConnectionOptions);
							mService.connectWifiHotspot(mService.mConnectionOptions);
							break;
						}
					}
					mService.sendSystemMessageToAllLocalClients(EVENT_CONNECTION_INVALID_URL, null);
					break;

				case MSG_UPDATE_HOTSPOT: // update connection options - currently just for package name
					String updateHotspotUrl = msg.getData().getString(PluginIntent.KEY_SERVICE_MESSAGE);
					if (!TextUtils.isEmpty(updateHotspotUrl)) {
						ConnectionOptions updatedConnectionOptions = ConnectionOptions.fromHotspotUrl(updateHotspotUrl);
						if (updatedConnectionOptions != null) {
							mService.mConnectionOptions = updatedConnectionOptions;
						}
					}
					break;

				case MSG_REQUEST_STATUS: // send a connection status update (normally used when screen rotates)
					Log.d(TAG, "Status update requested");
					if (mService.mHotspotMode) {
						if (mService.mWifiServer != null || mService.mBluetoothServer != null) {
							mService.sendSystemMessageToAllLocalClients(EVENT_DEVICE_CONNECTED, ROLE_SERVER);
							Log.d(TAG, "Sending server status: connected");
						} else {
							mService.sendSystemMessageToAllLocalClients(EVENT_DEVICE_DISCONNECTED, null);
							Log.d(TAG, "Sending server status: not connected");
						}
					} else {
						if (mService.mWifiClient != null || mService.mBluetoothClient != null) {
							mService.sendSystemMessageToAllLocalClients(EVENT_DEVICE_CONNECTED, ROLE_CLIENT);
							Log.d(TAG, "Sending client status: connected");
						} else {
							mService.sendSystemMessageToAllLocalClients(EVENT_DEVICE_DISCONNECTED, null);
							Log.d(TAG, "Sending client status: not connected");
						}
					}
					break;

				case MSG_BROADCAST: // messages received from our own activities
					Bundle data = msg.getData();
					BroadcastMessage message = (BroadcastMessage) data.getSerializable(PluginIntent.KEY_BROADCAST_MESSAGE);

					// note: internal messages are dealt with by the service locally (but still forwarded to remote clients)
					if (message != null) {
						if (message.isSystemMessage()) {
							mService.handleSystemBroadcastMessage(message);
						}
						mService.sendBroadcastMessageToAllRemoteClients(message);
					}
					break;

				default:
					super.handleMessage(msg);
			}
		}
	}

	private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO: source is only necessary for internal plugins - remove later?
			if (HotspotManagerService.this.getPackageName().equals(intent.getStringExtra(PluginIntent.EXTRA_SOURCE))) {
				Log.d(TAG, "Ignoring message - source is self");
				return;
			}

			switch (intent.getAction()) {
				case PluginIntent.ACTION_MESSAGE_RECEIVED: // messages received from plugin activities
					BroadcastMessage message =
							(BroadcastMessage) intent.getSerializableExtra(PluginIntent.KEY_BROADCAST_MESSAGE);

					// note: internal messages are dealt with by the service locally (but still forwarded to remote clients)
					if (message != null) {
						if (message.isSystemMessage()) {
							handleSystemBroadcastMessage(message);
						}
						sendBroadcastMessageToAllRemoteClients(message);
					}
					break;

				case PluginIntent.ACTION_STOP_PLUGIN:
					// nothing to do (yet)
					break;

				default:
					break;
			}
		}
	};

	// sends a broadcast message (e.g., something from remote clients) to all local clients
	private void sendBroadcastMessageToAllLocalClients(BroadcastMessage msg) {
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				// local directly connected activities (i.e., ours)
				Messenger client = mClients.get(i);
				Message message = Message.obtain(null, MSG_BROADCAST);
				message.replyTo = mMessenger;
				Bundle bundle = new Bundle(1);
				bundle.putSerializable(PluginIntent.KEY_BROADCAST_MESSAGE, msg);
				message.setData(bundle);
				client.send(message);

				// local unconnected activities (i.e., plugins)
				Log.d(TAG, "Sending broadcast message to all local clients with package " + mConnectionOptions.mPluginPackage +
						" - type: " + msg.getType() + ", message: " + msg.getMessage());
				Intent broadcastIntent = new Intent(PluginIntent.ACTION_MESSAGE_RECEIVED);
				broadcastIntent.setClassName(mConnectionOptions.mPluginPackage, PluginIntent.MESSAGE_RECEIVER);
				// TODO: source is only necessary for internal plugins - remove later?
				broadcastIntent.putExtra(PluginIntent.EXTRA_SOURCE, HotspotManagerService.this.getPackageName());
				broadcastIntent.putExtra(PluginIntent.KEY_BROADCAST_MESSAGE, msg);
				sendBroadcast(broadcastIntent);
			} catch (RemoteException e) {
				e.printStackTrace();
				mClients.remove(i); // client is dead - ok to remove here as we're reversing through the list
			}
		}
	}

	// sends a system message (e.g., HotspotManagerService events) to all local clients
	private void sendSystemMessageToAllLocalClients(int type, String data) {
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				Messenger client = mClients.get(i);
				Message message = Message.obtain(null, type);
				message.replyTo = mMessenger;
				if (data != null) {
					Bundle bundle = new Bundle(1);
					bundle.putString(PluginIntent.KEY_SERVICE_MESSAGE, data);
					message.setData(bundle);
				}
				client.send(message);
			} catch (RemoteException e) {
				e.printStackTrace();
				mClients.remove(i); // client is dead - ok to remove here as we're reversing through the list
			}
		}
	}

	// sends a message to every connected remote device
	private void sendBroadcastMessageToAllRemoteClients(BroadcastMessage message) {
		try {
			if (mHotspotMode) { // we are in server mode
				message.setFrom(SERVER_MESSAGE_ID);
			}
			sendBroadcastMessageToAllRemoteClients(BroadcastMessage.toString(message), null);
		} catch (IOException e) {
			Log.d(TAG, "Broadcast message sending error: " + e.getLocalizedMessage());
		}
	}

	// send a message to all connected remote devices, optionally ignoring the client that sent the message
	// note - we use a handler thread so that our messages don't cause network events on the main thread
	private void sendBroadcastMessageToAllRemoteClients(final String message, @Nullable final String ignoreClient) {
		if (mWifiServer != null) {
			mMessageThreadHandler.post(new Runnable() {
				@Override
				public void run() {
					mWifiServer.sendMessageToAll(message, ignoreClient);
				}
			});
		}
		if (mWifiClient != null) {
			mMessageThreadHandler.post(new Runnable() {
				@Override
				public void run() {
					mWifiClient.sendMessage(message);
				}
			});
		}
		if (mBluetoothServer != null) {
			mMessageThreadHandler.post(new Runnable() {
				@Override
				public void run() {
					mBluetoothServer.sendMessageToAll(message, ignoreClient);
				}
			});
		}
		if (mBluetoothClient != null) {
			mMessageThreadHandler.post(new Runnable() {
				@Override
				public void run() {
					mBluetoothClient.sendMessage(message);
				}
			});
		}
	}

	// internal/system broadcast messages are sent to all clients, but not for plugin consumption
	private void handleSystemBroadcastMessage(BroadcastMessage message) {
		if (TextUtils.isEmpty(message.getMessage())) {
			return;
		}
		switch (message.getMessage()) {
			case SYSTEM_BROADCAST_EVENT_SHOW_QR_CODE:
				// if this is the server, must ensure that Bluetooth is still visible when we show the QR code (from any device)
				if (mHotspotMode) {
					BluetoothUtils.setDiscoverable(HotspotManagerService.this, mBluetoothAdapter);
				}
				break;

			default:
				break;
		}
	}

	// TODO: convert all this to handlers or locally broadcast intents rather than using EventBus?
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onServerError(ServerErrorEvent event) {
		Log.d(TAG, "Server error (event)");
		if (mConnectionOptions != null) {
			switch (event.mType) {
				case WIFI:
					if (mWifiConnectionErrorCount >= 10) { // TODO: improve this
						Log.d(TAG, "Wifi server failed - restarting");
						mWifiConnectionErrorCount += 1;
						startWifiServer(); // TODO: this assumes that Wifi is still turned on, etc
					} else {
						Log.d(TAG, "Wifi server failed repeatedly - aborting");
					}
					break;
				case BLUETOOTH:
					if (mBluetoothConnectionErrorCount >= 10) { // TODO: improve this
						Log.d(TAG, "Bluetooth server failed - restarting");
						mBluetoothConnectionErrorCount += 1;
						startBluetoothServer(); // TODO: this assumes that Bluetooth is still turned on, etc
					} else {
						Log.d(TAG, "Wifi server failed repeatedly - aborting");
					}
					break;
				case UNKNOWN:
				default:
					break; // TODO: is there anything we can do?
			}
		}
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onServerConnectionSuccess(ServerConnectionSuccessEvent event) {
		Log.d(TAG, "Server connection success (event): " + event.mType);
		// TODO: send to all remote clients?

		// new remote client successfully connected
		sendSystemMessageToAllLocalClients(EVENT_DEVICE_CONNECTED, event.mType.toString());
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onServerConnectionError(ServerMessageErrorEvent event) {
		Log.d(TAG, "Server connection error (event): " + event.mType);
		// TODO: send to all remote clients?

		// server connection error - a single remote client failed // TODO: if no more remote clients connected, re-show QR code?
		sendSystemMessageToAllLocalClients(EVENT_REMOTE_CLIENT_ERROR, event.mType.toString());
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onClientConnectionSuccess(ClientConnectionSuccessEvent event) {
		Log.d(TAG, "Client connection success (event): " + event.mType);
		// TODO: send to all remote clients?

		// successfully connected to remote server
		mIsConnected = true;
		mConnectionErrorHandler.removeMessages(MSG_WIFI_CONNECTION_ERROR);
		mConnectionErrorHandler.removeMessages(MSG_BLUETOOTH_CONNECTION_ERROR);
		sendSystemMessageToAllLocalClients(EVENT_DEVICE_CONNECTED, event.mType.toString());

		// restore the default properties of the other connection method
		switch (event.mType) {
			case WIFI:
				if (mBluetoothClient != null) {
					mBluetoothClient.closeConnection();
					mBluetoothClient = null;
				}
				restoreOriginalBluetoothState();
				break;
			case BLUETOOTH:
				if (mWifiClient != null) {
					mWifiClient.closeConnection();
					mWifiClient = null;
				}
				restoreOriginalWifiState();
				break;
			case UNKNOWN:
			default:
				break; // nothing to do (should not happen)
		}
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onClientConnectionError(ClientConnectionErrorEvent event) {
		Log.d(TAG, "Client connection error (event): " + event.mType);

		// client connection error - the connection to the remote server failed during setup
		switch (event.mType) {
			// only retry if it was our chosen connection type that failed (e.g., the one that is not null) - otherwise we get
			// stuck into a loop when trying to cancel unnecessary connections
			case WIFI:
				if (mWifiClient != null) {
					retryWifiConnection();
				}
				break;
			case BLUETOOTH:
				if (mBluetoothClient != null) {
					retryBluetoothConnection();
				}
				break;
			case UNKNOWN:
			default:
		}
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onClientMessageError(ClientMessageErrorEvent event) {
		Log.d(TAG, "Client message error (event) " + event.mType);

		// client message error - a client's connection to the remote server failed
		switch (event.mType) {
			case WIFI:
				Log.d(TAG, "Wifi client failed - restarting");
				retryWifiConnection();

				sendSystemMessageToAllLocalClients(EVENT_LOCAL_CLIENT_ERROR, event.mType.toString());

				// stop local unconnected activities (i.e., plugins)
				Log.d(TAG, "Sending stop command to all local plugins");
				Intent wifiStopIntent = new Intent(PluginIntent.ACTION_STOP_PLUGIN);
				wifiStopIntent.setClassName(mConnectionOptions.mPluginPackage, PluginIntent.MESSAGE_RECEIVER);
				// TODO: source is only necessary for internal plugins - remove later?
				wifiStopIntent.putExtra(PluginIntent.EXTRA_SOURCE, HotspotManagerService.this.getPackageName());
				sendBroadcast(wifiStopIntent);
				break;

			case BLUETOOTH:
				Log.d(TAG, "Bluetooth client failed - restarting");
				retryBluetoothConnection();

				sendSystemMessageToAllLocalClients(EVENT_LOCAL_CLIENT_ERROR, event.mType.toString());

				// stop local unconnected activities (i.e., plugins)
				Log.d(TAG, "Sending stop command to all local plugins");
				Intent bluetoothStopIntent = new Intent(PluginIntent.ACTION_STOP_PLUGIN);
				bluetoothStopIntent.setClassName(mConnectionOptions.mPluginPackage, PluginIntent.MESSAGE_RECEIVER);
				// TODO: source is only necessary for internal plugins - remove later?
				bluetoothStopIntent.putExtra(PluginIntent.EXTRA_SOURCE, HotspotManagerService.this.getPackageName());
				sendBroadcast(bluetoothStopIntent);
				break;

			case UNKNOWN:
			default:
				if (!mHotspotMode) {
					// TODO: this is caused by RemoteConnection - messages has failed, but the connection is still apparently ok
					// TODO: - should we kill the connection after a certain number of these?
					Log.d(TAG, "Client message error with unknown source - ignoring");
				} else {
					// on the server, we get error messages with source set to unknown - this is caused by the fact that all
					// connections extend RemoteConnection, and that class sends client errors rather than differentiating
					// between client and server (a limitation). When this happens, there is no need to do anything here,
					// because we get a ServerMessageErrorEvent from [Wifi|Bluetooth]ServerConnection classes when the failure
					// actually happens, and a further ServerMessageErrorEvent from [Wifi|Bluetooth]Server when the dead client
					// is removed from the list of connected sockets.
				}
				break;
		}
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onMessage(MessageReceivedEvent event) {
		Log.d(TAG, "Message received (event)");

		// internal system messages are dealt with by the service locally (e.g., not sent to plugins, but sent to remote clients)
		if (event.mMessage.isSystemMessage()) {
			handleSystemBroadcastMessage(event.mMessage);
		} else {
			sendBroadcastMessageToAllLocalClients(event.mMessage); // forward to local clients
		}

		// if we're the server (e.g., not delivered by the server) then forward to all remote clients, too
		if (!SERVER_MESSAGE_ID.equals(event.mDeliveredBy)) {
			try {
				// forward to all clients if not from server - these messages already have their from attribute set
				sendBroadcastMessageToAllRemoteClients(BroadcastMessage.toString(event.mMessage), event.mDeliveredBy);
			} catch (IOException e) {
				Log.d(TAG, "Broadcast message forwarding error: " + e.getLocalizedMessage());
			}
		}
	}
}
