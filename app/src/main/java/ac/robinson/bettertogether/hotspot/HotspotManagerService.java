package ac.robinson.bettertogether.hotspot;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.NetworkInfo;
import android.net.UrlQuerySanitizer;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.event.ClientConnectionErrorEvent;
import ac.robinson.bettertogether.event.ClientConnectionSuccessEvent;
import ac.robinson.bettertogether.event.ClientMessageErrorEvent;
import ac.robinson.bettertogether.event.MessageReceivedEvent;
import ac.robinson.bettertogether.event.ServerConnectionSuccessEvent;
import ac.robinson.bettertogether.event.ServerErrorEvent;
import ac.robinson.bettertogether.event.ServerMessageErrorEvent;

public class HotspotManagerService extends Service {

	private static final String TAG = "HotspotManagerService";

	// note hotspot address is hardcoded in Android sources: http://stackoverflow.com/a/23183923
	private static final String HOTSPOT_STATE_FILTER = "android.net.wifi.WIFI_AP_STATE_CHANGED";
	private static final String DEFAULT_HOTSPOT_NAME_FORMAT = "%s-%s"; // [R.string.app_name]-[random string]
	private static final String DEFAULT_HOTSPOT_IP_ADDRESS = "192.168.43.1";
	private static final int DEFAULT_HOTSPOT_PORT = 33113; // up to 65535
	private static final String DEFAULT_HOTSPOT_URL = "http://reshapingthefuture.org/better-together?" +
			"connect={SSID}&k={password}";

	private WifiManager mWifiManager;
	private BluetoothAdapter mBluetoothAdapter;

	// for trying to restore the previous state on exit
	private boolean mOriginalBluetoothStatus = false;
	private String mOriginalBluetoothName = null;
	private boolean mOriginalWifiStatus = false;
	private String mOriginalHotspotName = null;
	private int mHotspotId = -1;
	private String mHotspotName = null;

	// for tracking server/client state
	public static final String INTERNAL_SERVER_ID = "qe9ch3t9yq7l";
	public static final UUID BLUETOOTH_SERVER_UUID = UUID.fromString("07eb2627-3de9-4ae4-b6a9-cbb282a6363f");
	public static final int MESSAGE_BUFFER_SIZE = 2048;
	public static final int MESSAGE_ID_SIZE = 12; // [12 chars of UUID]
	public static final int MESSAGE_PART_COUNT_SIZE = 2; // up to 10^n parts of MESSAGE_BUFFER_SIZE (when Base64-encoded)
	public static final int MESSAGE_HEADER_SIZE = MESSAGE_ID_SIZE + (2 * MESSAGE_PART_COUNT_SIZE); // id; part count; part num
	public static final int MESSAGE_PAYLOAD_SIZE = MESSAGE_BUFFER_SIZE - MESSAGE_HEADER_SIZE - 1; // 1 char for \n
	public static final byte MESSAGE_DELIMITER_BYTE = (byte) '\f'; // have to use \f as \r and \n are part of base64
	public static final String MESSAGE_DELIMITER_STRING = Character.toString((char) MESSAGE_DELIMITER_BYTE);
	private ConnectionOptions mConnectionOptions = null;
	private WiFiServer mWiFiServer;
	private WiFiClientConnection mWiFiClient = null;
	private BluetoothServer mBluetoothServer;
	private BluetoothClientConnection mBluetoothClient;

	private class ConnectionOptions {
		public String mName;
		public String mPassword;
		public String mIPAddress;
		public int mPort;
		public boolean mNeedsInternetAccess;
	}

	private ErrorHandler mConnectionErrorHandler = new ErrorHandler();
	private int mWifiConnectionErrorCount = 0;
	private int mBluetoothConnectionErrorCount = 0;
	private static final int WIFI_CONNECTION_TIMEOUT = 15000; // milliseconds
	private static final int BLUETOOTH_CONNECTION_TIMEOUT = 18000; // milliseconds
	public static final float CONNECTION_DELAY_INCREMENT_MULTIPLIER = 1.3f; // multiply delay by this on every failure
	private static final int MSG_WIFI_CONNECTION_ERROR = 1;
	private static final int MSG_BLUETOOTH_CONNECTION_ERROR = 2;
	private int mWifiConnectionTimeout = WIFI_CONNECTION_TIMEOUT;
	private int mBluetoothConnectionTimeout = BLUETOOTH_CONNECTION_TIMEOUT;

	// service messages and communication
	private boolean mIsBound = false;
	private final Messenger mMessenger;
	private ArrayList<Messenger> mClients = new ArrayList<>();

