package ac.robinson.bettertogether.hotspot;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.support.annotation.Nullable;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import ac.robinson.bettertogether.event.EventType;
import ac.robinson.bettertogether.event.ServerErrorEvent;

public class BluetoothServer implements Runnable {

	private static final String TAG = "BluetoothServer";

	private boolean mRunning = false;

	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothServerSocket mServerSocket;
	private HashMap<String, BluetoothServerConnection> mConnectedSockets = new HashMap<>();

	public BluetoothServer(BluetoothAdapter adapter) {
		mBluetoothAdapter = adapter;
	}

	@Override
	public void run() {
		mRunning = true;
		try {
			Log.d(TAG, "Starting Bluetooth server");
			mServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(mBluetoothAdapter.getName(),
					HotspotManagerService.BLUETOOTH_SERVER_UUID);

			try {
				while (mRunning) {
					BluetoothSocket acceptedSocket = mServerSocket.accept();

					String newConnectionId = HotspotManagerService.getRandomShortUUID();
					BluetoothServerConnection connectedServer = new BluetoothServerConnection(newConnectionId, acceptedSocket);
					new Thread(connectedServer).start();
					mConnectedSockets.put(newConnectionId, connectedServer);
				}
			} catch (IOException e) {
				Log.e(TAG, "Bluetooth server client error: " + e.getLocalizedMessage());
				EventBus.getDefault().post(new ServerErrorEvent(EventType.Type.BLUETOOTH));
			}

		} catch (IOException e) {
			Log.e(TAG, "Bluetooth server error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ServerErrorEvent(EventType.Type.BLUETOOTH));
		}
	}

	public void sendMessageToAll(String message, @Nullable String ignoreClient) {
		for (Map.Entry<String, BluetoothServerConnection> connection : mConnectedSockets.entrySet()) {
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
		for (Map.Entry<String, BluetoothServerConnection> connection : mConnectedSockets.entrySet()) {
			connection.getValue().closeConnection();
		}
	}
}
