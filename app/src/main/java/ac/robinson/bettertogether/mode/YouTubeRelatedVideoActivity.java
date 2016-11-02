package ac.robinson.bettertogether.mode;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;

import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivity;
import ac.robinson.bettertogether.youtube.YouTubeVideoArrayAdapter;

public class YouTubeRelatedVideoActivity extends BaseHotspotActivity {

	private ListView mListView;
	private ProgressBar mProgressIndicator;

	private String mCurrentVideos;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initialiseViewAndToolbar(R.layout.mode_youtube_related_videos, true);

		mListView = (ListView) findViewById(R.id.related_videos_list);
		mProgressIndicator = (ProgressBar) findViewById(R.id.related_videos_progress_indicator);

		if (savedInstanceState != null) {
			mCurrentVideos = savedInstanceState.getString("mCurrentVideos");
		}

		if (mCurrentVideos == null) { // request the playlist if not already loaded
			BroadcastMessage videoRequest = new BroadcastMessage(BroadcastMessage.Type.YOUTUBE, null);
			videoRequest.setCommand(YouTubeDataActivity.GET_RELATED_VIDEOS);
			sendBroadcastMessage(videoRequest);
		} else {
			parseResult(mCurrentVideos);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString("mCurrentVideos", mCurrentVideos);
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
			if (YouTubeDataActivity.GET_RELATED_VIDEOS.equals(message.getCommand())) {
				mCurrentVideos = message.mMessage;
				parseResult(message.mMessage);
			}
		} else {
			// TODO: handle errors
		}
	}

	private void parseResult(String rawJSON) {
		// hacky special case so we re-show the loading indicator when getting results for a new video
		if (YouTubeDataActivity.LOADING_JSON.equals(rawJSON)) {
			mListView.setAdapter(null);
			mProgressIndicator.setVisibility(View.VISIBLE);
		} else {
			YouTubeVideoArrayAdapter adapter = YouTubeVideoArrayAdapter.getInstance(YouTubeRelatedVideoActivity.this, rawJSON,
					false);
			if (adapter != null) {
				mListView.setAdapter(adapter);
				mProgressIndicator.setVisibility(View.GONE);
			} else {
				// TODO: handle errors
			}
		}
	}
}
