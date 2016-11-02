package ac.robinson.bettertogether.mode;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivity;
import ac.robinson.bettertogether.youtube.YouTubeVideoArrayAdapter;

public class YouTubeSearchActivity extends BaseHotspotActivity {

	private ListView mListView;
	private ProgressBar mProgressIndicator;
	private EditText mSearchQuery;

	private String mCurrentVideos;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initialiseViewAndToolbar(R.layout.mode_youtube_search, true);

		mListView = (ListView) findViewById(R.id.search_results_list);
		mProgressIndicator = (ProgressBar) findViewById(R.id.search_results_progress_indicator);
		mSearchQuery = (EditText) findViewById(R.id.search_query);

		mSearchQuery.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEARCH) {
					String query = mSearchQuery.getText().toString();
					if (!TextUtils.isEmpty(query)) {

						//mSearchQuery.clearFocus(); // so the keyboard is hidden TODO: doesn't work...

						mListView.setAdapter(null);
						mProgressIndicator.setVisibility(View.VISIBLE);

						BroadcastMessage jsonResult = new BroadcastMessage(BroadcastMessage.Type.YOUTUBE, query);
						jsonResult.setCommand(YouTubeDataActivity.GET_SEARCH_RESULTS);
						sendBroadcastMessage(jsonResult);
					}

					return false; // false so that keyboard is hidden automatically
				}
				return false;
			}
		});

		if (savedInstanceState != null) {
			mCurrentVideos = savedInstanceState.getString("mCurrentVideos"); // reload existing results if present
			if (mCurrentVideos != null) {
				parseResult(mCurrentVideos);
			}
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
			if (YouTubeDataActivity.GET_SEARCH_RESULTS.equals(message.getCommand())) {
				mCurrentVideos = message.mMessage;
				parseResult(message.mMessage);
			}
		} else {
			// TODO: handle errors
		}
	}

	public void handleClick(View view) {
		switch (view.getId()) {
			case R.id.search_button:
				mSearchQuery.onEditorAction(EditorInfo.IME_ACTION_SEARCH); // call via this method so the keyboard gets hidden
				break;
		}
	}

	private void parseResult(String rawJSON) {
		YouTubeVideoArrayAdapter adapter = YouTubeVideoArrayAdapter.getInstance(YouTubeSearchActivity.this, rawJSON, false);
		if (adapter != null) {
			mListView.setAdapter(adapter);
			mProgressIndicator.setVisibility(View.GONE);
		} else {
			// TODO: handle errors
		}
	}
}
