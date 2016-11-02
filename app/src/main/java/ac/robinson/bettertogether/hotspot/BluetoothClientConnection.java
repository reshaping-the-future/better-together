package ac.robinson.bettertogether.hotspot;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.InputStream;
import java.io.OutputStreamWriter;

import ac.robinson.bettertogether.event.ClientConnectionErrorEvent;
import ac.robinson.bettertogether.event.ClientMessageErrorEvent;
import ac.robinson.bettertogether.event.ClientConnectionSuccessEvent;
import ac.robinson.bettertogether.event.EventType;

public class BluetoothClientConnection extends RemoteConnection {

	private static final String TAG = "BTClientConnection";

	private boolean mRunning = false;

	private BluetoothDevice mRemoteDevice;
	private BluetoothConnector mBluetoothConnector;

	private BluetoothSocket mSocket;
	private InputStream mInputStream;
	private OutputStreamWriter mOutputStreamWriter;

	public BluetoothClientConnection(BluetoothDevice remoteDevice) {
		setLogTag(TAG);
		mRemoteDevice = remoteDevice;
	}

	@Override
	public void run() {
		mRunning = true;

		// TODO: improve this initial connection in a similar way to the WiFi version (e.g., merge BluetoothConnector here)
		while (mInputStream == null) {
			mBluetoothConnector = new BluetoothConnector(mRemoteDevice, true, HotspotManagerService.BLUETOOTH_SERVER_UUID);
			try {
				mSocket = mBluetoothConnector.connect().getUnderlyingSocket();
				mInputStream = mSocket.getInputStream();
			} catch (Exception e1) {
				Log.d(TAG, "Bluetooth socket connection exception - aborting");
				EventBus.getDefault().post(new ClientConnectionErrorEvent(EventType.Type.BLUETOOTH));
				return;
			}
		}
		if (mSocket == null) {
			Log.d(TAG, "Bluetooth socket setup failure - aborting");
			EventBus.getDefault().post(new ClientConnectionErrorEvent(EventType.Type.BLUETOOTH));
			return;
		}

		Log.d(TAG, "Bluetooth client connected to " + mRemoteDevice.getName());
		EventBus.getDefault().post(new ClientConnectionSuccessEvent(EventType.Type.BLUETOOTH));

		try {
			mOutputStreamWriter = new OutputStreamWriter(mSocket.getOutputStream());

			byte[] buffer = new byte[HotspotManagerService.MESSAGE_BUFFER_SIZE];
			int bytesRead;
			StringBuilder stringBuilder = new StringBuilder();

			while (mRunning) {
				bytesRead = mInputStream.read(buffer);
				processBytes(HotspotManagerService.INTERNAL_SERVER_ID, buffer, bytesRead, stringBuilder);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Bluetooth client error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ClientMessageErrorEvent(EventType.Type.BLUETOOTH));
		}
	}

	public boolean sendMessage(String message) {
		return sendMessage(mOutputStreamWriter, message);
	}

	public void closeConnection() {
		mRunning = false;
		closeConnection(mInputStream);
		mInputStream = null;
		closeConnection(mOutputStreamWriter);
		mOutputStreamWriter = null;
		closeConnection(mSocket);
		mSocket = null;
		mBluetoothConnector.close();
		mBluetoothConnector = null;
	}
}