	public static final String KEY_BROADCAST_MESSAGE = "broadcast";
	public static final String KEY_SERVICE_MESSAGE = "service";

	public static final int MSG_REGISTER_CLIENT = 1; // service management
	public static final int MSG_UNREGISTER_CLIENT = 2; // service management

	public static final int MSG_ENABLE_HOTSPOT = 3; // hotspot management
	public static final int MSG_DISABLE_HOTSPOT = 4; // hotspot management
	public static final int MSG_JOIN_HOTSPOT = 5; // hotspot management

	public static final int MSG_BROADCAST = 6; // messages to remote clients

	public static final int EVENT_NEW_DEVICE_CONNECTED = 7; // events sent as messages to local clients
	public static final int EVENT_CONNECTION_STATUS_UPDATE = 8; // events sent as messages to local clients
	public static final int EVENT_LOCAL_CLIENT_ERROR = 9;
	public static final int EVENT_REMOTE_CLIENT_ERROR = 10;
	public static final int EVENT_SETTINGS_PERMISSION_ERROR = 11;

	public static final String INTERNAL_BROADCAST_EVENT_SHOW_QR_CODE = "show_qr";

	public HotspotManagerService() {
		// initialise the handler - actual service is initialised in onBind as we can't get a context here
		mMessenger = new Messenger(new IncomingHandler(HotspotManagerService.this));
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		if (!mIsBound) {
			initialise();
			mIsBound = true;
		}

		return mMessenger.getBinder();
	}

	@Override
	public void onDestroy() {
		cleanup();
	}

	private void initialise() {
		mBluetoothAdapter = getBluetoothAdapter(HotspotManagerService.this);

		//TODO: check mBluetoothAdapter is not null (or earlier, via, e.g., checking presence of bluetooth)
		//TODO: context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
		//TODO: do the same for WiFi
		mOriginalBluetoothStatus = mBluetoothAdapter.isEnabled();
		mOriginalBluetoothName = mBluetoothAdapter.getName();

		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

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
		}
		try {
			WifiConfiguration wifiConfiguration = getWifiHotspotConfiguration();
			mOriginalHotspotName = wifiConfiguration.SSID;
		} catch (Exception ignored) {
		}

