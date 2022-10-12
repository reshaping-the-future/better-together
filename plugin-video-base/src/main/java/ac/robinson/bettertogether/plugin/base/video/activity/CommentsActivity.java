/*
 * Copyright (C) 2017 The Better Together Toolkit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package ac.robinson.bettertogether.plugin.base.video.activity;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import ac.robinson.bettertogether.api.BasePluginActivity;
import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.plugin.base.video.R;
import ac.robinson.bettertogether.plugin.base.video.youtube.MessageType;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

public class CommentsActivity extends BasePluginActivity {

	private ListView mListView;
	private ProgressBar mProgressIndicator;

	private String mCurrentComments;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.mode_youtube_comments);

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setDisplayShowTitleEnabled(true);
		}

		mListView = findViewById(R.id.comments_list);
		mProgressIndicator = findViewById(R.id.comments_progress_indicator);

		if (savedInstanceState != null) {
			mCurrentComments = savedInstanceState.getString("mCurrentComments");
		}

		if (mCurrentComments == null) { // request the comments if not already loaded
			BroadcastMessage videoRequest = new BroadcastMessage(MessageType.COMMAND_GET_COMMENTS, null);
			sendMessage(videoRequest);
		} else {
			parseComments(mCurrentComments);
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putString("mCurrentComments", mCurrentComments);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onMessageReceived(@NonNull BroadcastMessage message) {
		switch (message.getType()) {
			case MessageType.COMMAND_EXIT:
				finish(); // player has exited - we must finish too
				break;

			case MessageType.JSON_COMMENTS:
				mCurrentComments = message.getMessage(); // save so we avoid reloading on rotate
				parseComments(message.getMessage());
				break;

			default:
				break;
		}
	}

	private void parseComments(String rawJSON) {
		// hacky special case so we re-show the loading indicator when getting results for a new video
		if (VideoActivity.LOADING_JSON.equals(rawJSON)) {
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

			mListView.setAdapter(new CommentArrayAdapter(CommentsActivity.this, comments));
			mProgressIndicator.setVisibility(View.GONE);
		}
	}

	private class CommentArrayAdapter extends ArrayAdapter<YouTubeCommentItem> {
		private final ArrayList<YouTubeCommentItem> mCommentsList;

		CommentArrayAdapter(Context context, ArrayList<YouTubeCommentItem> items) {
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
				commentHolder.mAuthor = currentView.findViewById(R.id.comment_author);
				commentHolder.mComment = currentView.findViewById(R.id.comment_text);
				currentView.setTag(commentHolder);
			} else {
				currentView = convertView;
				commentHolder = (YouTubeCommentHolder) convertView.getTag();
			}

			YouTubeCommentItem currentComment = mCommentsList.get(position);
			commentHolder.mAuthor.setText(currentComment.mAuthor);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				commentHolder.mComment.setText(Html.fromHtml(currentComment.mComment, Html.FROM_HTML_MODE_LEGACY));
			} else {
				commentHolder.mComment.setText(Html.fromHtml(currentComment.mComment));
			}
			return currentView;
		}
	}

	private static class YouTubeCommentItem {
		String mAuthor;
		String mComment;

		YouTubeCommentItem(String author, String comment) {
			mAuthor = author;
			mComment = comment;
		}
	}

	private static class YouTubeCommentHolder {
		TextView mAuthor;
		TextView mComment;
	}
}
