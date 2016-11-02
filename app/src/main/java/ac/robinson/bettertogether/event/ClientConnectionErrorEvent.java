package ac.robinson.bettertogether.event;

public class ClientConnectionErrorEvent {
	public EventType.Type mType;

	public ClientConnectionErrorEvent(EventType.Type type) {
		mType = type;
	}
}
