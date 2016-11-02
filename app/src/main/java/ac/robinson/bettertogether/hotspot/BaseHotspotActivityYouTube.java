package ac.robinson.bettertogether.hotspot;


import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import ac.robinson.bettertogether.HotspotManagerActivity;
import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.youtube.YouTubeFailureRecoveryActivity;

// TODO: SWITCH TO USING FRAGMENTS FOR YOUTUBE: https://developers.google.com/youtube/android/player/reference/com/google/android/youtube/player/YouTubePlayerFragment#Overview
public abstract class BaseHotspotActivityYouTube extends YouTubeFailureRecoveryActivity implements HotspotManagerServiceCommunicator
.HotspotServiceCallback  {

    public static final String HOTSPOT_URL = "hotspot_url";

    private HotspotManagerServiceCommunicator mServiceCommunicator;
    private String mHotspotUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mServiceCommunicator = new HotspotManagerServiceCommunicator(BaseHotspotActivityYouTube.this);
        mServiceCommunicator.bindService(BaseHotspotActivityYouTube.this);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mHotspotUrl = extras.getString(HOTSPOT_URL);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceCommunicator != null) {
            mServiceCommunicator.unbindService(BaseHotspotActivityYouTube.this, !isFinishing());  // don't kill service on rotation
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
            case R.id.action_show_qr_code: // every mode activity can help others join the group
                HotspotManagerActivity.showQRDialog(BaseHotspotActivityYouTube.this, mHotspotUrl);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceMessageReceived(int type, String data) {
        switch (type) {
            case HotspotManagerService.EVENT_LOCAL_CLIENT_ERROR:
                finish(); // our connection to the server failed - need to reconnect from manager activity
                break;
        }
    }

    @Override
    public abstract void onBroadcastMessageReceived(BroadcastMessage message);

    public void sendBroadcastMessage(BroadcastMessage message) {
        mServiceCommunicator.sendBroadcastMessage(message);
    }

}
