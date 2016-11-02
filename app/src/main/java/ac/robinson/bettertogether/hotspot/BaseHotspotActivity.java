package ac.robinson.bettertogether.hotspot;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import ac.robinson.bettertogether.HotspotManagerActivity;
import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.event.BroadcastMessage;

public abstract class BaseHotspotActivity extends AppCompatActivity implements HotspotManagerServiceCommunicator
		.HotspotServiceCallback {

	public static final String HOTSPOT_URL = "hotspot_url";

	private HotspotManagerServiceCommunicator mServiceCommunicator;
	private String mHotspotUrl;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mServiceCommunicator = new HotspotManagerServiceCommunicator(BaseHotspotActivity.this);
		mServiceCommunicator.bindService(BaseHotspotActivity.this);

		if (savedInstanceState != null) {
			mHotspotUrl = savedInstanceState.getString("mHotspotUrl");
		} else {
			Bundle extras = getIntent().getExtras();
			if (extras != null) {
				mHotspotUrl = extras.getString(HOTSPOT_URL);
			}
		}
	}

	protected void initialiseViewAndToolbar(int layout, boolean keepScreenOn) {
		if (keepScreenOn) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}

		setContentView(layout);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	protected void setHotspotUrl(String hotspotUrl) {
		mHotspotUrl = hotspotUrl;
	}

	protected String getHotspotUrl() {
		return mHotspotUrl;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString("mHotspotUrl", mHotspotUrl);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mServiceCommunicator != null) {
			mServiceCommunicator.unbindService(BaseHotspotActivity.this, !isFinishing());  // don't kill service on rotation
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (mHotspotUrl != null) {
			getMenuInflater().inflate(R.menu.menu_mode, menu);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				// NavUtils.navigateUpFromSameTask(BaseHotspotActivity.this); // only API 16+; requires manifest tag
				finish();
				return true;

			case R.id.action_show_qr_code: // every mode activity can help others join the group
				if (mHotspotUrl != null) {
					HotspotManagerActivity.showQRDialog(BaseHotspotActivity.this, mHotspotUrl);
					sendBroadcastMessage(new BroadcastMessage(BroadcastMessage.Type.INTERNAL, HotspotManagerService
							.INTERNAL_BROADCAST_EVENT_SHOW_QR_CODE));
				}
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void sendServiceMessage(int type, String data) {
		mServiceCommunicator.sendServiceMessage(type, data);
	}

	@Override
	public void onServiceMessageReceived(int type, String data) {
		switch (type) {
			case HotspotManagerService.EVENT_LOCAL_CLIENT_ERROR:
				finish(); // our connection to the server failed - need to reconnect from manager activity
				break;
		}
	}

	public void sendBroadcastMessage(BroadcastMessage message) {
		mServiceCommunicator.sendBroadcastMessage(message);
	}

	@Override
	public abstract void onBroadcastMessageReceived(BroadcastMessage message);
}
