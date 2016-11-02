package ac.robinson.bettertogether.mode;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;

import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivity;
import ac.robinson.bettertogether.youtube.YouTubeVideoArrayAdapter;

public class YouTubePlaylistActivity extends BaseHotspotActivity {

	private ListView mListView;
	private ProgressBar mProgressIndicator;

	private String mCurrentPlaylist;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initialiseViewAndToolbar(R.layout.mode_youtube_playlist, true);

		mListView = (ListView) findViewById(R.id.playlist_list);
		mProgressIndicator = (ProgressBar) findViewById(R.id.playlist_progress_indicator);

		if (savedInstanceState != null) {
			mCurrentPlaylist = savedInstanceState.getString("mCurrentPlaylist");
		}

		if (mCurrentPlaylist == null) { // request the playlist if not already loaded
			BroadcastMessage videoRequest = new BroadcastMessage(BroadcastMessage.Type.YOUTUBE, null);
			videoRequest.setCommand(YouTubeDataActivity.GET_PLAYLIST);
			sendBroadcastMessage(videoRequest);
		} else {
			parseResult(mCurrentPlaylist);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString("mCurrentPlaylist", mCurrentPlaylist);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onBroadcastMessageReceived(BroadcastMessage message) {
		if (message.mType == BroadcastMessage.Type.YOUTUBE) {
			if (YouTubeDataActivity.INFO_STATE.equals(message.getCommand())) {
				if (message.mMessage == null) {
					finish(); // null state message means player has exited
				}
			}
		} else if (message.mType == BroadcastMessage.Type.JSON) {
			if (YouTubeDataActivity.GET_PLAYLIST.equals(message.getCommand())) {
				mCurrentPlaylist = message.mMessage;
				parseResult(message.mMessage);
			}
		} else {
			// TODO: handle errors
		}
	}

	private void parseResult(String rawJSON) {
		// preserve scroll position when updating
		int listPosition = mListView.getFirstVisiblePosition();
		View firstView = mListView.getChildAt(0);
		int firstViewOffset = (firstView == null) ? 0 : firstView.getTop();

		YouTubeVideoArrayAdapter adapter = YouTubeVideoArrayAdapter.getInstance(YouTubePlaylistActivity.this, rawJSON, true);
		if (adapter != null) {
			mListView.setAdapter(adapter);
			mListView.setSelectionFromTop(listPosition, firstViewOffset);
			mProgressIndicator.setVisibility(View.GONE);
		} else {
			// TODO: handle errors
		}
	}
}
