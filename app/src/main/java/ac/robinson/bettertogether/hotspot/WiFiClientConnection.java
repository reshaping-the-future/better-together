package ac.robinson.bettertogether.hotspot;

import android.os.Build;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

import ac.robinson.bettertogether.event.ClientConnectionErrorEvent;
import ac.robinson.bettertogether.event.ClientConnectionSuccessEvent;
import ac.robinson.bettertogether.event.ClientMessageErrorEvent;
import ac.robinson.bettertogether.event.EventType;

public class WiFiClientConnection extends RemoteConnection {

	private static final String TAG = "WifiClientConnection";

	private boolean mRunning = false;

	private String mHost;
	private int mPort;

	private Socket mSocket;
	private InputStream mInputStream;
	private OutputStreamWriter mOutputStreamWriter;

	public WiFiClientConnection(String host, int port) {
		setLogTag(TAG);
		mHost = host;
		mPort = port;
	}

	@Override
	public void run() {
		mRunning = true;

		Log.d(TAG, "Starting WiFi client - connecting to " + mHost + " on port " + mPort);
		int errorCount = 0;
		while (mInputStream == null) {
			try {
				if (mSocket != null) {
					mSocket.close();
				}
				mSocket = new Socket();
				mSocket.bind(null);
				mSocket.connect((new InetSocketAddress(mHost, mPort)), 500);
				mInputStream = mSocket.getInputStream();
			} catch (Exception e) {
				errorCount += 1;
				try {
					Thread.sleep(25);
				} catch (Exception ignored) {
				}
				if (errorCount > 200) {
					Log.d(TAG, "Multiple WiFi socket connection exceptions - aborting");
					EventBus.getDefault().post(new ClientConnectionErrorEvent(EventType.Type.WIFI));
					return;
				}
			}
		}

		Log.d(TAG, "WiFi client connected to " + mHost + " on port " + mPort);
		EventBus.getDefault().post(new ClientConnectionSuccessEvent(EventType.Type.WIFI));

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
			Log.e(TAG, "WiFi client error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ClientMessageErrorEvent(EventType.Type.WIFI));
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
