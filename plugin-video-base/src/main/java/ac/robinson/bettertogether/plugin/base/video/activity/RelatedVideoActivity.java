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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.ProgressBar;

import ac.robinson.bettertogether.api.BasePluginActivity;
import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.plugin.base.video.R;
import ac.robinson.bettertogether.plugin.base.video.youtube.MessageType;
import ac.robinson.bettertogether.plugin.base.video.youtube.YouTubeVideoArrayAdapter;

public class RelatedVideoActivity extends BasePluginActivity {

	private ListView mListView;
	private ProgressBar mProgressIndicator;

	private String mCurrentVideos;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.mode_youtube_related_videos);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setDisplayShowTitleEnabled(true);
		}

		mListView = (ListView) findViewById(R.id.related_videos_list);
		mProgressIndicator = (ProgressBar) findViewById(R.id.related_videos_progress_indicator);

		if (savedInstanceState != null) {
			mCurrentVideos = savedInstanceState.getString("mCurrentVideos");
		}

		if (mCurrentVideos == null) { // request the playlist if not already loaded
			BroadcastMessage videoRequest = new BroadcastMessage(MessageType.COMMAND_GET_RELATED, null);
			sendMessage(videoRequest);
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
	protected void onMessageReceived(@NonNull BroadcastMessage message) {
		switch (message.getType()) {
			case MessageType.COMMAND_EXIT:
				finish(); // player has exited - we must finish too
				break;

			case MessageType.JSON_RELATED:
				mCurrentVideos = message.getMessage();
				parseResult(message.getMessage());
				break;

			default:
				break;
		}
	}

	private void parseResult(String rawJSON) {
		// hacky special case so we re-show the loading indicator when getting results for a new video
		if (VideoActivity.LOADING_JSON.equals(rawJSON)) {
			mListView.setAdapter(null);
			mProgressIndicator.setVisibility(View.VISIBLE);
		} else {
			YouTubeVideoArrayAdapter adapter = YouTubeVideoArrayAdapter.getInstance(RelatedVideoActivity.this, rawJSON, false);
			if (adapter != null) {
				mListView.setAdapter(adapter);
				mProgressIndicator.setVisibility(View.GONE);
			} else {
				// TODO: handle errors
			}
		}
	}
}
