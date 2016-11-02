package ac.robinson.bettertogether.mode;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;

import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivity;

public class YouTubeControlsActivity extends BaseHotspotActivity {

	private boolean mIsPlaying;

	private ImageButton mPlayPauseButton;
	private SeekBar mSeekBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initialiseViewAndToolbar(R.layout.mode_youtube_controls, true);

		mPlayPauseButton = (ImageButton) findViewById(R.id.play_pause_button);
		mSeekBar = (SeekBar) findViewById(R.id.video_seek_bar);
		mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
				if (fromUser) {
					BroadcastMessage seekMessage = new BroadcastMessage(BroadcastMessage.Type.YOUTUBE, null);
					seekMessage.setCommand(YouTubeDataActivity.COMMAND_SEEK);
					seekMessage.setExtras(new int[]{progressValue * 1000}); // convert to milliseconds
					sendBroadcastMessage(seekMessage);
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

	public void handleClick(View view) {
		final int viewId = view.getId();
		switch (viewId) {
			case R.id.play_pause_button:
				BroadcastMessage playPauseMessage = new BroadcastMessage(BroadcastMessage.Type.YOUTUBE, null);
				playPauseMessage.setCommand(mIsPlaying ? YouTubeDataActivity.COMMAND_PAUSE : YouTubeDataActivity.COMMAND_PLAY);
				sendBroadcastMessage(playPauseMessage);
				break;

			case R.id.previous_button:
			case R.id.next_button:
				BroadcastMessage skipMessage = new BroadcastMessage(BroadcastMessage.Type.YOUTUBE, null);
				skipMessage.setCommand(YouTubeDataActivity.COMMAND_SKIP);
				skipMessage.setExtras(new int[]{viewId == R.id.previous_button ? -1 : 1});
				sendBroadcastMessage(skipMessage);
				break;
		}
	}

	@Override
	public void onBroadcastMessageReceived(BroadcastMessage message) {
		if (message.mType == BroadcastMessage.Type.YOUTUBE) {
			if (YouTubeDataActivity.INFO_STATE.equals(message.getCommand())) {
				if (message.mMessage == null) {
					finish(); // null state message means player has exited

				} else {
					if (message.hasExtras()) {
						int[] extras = message.getExtras();
						if (extras.length == 2) {
							switch (message.mMessage) {
								case YouTubeDataActivity.COMMAND_PLAY:
								case YouTubeDataActivity.COMMAND_PAUSE:
									int newMax = extras[1] / 1000; // convert from milliseconds
									if (mSeekBar.getMax() != newMax) {
										mSeekBar.setMax(newMax);
									}
									mSeekBar.setProgress(extras[0] / 1000); // convert from milliseconds
									mIsPlaying = YouTubeDataActivity.COMMAND_PLAY.equals(message.mMessage);
									if (mIsPlaying) {
										mPlayPauseButton.setImageResource(R.drawable.ic_pause_red_600_48dp);
									} else {
										mPlayPauseButton.setImageResource(R.drawable.ic_play_arrow_red_600_48dp);
									}
									break;
							}
						}
					}
				}
			}
		} else {

		}
	}
}
