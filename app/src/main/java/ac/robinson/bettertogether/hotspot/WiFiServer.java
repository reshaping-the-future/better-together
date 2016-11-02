package ac.robinson.bettertogether.hotspot;

import android.support.annotation.Nullable;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import ac.robinson.bettertogether.event.EventType;
import ac.robinson.bettertogether.event.ServerErrorEvent;

public class WiFiServer implements Runnable {

	private static final String TAG = "WifiServer";

	private boolean mRunning = false;

	private int mPort;
	private ServerSocket mServerSocket;
	private HashMap<String, WiFiServerConnection> mConnectedSockets = new HashMap<>();

	public WiFiServer(int port) {
		mPort = port;
	}

	@Override
	public void run() {
		mRunning = true;
		try {
			Log.d(TAG, "Starting WiFi server on port " + mPort);
			mServerSocket = new ServerSocket(mPort);

			try {
				while (mRunning) {
					Socket acceptedSocket = mServerSocket.accept();

					String newConnectionId = HotspotManagerService.getRandomShortUUID();
					WiFiServerConnection connectedServer = new WiFiServerConnection(newConnectionId, acceptedSocket);
					new Thread(connectedServer).start();
					mConnectedSockets.put(newConnectionId, connectedServer);
				}
			} catch (IOException e) {
				Log.e(TAG, "WiFi server client error: " + e.getLocalizedMessage());
				EventBus.getDefault().post(new ServerErrorEvent(EventType.Type.WIFI));
			}

		} catch (IOException e) {
			Log.e(TAG, "WiFi server error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ServerErrorEvent(EventType.Type.WIFI));
		}
	}

	public void sendMessageToAll(String message, @Nullable String ignoreClient) {
		for (Map.Entry<String, WiFiServerConnection> connection : mConnectedSockets.entrySet()) {
			if (!connection.getKey().equals(ignoreClient)) { // send to all except the single ignored client
				connection.getValue().sendMessage(message);
			}
		}
	}

	public void closeAllConnections() {
		mRunning = false;
		if (mServerSocket != null) {
			try {
				mServerSocket.close();
				mServerSocket = null;
			} catch (Exception ignored) {
			}
		}
		for (Map.Entry<String, WiFiServerConnection> connection : mConnectedSockets.entrySet()) {
			connection.getValue().closeConnection();
		}
	}
}
