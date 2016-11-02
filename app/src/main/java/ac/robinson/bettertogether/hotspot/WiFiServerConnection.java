package ac.robinson.bettertogether.hotspot;

import android.os.Build;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;

import ac.robinson.bettertogether.event.EventType;
import ac.robinson.bettertogether.event.ServerMessageErrorEvent;
import ac.robinson.bettertogether.event.ServerConnectionSuccessEvent;

public class WiFiServerConnection extends RemoteConnection {

	private static final String TAG = "WifiServerConnection";

	private boolean mRunning = false;

	private String mId;
	private Socket mSocket;
	private InputStream mInputStream;
	private OutputStreamWriter mOutputStreamWriter;

	public WiFiServerConnection(String id, Socket socket) {
		mId = id;
		mSocket = socket;
	}

	@Override
	public void run() {
		mRunning = true;
		try {
			Log.d(TAG, "WiFi server connected to client");
			EventBus.getDefault().post(new ServerConnectionSuccessEvent(EventType.Type.WIFI));

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
			Log.e(TAG, "WiFi server connection error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ServerMessageErrorEvent(EventType.Type.WIFI));
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
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			closeConnection(mSocket);
		} else {
			if (mSocket != null) {
				try {
					mSocket.close();
				} catch (Exception ignored) {
				}
			}
		}
		mSocket = null;
	}
}
