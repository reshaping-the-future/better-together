package ac.robinson.bettertogether.event;

public class EventType {
	public enum Type {
		UNKNOWN, // only happens when used in RemoteConnection (on error)
		WIFI,
		BLUETOOTH
	}
}
