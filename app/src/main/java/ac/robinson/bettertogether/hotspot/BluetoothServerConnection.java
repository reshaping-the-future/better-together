package ac.robinson.bettertogether.hotspot;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.InputStream;
import java.io.OutputStreamWriter;

import ac.robinson.bettertogether.event.EventType;
import ac.robinson.bettertogether.event.ServerMessageErrorEvent;
import ac.robinson.bettertogether.event.ServerConnectionSuccessEvent;

public class BluetoothServerConnection extends RemoteConnection {

	private static final String TAG = "BTServerConnection";

	private boolean mRunning = false;

	private String mId;
	private BluetoothSocket mSocket;
	private InputStream mInputStream;
	private OutputStreamWriter mOutputStreamWriter;

	public BluetoothServerConnection(String id, BluetoothSocket socket) {
		mId = id;
		mSocket = socket;
	}

	@Override
	public void run() {
		mRunning = true;
		try {
			Log.d(TAG, "Bluetooth server connected to client");
			EventBus.getDefault().post(new ServerConnectionSuccessEvent(EventType.Type.BLUETOOTH));

			mInputStream = mSocket.getInputStream();
			mOutputStreamWriter = new OutputStreamWriter(mSocket.getOutputStream());

			int bufferSize = HotspotManagerService.MESSAGE_BUFFER_SIZE;
			byte[] buffer = new byte[bufferSize];
			int bytesRead;
			StringBuilder stringBuilder = new StringBuilder();

			while (mRunning) {
				bytesRead = mInputStream.read(buffer);
				processBytes(mId, buffer, bytesRead, stringBuilder);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Bluetooth server connection error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ServerMessageErrorEvent(EventType.Type.BLUETOOTH));
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
	}
}
