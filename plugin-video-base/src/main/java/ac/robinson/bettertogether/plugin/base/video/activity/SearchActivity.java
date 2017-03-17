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
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import ac.robinson.bettertogether.api.BasePluginActivity;
import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.plugin.base.video.R;
import ac.robinson.bettertogether.plugin.base.video.youtube.MessageType;
import ac.robinson.bettertogether.plugin.base.video.youtube.YouTubeVideoArrayAdapter;

public class SearchActivity extends BasePluginActivity {

	private ListView mListView;
	private ProgressBar mProgressIndicator;
	private EditText mSearchQuery;

	private String mCurrentVideos;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.mode_youtube_search);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setDisplayShowTitleEnabled(true);
		}

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

						BroadcastMessage jsonResult = new BroadcastMessage(MessageType.COMMAND_GET_SEARCH, query);
						sendMessage(jsonResult);
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
	protected void onMessageReceived(@NonNull BroadcastMessage message) {
		switch (message.getType()) {
			case MessageType.COMMAND_EXIT:
				finish(); // player has exited - we must finish too
				break;

			case MessageType.JSON_SEARCH:
				mCurrentVideos = message.getMessage();
				parseResult(message.getMessage());
				break;

			default:
				break;
		}
	}

	public void handleClick(View view) {
		if (view.getId() == R.id.search_button) { // can't use resource id switch statements in library modules
			mSearchQuery.onEditorAction(EditorInfo.IME_ACTION_SEARCH); // call via this method so the keyboard gets hidden
		}
	}

	private void parseResult(String rawJSON) {
		YouTubeVideoArrayAdapter adapter = YouTubeVideoArrayAdapter.getInstance(SearchActivity.this, rawJSON, false);
		if (adapter != null) {
			mListView.setAdapter(adapter);
			mProgressIndicator.setVisibility(View.GONE);
		} else {
			// TODO: handle errors
		}
	}
}
