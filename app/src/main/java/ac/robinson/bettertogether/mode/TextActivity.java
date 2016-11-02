package ac.robinson.bettertogether.mode;

import android.os.Bundle;

import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivity;

public class TextActivity extends BaseHotspotActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initialiseViewAndToolbar(R.layout.mode_text, false);
	}

	@Override
	public void onBroadcastMessageReceived(BroadcastMessage message) {
		// nothing to do - text entry is handled in a custom remote keyboard so that remote control can be used in any
		// application; this activity is just to demonstrate how text entry might work
	}
}
