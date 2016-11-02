package ac.robinson.bettertogether.event;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BroadcastMessage implements Serializable {
	private final static long serialVersionUID = 1;

	public enum Type {
		INTERNAL,
		MESSAGE,
		KEYBOARD,
		JSON,
		YOUTUBE
	}

	private String mFrom;

	public final BroadcastMessage.Type mType;
	public final String mMessage;
	private String mCommand = null;
	private int[] mExtras = null;

	public BroadcastMessage(BroadcastMessage.Type type, String message) {
		mType = type;
		mMessage = message;
	}

	public String getFrom() {
		return mFrom;
	}

	public void setFrom(String from) {
		mFrom = from;
	}

	public boolean hasExtras() {
		return mExtras != null;
	}

	public int[] getExtras() {
		return mExtras;
	}

	// returns the value of the first extra, or zero if it doesn't exist. Note that this is obviously incompatible with extra
	// values that could be zero...
	public int getFirstExtra() {
		if (mExtras != null && mExtras.length == 1) {
			return mExtras[0];
		}
		return 0;
	}

	public void setExtras(int[] extras) {
		mExtras = extras;
	}

	public boolean hasCommand() {
		return mCommand != null;
	}

	public String getCommand() {
		return mCommand;
	}

	public void setCommand(String command) {
		mCommand = command;
	}

	public static BroadcastMessage fromString(String message) throws IOException, ClassNotFoundException {
		byte[] data = Base64.decode(message, Base64.DEFAULT);
		ObjectInputStream objectInputStream = new ObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(data)));
		Object object = objectInputStream.readObject();
		objectInputStream.close();
		return (BroadcastMessage) object;
	}

	public static String toString(BroadcastMessage message) throws IOException {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		ObjectOutputStream objectOutputStream = new ObjectOutputStream(new GZIPOutputStream(byteArrayOutputStream));
		objectOutputStream.writeObject(message);
		objectOutputStream.close();
		return new String(Base64.encode(byteArrayOutputStream.toByteArray(), Base64.DEFAULT));
	}

	public static List<String> splitEqually(String message, int size) {
		// see: http://stackoverflow.com/a/3760193
		List<String> result = new ArrayList<>((message.length() + size - 1) / size);
		for (int start = 0; start < message.length(); start += size) {
			result.add(message.substring(start, Math.min(message.length(), start + size)));
		}
		return result;
	}
}