		// listen for network/bluetooth state changes
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(HOTSPOT_STATE_FILTER); // WiFi hotspot states
		intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION); // WiFi on/off
		intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION); // network connection/disconnection
		intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED); // Bluetooth on/off
		intentFilter.addAction(BluetoothDevice.ACTION_FOUND); // Bluetooth device found
		registerReceiver(mBroadcastReceiver, intentFilter);

		if (!EventBus.getDefault().isRegistered(this)) {
			EventBus.getDefault().register(this); // register for EventBus events
		}
	}

	private void cleanup() {
		EventBus.getDefault().unregister(this);
		unregisterReceiver(mBroadcastReceiver);

		// disconnect all servers/clients
		if (mWiFiServer != null) {
			mWiFiServer.closeAllConnections();
		}
		if (mWiFiClient != null) {
			mWiFiClient.closeConnection();
		}

		// reset to original state
		if (getHotspotEnabled()) {
			try {
				// TODO: is it possible to save the original password?
				WifiConfiguration wifiConfiguration = getWifiHotspotConfiguration();
				setConfigurationAttributes(wifiConfiguration, mOriginalHotspotName, "");
				setWifiHotspotConfiguration(wifiConfiguration);
				setHotspotEnabled(wifiConfiguration, false);
			} catch (Exception ignored) {
			}
		}

		// used to remove the network here - not done any more so that repeat connections are faster
		mWifiManager.disableNetwork(mHotspotId);
		mWifiManager.setWifiEnabled(mOriginalWifiStatus);
		if (mOriginalWifiStatus) {
			mWifiManager.reconnect();
		}

		// disable bluetooth if necessary
		mBluetoothAdapter.setName(mOriginalBluetoothName);
		if (!mOriginalBluetoothStatus) {
			mBluetoothAdapter.disable(); // note that on later Android versions, WiFi connections with no access do this anyway
		}
	}

	public static String getHotspotName(String name, String uniqueId) {
		String hotspotName = String.format(DEFAULT_HOTSPOT_NAME_FORMAT, name, uniqueId);
		return hotspotName.substring(0, Math.min(hotspotName.length(), 20)); // need to trim to 20 chars max for some devices
	}

	public static String getHotspotUrl(String name, String password) {
		String hotspotUrl = DEFAULT_HOTSPOT_URL;
		hotspotUrl = hotspotUrl.replace("{SSID}", name);
		hotspotUrl = hotspotUrl.replace("{password}", password);
		Log.d(TAG, "Hotspot URL: " + hotspotUrl);
		// ip and port not needed for now - hardcoded below
		// HotspotManagerService.DEFAULT_HOTSPOT_IP_ADDRESS
		// HotspotManagerService.DEFAULT_HOTSPOT_PORT
		return hotspotUrl;
	}

	private ConnectionOptions parseHotspotUrl(String url) {
		UrlQuerySanitizer sanitizer = new UrlQuerySanitizer(url);
		ConnectionOptions connectionOptions = new ConnectionOptions();
		connectionOptions.mName = sanitizer.getValue("connect");
		connectionOptions.mPassword = sanitizer.getValue("k");
		connectionOptions.mIPAddress = DEFAULT_HOTSPOT_IP_ADDRESS; // ip not needed for now - hardcoded
		connectionOptions.mPort = DEFAULT_HOTSPOT_PORT; // port not needed for now - hardcoded
		String internetAccess = sanitizer.getValue("ia");
		connectionOptions.mNeedsInternetAccess = "1".equals(internetAccess);
		return connectionOptions;
	}

	public static String getRandomShortUUID() {
		// from: https://gist.github.com/LeeSanghoon/5811136
		long uuid = ByteBuffer.wrap(UUID.randomUUID().toString().getBytes()).getLong();
		if (uuid > Long.MAX_VALUE / 2) {
			uuid -= (Long.MAX_VALUE / 2); // ensure we get only 12 characters
		}
		return Long.toString(uuid, Character.MAX_RADIX);
	}

	public static Bitmap generateQrCode(String text) {
		Hashtable<EncodeHintType, ErrorCorrectionLevel> hintMap = new Hashtable<>();
		hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M); // medium error correction

		int size = 256;
		QRCodeWriter qrCodeWriter = new QRCodeWriter();
		BitMatrix bitMatrix;
		try {
			bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size, hintMap);
		} catch (WriterException e) {
			// TODO: handle this better
			return null;
		}

		int matrixSize = bitMatrix.getWidth();
		Bitmap bitmap = Bitmap.createBitmap(matrixSize, matrixSize, Bitmap.Config.RGB_565);
		for (int x = 0; x < matrixSize; x++) {
			for (int y = 0; y < matrixSize; y++) {
				//noinspection SuspiciousNameCombination - reverse to orient QR code correctly
				bitmap.setPixel(y, x, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
			}
		}
		return bitmap;
	}

	private static BluetoothAdapter getBluetoothAdapter(Context context) {
		BluetoothAdapter bluetoothAdapter;
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		} else {
			bluetoothAdapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
		}
		return bluetoothAdapter;
	}

	private void configureAndStartBluetoothHotspot() {
		mBluetoothAdapter.setName(mHotspotName);

		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
			discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // need to do this to start from service
			startActivity(discoverableIntent);
		}

		startBluetoothServer();
	}

	private void startBluetoothServer() {
		BluetoothServer bluetoothServer = new BluetoothServer(mBluetoothAdapter);
		new Thread(bluetoothServer).start();
		mBluetoothServer = bluetoothServer;
	}

	// TODO: is this necessary? (see: http://stackoverflow.com/a/21968663)
	private String getWifiIp() throws SocketException {
		for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
			NetworkInterface intf = en.nextElement();
			if (intf.isLoopback()) {
				continue;
			}
			if (intf.isVirtual()) {
				continue;
			}
			if (!intf.isUp()) {
				continue;
			}
			if (intf.isPointToPoint()) {
				continue;
			}
			if (intf.getHardwareAddress() == null) {
				continue;
			}
			for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
				InetAddress inetAddress = enumIpAddr.nextElement();
				if (inetAddress.getAddress().length == 4) {
					return inetAddress.getHostAddress();
				}
			}
		}
		return null;
	}

	// whether the hotspot is on or off
	private boolean getHotspotEnabled() {
		try {
			Method isWifiApEnabled = mWifiManager.getClass().getMethod("isWifiApEnabled");
			return (Boolean) isWifiApEnabled.invoke(mWifiManager);
		} catch (Throwable ignored) {
		}
		return false;
	}

	// toggle wifi hotspot on or off - TODO: configuration seems to be ignored when turning off - does this matter?
	private void setHotspotEnabled(@Nullable WifiConfiguration configuration, boolean enabled) throws NoSuchMethodException,
			InvocationTargetException, IllegalAccessException {
		// TODO: wait for the hotspot to be enabled, then send clients a confirmation
		// TODO: do we need to wait for WiFi to finish being disabled before enabling the hotspot?
		mWifiManager.setWifiEnabled(false); // some devices require this to be called before enabling the hotspot
		Method setWifiApEnabled = mWifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
		setWifiApEnabled.invoke(mWifiManager, configuration, enabled);

		if (enabled) {
			//noinspection ConstantConditions - configuration is only @Nullable when enabled is false
			mHotspotName = configuration.SSID;
			startWifiServer();

			switch (mBluetoothAdapter.getState()) {
				case BluetoothAdapter.STATE_ON:
					configureAndStartBluetoothHotspot();
					break;

				case BluetoothAdapter.STATE_TURNING_ON:
					break; // finish configuration in receiver

				default:
					mBluetoothAdapter.enable(); // finish configuration in receiver
			}
		}
	}

	private void startWifiServer() {
		WiFiServer wiFiServer = new WiFiServer(DEFAULT_HOTSPOT_PORT);
		new Thread(wiFiServer).start();
		mWiFiServer = wiFiServer;
	}

	// returns one of WifiManager.WIFI_AP_STATE_*
	private int getWifiHotspotState() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		Method getWifiApConfiguration = mWifiManager.getClass().getMethod("getWifiApState");
		return (Integer) getWifiApConfiguration.invoke(mWifiManager);
	}

	// get the current hotspot configuration
	private WifiConfiguration getWifiHotspotConfiguration() throws NoSuchMethodException, InvocationTargetException,
			IllegalAccessException {
		Method getWifiApConfiguration = mWifiManager.getClass().getMethod("getWifiApConfiguration");
		return (WifiConfiguration) getWifiApConfiguration.invoke(mWifiManager);
	}

	// update a WifiConfiguration with the given name (SSID) and password - see: https://stackoverflow.com/questions/2140133/
	private WifiConfiguration setConfigurationAttributes(@NonNull WifiConfiguration wifiConfiguration, @NonNull String name,
	                                                     @NonNull String password) {
		wifiConfiguration.SSID = name;
		wifiConfiguration.preSharedKey = password;
		return wifiConfiguration; // TODO: add other attributes? (see: http://stackoverflow.com/a/13875379)
	}

	// set the hotspot configuration without changing its on/off status
	private void setWifiHotspotConfiguration(@NonNull WifiConfiguration wifiConfiguration) throws NoSuchMethodException,
			InvocationTargetException, IllegalAccessException {
		Method setWifiApConfiguration = mWifiManager.getClass().getMethod("setWifiApConfiguration", WifiConfiguration.class);
		setWifiApConfiguration.invoke(mWifiManager, wifiConfiguration);
	}

	// connect to a hotspot with the given name (SSID) and password
	private void connectHotspot(@NonNull ConnectionOptions options) {
		mConnectionOptions = options;
		mHotspotName = options.mName;

		// connect to either the WiFi or Bluetooth hotspot depending on whether we need internet access
		// TODO: saved connectivity is not currently filtered (e.g., if the receiver finds the network, we'll connect anyway)
		if (options.mNeedsInternetAccess) {
			sendServiceMessageToAllLocalClients(EVENT_CONNECTION_STATUS_UPDATE, "Searching Bluetooth...");
			Log.d(TAG, "Attempting connection via Bluetooth");
			switch (mBluetoothAdapter.getState()) {
				case BluetoothAdapter.STATE_ON:
					Log.d(TAG, "Starting Bluetooth discovery");
					mBluetoothAdapter.startDiscovery();
					setBluetoothErrorTimeout();
					break;

				case BluetoothAdapter.STATE_TURNING_ON:
					Log.d(TAG, "Waiting for Bluetooth to be enabled");
					break; // start discovery in receiver

				default:
					Log.d(TAG, "Enabling Bluetooth / waiting for receiver to enable");
					mBluetoothAdapter.enable(); // start discovery in receiver
			}
		} else {
			sendServiceMessageToAllLocalClients(EVENT_CONNECTION_STATUS_UPDATE, "Searching WiFi...");
			Log.d(TAG, "Attempting connection via WiFi");
			switch (mWifiManager.getWifiState()) {
				case WifiManager.WIFI_STATE_ENABLED:
					Log.d(TAG, "Completing hotspot connection");
					finishConnectingHotspotWithSavedOptions();
					break;

				case WifiManager.WIFI_STATE_ENABLING:
					Log.d(TAG, "Waiting for WiFi to be enabled");
					break; // will connect in receiver

				default:
					Log.d(TAG, "Enabling WiFi");
					mWifiManager.setWifiEnabled(true); // will connect in receiver
					break;
			}
		}
	}

	private void finishConnectingHotspotWithSavedOptions() {
		// set up new network - *must* be surrounded by " (see: https://stackoverflow.com/questions/2140133/)
		Log.d(TAG, "Connecting to WiFi network " + mConnectionOptions.mName);
		WifiConfiguration wifiConfiguration = new WifiConfiguration();
		setConfigurationAttributes(wifiConfiguration, "\"" + mConnectionOptions.mName + "\"", "\"" + mConnectionOptions
				.mPassword + "\"");

		int savedNetworkId = getWifiNetworkId(wifiConfiguration.SSID);
		if (savedNetworkId >= 0) {
			Log.d(TAG, "Found saved WiFi network id");
			mHotspotId = savedNetworkId;
		} else {
			Log.d(TAG, "Adding WiFi network");
			mHotspotId = mWifiManager.addNetwork(wifiConfiguration);
			// mWifiManager.saveConfiguration(); // can change network IDs(!) - not really needed, so disabled
			if (mHotspotId < 0) {
				Log.d(TAG, "Couldn't add WiFi network");
				mWifiConnectionErrorCount += 1;
				retryRemoteConnection();
				return;
			}
		}

		// if we're auto-connected to a previous network (unlikely!), continue straight away; if not, reconnect
		WifiInfo currentConnection = mWifiManager.getConnectionInfo();
		if (currentConnection != null && wifiConfiguration.SSID.equals(currentConnection.getSSID())) {
			Log.d(TAG, "Continuing with current WiFi connection");
			connectWiFiClient(mConnectionOptions.mIPAddress, mConnectionOptions.mPort);
		} else {
			Log.d(TAG, "Enabling WiFi network");
			mWifiManager.disconnect();
			if (!mWifiManager.enableNetwork(mHotspotId, true)) { // connect to our network - handle connection in receiver
				Log.d(TAG, "Couldn't enable WiFi network");
				mWifiConnectionErrorCount += 1;
				retryRemoteConnection();
			} else {
				Log.d(TAG, "WiFi network enabled");
				mWifiManager.reconnect();
				setWifiErrorTimeout();
			}
		}
	}

	private int getWifiNetworkId(@NonNull String name) {
		// remove previous networks with the same SSID so we can update the password
		List<WifiConfiguration> configuredNetworks = mWifiManager.getConfiguredNetworks();
		if (configuredNetworks != null) { // can be null when WiFi is turned off
			for (WifiConfiguration network : configuredNetworks) {
				if (!TextUtils.isEmpty(network.SSID) && (network.SSID.equals(name))) {
					return network.networkId;
				}
			}
		}
		return -1;
	}

	private void connectWiFiClient(@NonNull String ip, int port) {
		sendServiceMessageToAllLocalClients(EVENT_CONNECTION_STATUS_UPDATE, "Found a phone – connecting over WiFi...");
		Log.d(TAG, "Starting WiFi client connection");
		WiFiClientConnection client = new WiFiClientConnection(ip, port);
		new Thread(client).start();
		mWiFiClient = client;
		setWifiErrorTimeout(); // reset/increase the timeout to give the connection time to succeed
	}

	private void connectBluetoothClient(@NonNull BluetoothDevice device) {
		sendServiceMessageToAllLocalClients(EVENT_CONNECTION_STATUS_UPDATE, "Found a phone – connecting over Bluetooth...");
		Log.d(TAG, "Starting Bluetooth client connection");
		BluetoothClientConnection client = new BluetoothClientConnection(device);
		new Thread(client).start();
		mBluetoothClient = client;
		setBluetoothErrorTimeout(); // reset/increase the timeout to give the connection time to succeed
	}

	private String trimQuotes(String name) {
		if (TextUtils.isEmpty(name)) {
			return null;
		}
		Matcher m = Pattern.compile("^\"(.*)\"$").matcher(name);
		if (m.find()) {
			return m.group(1);
		}
		return name;
	}

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			switch (intent.getAction()) {
				case HOTSPOT_STATE_FILTER: // see: http://stackoverflow.com/a/14681207
					int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
					if (state % 10 == WifiManager.WIFI_STATE_ENABLED) {
						// TODO: should we wait until here to setup server etc? (and detect manual disable events?)
						Log.d(TAG, "Hotspot enabled");
					}
					break;

				case WifiManager.WIFI_STATE_CHANGED_ACTION:
					Log.d(TAG, "Wifi state changed to: " + intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager
							.WIFI_STATE_UNKNOWN));
					switch (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)) {
						case WifiManager.WIFI_STATE_DISABLED:
							// TODO: do this here in case we see on state when turning off
							// TODO: can we do this only when needed? (e.g., don't fight the user)
							mWifiManager.setWifiEnabled(true);
							break;

						case WifiManager.WIFI_STATE_ENABLED:
							Log.d(TAG, "Wifi enabled");
							if (mWiFiServer != null) {
								// nothing do do in server mode - enabling the hotspot can be done after starting the server
							} else {
								if (!TextUtils.isEmpty(mHotspotName)) {
									finishConnectingHotspotWithSavedOptions();
								}
							}
							break;
					}
					break;

				case BluetoothAdapter.ACTION_STATE_CHANGED:
					Log.d(TAG, "Bluetooth state changed to: " + intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
					switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
						case BluetoothAdapter.STATE_OFF:
							// TODO: do this here in case we see on state when turning off
							// TODO: can we do this only when needed? (e.g., don't fight the user)
							mBluetoothAdapter.enable(); // start discovery in receiver
							break;

						case BluetoothAdapter.STATE_ON:
							Log.d(TAG, "Bluetooth enabled");
							if (mWiFiServer != null) {
								configureAndStartBluetoothHotspot();
							} else {
								if (!TextUtils.isEmpty(mHotspotName)) {
									Log.d(TAG, "Starting Bluetooth discovery (2)");
									mBluetoothAdapter.startDiscovery();
									setBluetoothErrorTimeout();
								}
							}
							break;
					}
					break;

				case WifiManager.NETWORK_STATE_CHANGED_ACTION:
					Log.d(TAG, "WiFi network state changed");
					if (mWiFiServer == null && !TextUtils.isEmpty(mHotspotName)) {
						boolean isConnected = false;
						String networkName1 = null;
						NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
						if (networkInfo != null) {
							networkName1 = trimQuotes(networkInfo.getExtraInfo());
							isConnected = networkInfo.isConnected();
						}
						String networkName2 = null;
						WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
						if (wifiInfo != null) {
							networkName2 = trimQuotes(wifiInfo.getSSID());
							isConnected = true;
						}
						Log.d(TAG, "(State change for network: " + networkName1 + " / " + networkName2 + "); connected: " +
								isConnected);
						if (isConnected && (mHotspotName.equals(networkName1) || mHotspotName.equals(networkName2)) &&
								mWiFiClient == null) {
							Log.d(TAG, "Continuing with current WiFi connection (2)");
							connectWiFiClient(mConnectionOptions.mIPAddress, mConnectionOptions.mPort);
						} else {
							Log.d(TAG, "Ignoring - connection already in progress");
						}
					}
					break;

				case BluetoothDevice.ACTION_FOUND: // TODO: handle ACTION_BOND_STATE_CHANGED too?
					Log.d(TAG, "Bluetooth device found");
					if (mWiFiServer == null && !TextUtils.isEmpty(mHotspotName)) {
						BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
						Log.d(TAG, "(Device name: " + device.getName() + " - searching for " + mHotspotName + ")");
						if (mHotspotName.equals(device.getName()) && mBluetoothClient == null) {
							Log.d(TAG, "Connecting to Bluetooth device");
							connectBluetoothClient(device);
							// mBluetoothAdapter.cancelDiscovery(); // no need to keep searching TODO: buggy - not essential
						}
					}
					break;
			}
		}
	};

	private class ErrorHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MSG_WIFI_CONNECTION_ERROR:
					Log.d(TAG, "WiFi connection error");
					mWifiConnectionErrorCount += 1;
					retryRemoteConnection();
					break;

				case MSG_BLUETOOTH_CONNECTION_ERROR:
					Log.d(TAG, "Bluetooth connection error");
					mBluetoothConnectionErrorCount += 1;
					retryRemoteConnection();
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

	private void retryRemoteConnection() {
		removeConnectionErrorHandlers();

		sendServiceMessageToAllLocalClients(EVENT_CONNECTION_STATUS_UPDATE, "Couldn't connect – retrying connection...");
		Log.d(TAG, "Retrying remote connection");

		mWiFiClient = null;
		mBluetoothClient = null;

		// after one failure, try restarting the adapters
		if (mWifiConnectionErrorCount > 0) {
			if (mHotspotId >= 0) {
				mWifiManager.removeNetwork(mHotspotId); // sometimes saved networks won't connect - remove and retry
				mWifiManager.saveConfiguration();
			}
			mWifiManager.setWifiEnabled(false);
		}
		if (mBluetoothConnectionErrorCount > 0) {
			mBluetoothAdapter.disable();
		}

		// after two failures, try the other method
		if (mBluetoothConnectionErrorCount > 1 || mWifiConnectionErrorCount > 1) {
			Log.d(TAG, "Switching connection method (WiFi/Bluetooth)");
			mConnectionOptions.mNeedsInternetAccess = !mConnectionOptions.mNeedsInternetAccess;
		}

		connectHotspot(mConnectionOptions); // try to reconnect
	}

	private void removeConnectionErrorHandlers() {
		mConnectionErrorHandler.removeMessages(MSG_WIFI_CONNECTION_ERROR);
		mConnectionErrorHandler.removeMessages(MSG_BLUETOOTH_CONNECTION_ERROR);
	}

	// handler for messages from local clients (e.g., activities that have connected to this service)
	private static class IncomingHandler extends Handler {
		private final WeakReference<HotspotManagerService> mServiceReference; // so we don't prevent garbage collection

		public IncomingHandler(HotspotManagerService instance) {
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
						mService.stopSelf(); // so we stop the hotspot etc when our final activity is killed
					}
					break;

				case MSG_ENABLE_HOTSPOT:
					try {
						mService.setHotspotEnabled(null, false);
					} catch (Exception e) {
						e.printStackTrace(); // unable to stop access point
					}
					ConnectionOptions options = mService.parseHotspotUrl(msg.getData().getString(KEY_SERVICE_MESSAGE));
					WifiConfiguration wifiConfiguration = null;
					try {
						// see: https://stackoverflow.com/questions/2140133/
						wifiConfiguration = mService.getWifiHotspotConfiguration();
						mService.setConfigurationAttributes(wifiConfiguration, options.mName, options.mPassword);
					} catch (Exception e) {
						e.printStackTrace(); // unable to get configuration
					}
					try {
						mService.setHotspotEnabled(wifiConfiguration, true);
					} catch (Exception e) {
						e.printStackTrace(); // unable to start access point
						mService.sendServiceMessageToAllLocalClients(EVENT_SETTINGS_PERMISSION_ERROR, null);
					}
					break;

				case MSG_DISABLE_HOTSPOT:
					try {
						mService.setHotspotEnabled(null, false);
					} catch (Exception e) {
						e.printStackTrace(); // unable to stop access point
					}
					break;

				case MSG_JOIN_HOTSPOT:
					mService.connectHotspot(mService.parseHotspotUrl(msg.getData().getString(KEY_SERVICE_MESSAGE)));
					break;

				case MSG_BROADCAST:
					Bundle data = msg.getData();
					BroadcastMessage message = (BroadcastMessage) data.getSerializable(KEY_BROADCAST_MESSAGE);

					// note: internal messages are dealt with by the service locally (but still forwarded to remote clients)
					if (message != null) {
						switch (message.mType) {
							case INTERNAL:
								mService.handleInternalBroadcastMessage(message);
								// note: intentionally following through to send message to all
							default:
								mService.sendBroadcastMessageToAllRemoteClients(message);
								break;
						}
					}
					break;

				default:
					super.handleMessage(msg);
			}
		}
	}

	// sends a broadcast message (e.g., something from remote clients) to all local clients
	private void sendBroadcastMessageToAllLocalClients(BroadcastMessage msg) {
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				Messenger client = mClients.get(i);
				Message message = Message.obtain(null, MSG_BROADCAST);
				message.replyTo = mMessenger;
				Bundle bundle = new Bundle(1);
				bundle.putSerializable(KEY_BROADCAST_MESSAGE, msg);
				message.setData(bundle);
				client.send(message);
			} catch (RemoteException e) {
				e.printStackTrace();
				mClients.remove(i); // client is dead - ok to remove here as we're reversing through the list
			}
		}
	}

	// sends a service message (e.g., HotspotManagerService events) to all local clients
	private void sendServiceMessageToAllLocalClients(int type, String data) {
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				Messenger client = mClients.get(i);
				Message message = Message.obtain(null, type);
				message.replyTo = mMessenger;
				if (data != null) {
					Bundle bundle = new Bundle(1);
					bundle.putString(KEY_SERVICE_MESSAGE, data);
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
			if (mWiFiServer != null) {
				message.setFrom(INTERNAL_SERVER_ID);
			}
			sendBroadcastMessageToAllRemoteClients(BroadcastMessage.toString(message), null);
		} catch (IOException e) {
			Log.d(TAG, "Broadcast message sending error: " + e.getLocalizedMessage());
		}
	}

	// send a message to all connected remote devices, optionally ignoring the client that sent the message
	private void sendBroadcastMessageToAllRemoteClients(String message, @Nullable String ignoreClient) {
		if (mWiFiServer != null) {
			mWiFiServer.sendMessageToAll(message, ignoreClient);
		}
		if (mWiFiClient != null) {
			mWiFiClient.sendMessage(message);
		}
		if (mBluetoothServer != null) {
			mBluetoothServer.sendMessageToAll(message, ignoreClient);
		}
		if (mBluetoothClient != null) {
			mBluetoothClient.sendMessage(message);
		}
	}

	private void handleInternalBroadcastMessage(BroadcastMessage message) {
		switch (message.mMessage) {
			case INTERNAL_BROADCAST_EVENT_SHOW_QR_CODE:
				// if this is the server, must ensure that Bluetooth is still visible when we show the QR code (from any device)
				if (mWiFiServer != null && mBluetoothAdapter.getScanMode() != BluetoothAdapter
						.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
					Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
					discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
					discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // need to do this to start from service
					startActivity(discoverableIntent);
				}
				break;
		}
	}

	// TODO: convert all this to handlers rather than using EventBus?
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onServerError(ServerErrorEvent event) {
		Log.d(TAG, "Server error (event)");
		switch (event.mType) {
			case WIFI:
				Log.d(TAG, "WiFi server failed - restarting");
				startWifiServer(); // TODO: this assumes that Wifi is still turned on, etc
				break;
			case BLUETOOTH:
				Log.d(TAG, "Bluetooth server failed - restarting");
				startBluetoothServer(); // TODO: this assumes that Bluetooth is still turned on, etc
				break;
		}
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onServerConnectionSuccess(ServerConnectionSuccessEvent event) {
		Log.d(TAG, "Server connection success (event)");
		// TODO: SEND THIS EVENT TO ALL REMOTE CLIENTS?

		// new remote client successfully connected
		sendServiceMessageToAllLocalClients(EVENT_NEW_DEVICE_CONNECTED, event.mType.toString());
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onServerConnectionError(ServerMessageErrorEvent event) {
		Log.d(TAG, "Server connection error (event)");
		// TODO: SEND THIS EVENT TO ALL REMOTE CLIENTS?

		// server connection error - a single remote client failed // TODO: if no more remote clients connected, re-show QR code
		sendServiceMessageToAllLocalClients(EVENT_REMOTE_CLIENT_ERROR, event.mType.toString());
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onClientConnectionSuccess(ClientConnectionSuccessEvent event) {
		Log.d(TAG, "Client connection success (event)");
		// TODO: SEND THIS EVENT TO ALL REMOTE CLIENTS?

		// successfully connected to remote server
		removeConnectionErrorHandlers();
		sendServiceMessageToAllLocalClients(EVENT_NEW_DEVICE_CONNECTED, event.mType.toString());
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onClientConnectionError(ClientConnectionErrorEvent event) {
		Log.d(TAG, "Client connection error (event)");
		retryRemoteConnection();

		// client connection error - the connection to the remote server failed during setup
		// TODO: do we need to do anything here? (should be handled by timeouts etc)
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onClientMessageError(ClientMessageErrorEvent event) {
		Log.d(TAG, "Client message error (event)");

		// client message error - the connection to the remote server failed
		retryRemoteConnection();
		sendServiceMessageToAllLocalClients(EVENT_LOCAL_CLIENT_ERROR, event.mType.toString());
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onMessage(MessageReceivedEvent event) {
		Log.d(TAG, "Message received (event)");

		// internal messages are dealt with by the service locally (but still forwarded to remote clients)
		switch (event.mMessage.mType) {
			case INTERNAL:
				handleInternalBroadcastMessage(event.mMessage);
				break;

			default:
				sendBroadcastMessageToAllLocalClients(event.mMessage); // forward to local clients
				break;
		}

		// if we're the server (e.g., not delivered by the server) then forward to all remote clients, too
		if (!INTERNAL_SERVER_ID.equals(event.mDeliveredBy)) {
			try {
				// forward to all clients if not from server - these messages already have their from attribute set
				sendBroadcastMessageToAllRemoteClients(BroadcastMessage.toString(event.mMessage), event.mDeliveredBy);
			} catch (IOException e) {
				Log.d(TAG, "Broadcast message forwarding error: " + e.getLocalizedMessage());
			}
		}
	}
}
