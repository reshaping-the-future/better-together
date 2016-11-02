package ac.robinson.bettertogether.mode;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivity;

public class YouTubeCommentsActivity extends BaseHotspotActivity {

	private ListView mListView;
	private ProgressBar mProgressIndicator;

	private String mCurrentComments;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initialiseViewAndToolbar(R.layout.mode_youtube_comments, true);

		mListView = (ListView) findViewById(R.id.comments_list);
		mProgressIndicator = (ProgressBar) findViewById(R.id.comments_progress_indicator);

		if (savedInstanceState != null) {
			mCurrentComments = savedInstanceState.getString("mCurrentComments");
		}

		if (mCurrentComments == null) { // request the comments if not already loaded
			BroadcastMessage videoRequest = new BroadcastMessage(BroadcastMessage.Type.YOUTUBE, null);
			videoRequest.setCommand(YouTubeDataActivity.GET_VIDEO_COMMENTS);
			sendBroadcastMessage(videoRequest);
		} else {
			parseComments(mCurrentComments);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString("mCurrentComments", mCurrentComments);
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
			if (YouTubeDataActivity.GET_VIDEO_COMMENTS.equals(message.getCommand())) {
				mCurrentComments = message.mMessage; // save so we avoid reloading on rotate
				parseComments(message.mMessage);
			}
		} else {
			// TODO: handle errors
		}
	}

	public void parseComments(String rawJSON) {
		// hacky special case so we re-show the loading indicator when getting results for a new video
		if (YouTubeDataActivity.LOADING_JSON.equals(rawJSON)) {
			mListView.setAdapter(null);
			mProgressIndicator.setVisibility(View.VISIBLE);
		} else {
			JSONArray jsonArray;
			try {
				jsonArray = new JSONArray(rawJSON);
			} catch (JSONException e) {
				return; // TODO: handle error
			}

			ArrayList<YouTubeCommentItem> comments = new ArrayList<>();
			for (int i = 0; i < jsonArray.length(); i++) {
				try {
					JSONObject result = jsonArray.getJSONObject(i);
					String name = result.getString("name");
					String comment = result.getString("comment");
					comments.add(new YouTubeCommentItem(name, comment));
				} catch (JSONException ignored) {
				}
			}

			mListView.setAdapter(new CommentArrayAdapter(YouTubeCommentsActivity.this, comments));
			mProgressIndicator.setVisibility(View.GONE);
		}
	}

	public class CommentArrayAdapter extends ArrayAdapter<YouTubeCommentItem> {
		private final ArrayList<YouTubeCommentItem> mCommentsList;

		public CommentArrayAdapter(Context context, ArrayList<YouTubeCommentItem> items) {
			super(context, R.layout.youtube_comment_list_item, items);
			mCommentsList = items;
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			View currentView;
			YouTubeCommentHolder commentHolder;

			if (convertView == null) {
				currentView = getLayoutInflater().inflate(R.layout.youtube_comment_list_item, parent, false);
				commentHolder = new YouTubeCommentHolder();
				commentHolder.mAuthor = (TextView) currentView.findViewById(R.id.comment_author);
				commentHolder.mComment = (TextView) currentView.findViewById(R.id.comment_text);
				currentView.setTag(commentHolder);
			} else {
				currentView = convertView;
				commentHolder = (YouTubeCommentHolder) convertView.getTag();
			}

			YouTubeCommentItem currentComment = mCommentsList.get(position);
			commentHolder.mAuthor.setText(currentComment.mAuthor);
			commentHolder.mComment.setText(Html.fromHtml(currentComment.mComment));
			return currentView;
		}
	}

	private class YouTubeCommentItem {
		public String mAuthor;
		public String mComment;

		public YouTubeCommentItem(String author, String comment) {
			mAuthor = author;
			mComment = comment;
		}
	}

	private class YouTubeCommentHolder {
		public TextView mAuthor;
		public TextView mComment;
	}
}
