package ac.robinson.bettertogether;

import android.app.Application;
import android.content.Intent;

import ac.robinson.bettertogether.hotspot.HotspotManagerService;

public class BetterTogetherApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		startService(new Intent(getBaseContext(), HotspotManagerService.class));
	}
}
