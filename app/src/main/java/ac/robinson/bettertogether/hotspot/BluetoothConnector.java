package ac.robinson.bettertogether.hotspot;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

// based on: https://github.com/arissa34/Android-Multi-Bluetooth-Library (license: "beer-ware")
public class BluetoothConnector {

	private static final String TAG = "BluetoothConnector";

	private BluetoothSocketWrapper mBluetoothSocket;

	private BluetoothDevice mBluetoothDevice;
	private boolean mPreferSecureConnection;
	private UUID mUuid;

	public BluetoothConnector(BluetoothDevice device, boolean preferSecureConnection, UUID uuid) {
		mBluetoothDevice = device;
		mPreferSecureConnection = preferSecureConnection;
		mUuid = uuid;
	}

	@SuppressLint("NewApi") // we *do* check the api level for createInsecureRfcommSocketToServiceRecord
	public BluetoothSocketWrapper connect() throws IOException {
		BluetoothSocket socket;
		if (mPreferSecureConnection || Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1) {
			socket = mBluetoothDevice.createRfcommSocketToServiceRecord(mUuid);
		} else {
			socket = mBluetoothDevice.createInsecureRfcommSocketToServiceRecord(mUuid);
		}
		mBluetoothSocket = new NativeBluetoothSocket(socket);

		try {
			mBluetoothSocket.connect();
			return mBluetoothSocket;

		} catch (IOException e) {
			try {
				mBluetoothSocket = new FallbackBluetoothSocket(mBluetoothSocket.getUnderlyingSocket());
				Thread.sleep(500); // TODO: do we actually need to wait for the socket to be initialised?
				mBluetoothSocket.connect();
				return mBluetoothSocket;

			} catch (FallbackException e1) {
				Log.d(TAG, "Could not initialize FallbackBluetoothSocket", e);
			} catch (IOException e1) {
				Log.d(TAG, "Fallback failed. Cancelling.", e1);
			} catch (Exception e1) {
				Log.d(TAG, "All attempts failed. Cancelling.", e1);
			}
		}

		Log.d(TAG, "Could not connect to BluetoothDevice: " + mBluetoothDevice.getAddress());
		throw new IOException();
	}

	public void close() {
		if (mBluetoothSocket != null) {
			try {
				mBluetoothSocket.close();
			} catch (IOException ignored) {
			}
		}
	}

	public interface BluetoothSocketWrapper {
		void connect() throws IOException;

		void close() throws IOException;

		BluetoothSocket getUnderlyingSocket();
	}

	public static class NativeBluetoothSocket implements BluetoothSocketWrapper {
		private BluetoothSocket mSocket;

		public NativeBluetoothSocket(BluetoothSocket socket) {
			mSocket = socket;
		}

		@Override
		public void connect() throws IOException {
			mSocket.connect();
		}

		@Override
		public void close() throws IOException {
			mSocket.close();
		}

		@Override
		public BluetoothSocket getUnderlyingSocket() {
			return mSocket;
		}
	}

	public class FallbackBluetoothSocket extends NativeBluetoothSocket {
		private BluetoothSocket mFallbackSocket;

		public FallbackBluetoothSocket(BluetoothSocket socket) throws FallbackException {
			super(socket);
			try {
				Class<?> cls = socket.getRemoteDevice().getClass();
				Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};
				Method m = cls.getMethod("createRfcommSocket", paramTypes);
				Object[] params = new Object[]{1};
				mFallbackSocket = (BluetoothSocket) m.invoke(socket.getRemoteDevice(), params);
			} catch (Exception e) {
				throw new FallbackException(e); // TODO: do we actually want to do this? (or just retry?)
			}
		}

		@Override
		public void connect() throws IOException {
			mFallbackSocket.connect();
		}

		@Override
		public void close() throws IOException {
			mFallbackSocket.close();
		}
	}

	public static class FallbackException extends Exception {
		private static final long serialVersionUID = 1;

		public FallbackException(Exception e) {
			super(e);
		}

	}
}
