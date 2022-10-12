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
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;

import ac.robinson.bettertogether.api.BasePluginActivity;
import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.plugin.base.video.R;
import ac.robinson.bettertogether.plugin.base.video.youtube.MessageType;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

public class ControlsActivity extends BasePluginActivity {

	private boolean mIsPlaying;

	private ImageButton mPlayPauseButton;
	private SeekBar mSeekBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.mode_youtube_controls);

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setDisplayShowTitleEnabled(true);
		}

		mPlayPauseButton = findViewById(R.id.play_pause_button);
		mSeekBar = findViewById(R.id.video_seek_bar);
		mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
				if (fromUser) {
					BroadcastMessage seekMessage = new BroadcastMessage(MessageType.COMMAND_SEEK, null);
					seekMessage.setIntExtra(progressValue * 1000); // convert to milliseconds for YouTube
					sendMessage(seekMessage);
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();

		BroadcastMessage playbackDuration = new BroadcastMessage(MessageType.COMMAND_GET_STATE, null);
		sendMessage(playbackDuration);
	}

	public void handleClick(View view) {
		final int viewId = view.getId();
		// can't use resource id switch statements in library modules
		if (viewId == R.id.play_pause_button) {
			BroadcastMessage playPauseMessage = new BroadcastMessage(
					mIsPlaying ? MessageType.COMMAND_PAUSE : MessageType.COMMAND_PLAY, null);
			sendMessage(playPauseMessage);
		} else if (viewId == R.id.previous_button || viewId == R.id.next_button) {
			BroadcastMessage skipMessage = new BroadcastMessage(MessageType.COMMAND_SKIP, null);
			skipMessage.setIntExtra(viewId == R.id.previous_button ? -1 : 1);
			sendMessage(skipMessage);
		}
	}

	@Override
	protected void onMessageReceived(@NonNull BroadcastMessage message) {
		switch (message.getType()) {
			case MessageType.COMMAND_EXIT:
				finish(); // player has exited - we must finish too
				break;

			case MessageType.INFO_DURATION:
				int newMax = message.getIntExtra() / 1000; // convert from milliseconds
				if (mSeekBar.getMax() != newMax) {
					mSeekBar.setMax(newMax);
				}
				break;

			case MessageType.COMMAND_PLAY:
			case MessageType.COMMAND_PAUSE:
				mSeekBar.setProgress(message.getIntExtra() / 1000); // convert from milliseconds
				mIsPlaying = MessageType.COMMAND_PLAY == message.getType();
				if (mIsPlaying) {
					mPlayPauseButton.setImageResource(R.drawable.ic_pause_red_800_48dp);
				} else {
					mPlayPauseButton.setImageResource(R.drawable.ic_play_arrow_red_800_48dp);
				}
				break;

			default:
				break;
		}
	}
}
