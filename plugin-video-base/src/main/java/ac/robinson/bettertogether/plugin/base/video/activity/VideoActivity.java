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
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.WindowManager;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.StringRequestListener;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayer.ErrorReason;
import com.google.android.youtube.player.YouTubePlayer.PlaybackEventListener;
import com.google.android.youtube.player.YouTubePlayer.PlayerStateChangeListener;
import com.google.android.youtube.player.YouTubePlayer.PlaylistEventListener;
import com.google.android.youtube.player.YouTubePlayerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;

import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.api.messaging.PluginConnectionDelegate;
import ac.robinson.bettertogether.plugin.base.video.R;
import ac.robinson.bettertogether.plugin.base.video.youtube.DeveloperKey;
import ac.robinson.bettertogether.plugin.base.video.youtube.MessageType;
import ac.robinson.bettertogether.plugin.base.video.youtube.YouTubeFailureRecoveryActivity;
import ac.robinson.bettertogether.plugin.base.video.youtube.YouTubeVideoItem;

public class VideoActivity extends YouTubeFailureRecoveryActivity {

	private static final String JSON_INTERFACE_URL = "http://cs.swan.ac.uk/~cssimonr/projects/better-together/youtube.php";

	private PluginConnectionDelegate mDelegate;

	private ArrayList<YouTubeVideoItem> mPlaylist = new ArrayList<>();
	private int mPlaylistPosition = -1;
	private boolean mHasRequestedRelatedVideos = false;
	private boolean mHasRequestedComments = false;

	private YouTubePlayerView mPlayerView;
	private YouTubePlayer mPlayer;

	private YouTubePlaylistEventListener mPlaylistEventListener;
	private YouTubePlayerStateChangeListener mPlayerStateChangeListener;
	private YouTubePlaybackEventListener mPlaybackEventListener;

	private Handler mStateUpdateHandler = new Handler();
	private static final int STATE_UPDATE_INTERVAL = 1000; // milliseconds

	public static final String LOADING_JSON = "[{}]"; // hacky!

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mDelegate = new PluginConnectionDelegate(VideoActivity.this, mMessageReceivedCallback);
		mDelegate.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.mode_youtube_video_player);
		mPlayerView = (YouTubePlayerView) findViewById(R.id.youtube_view);

		mPlayerStateChangeListener = new YouTubePlayerStateChangeListener();
		mPlaybackEventListener = new YouTubePlaybackEventListener();
		mPlaylistEventListener = new YouTubePlaylistEventListener();

		if (savedInstanceState != null) {
			mPlaylist = savedInstanceState.getParcelableArrayList("mPlaylist");
			mPlaylistPosition = savedInstanceState.getInt("mPlaylistPosition", -1);
			mHasRequestedRelatedVideos = savedInstanceState.getBoolean("mHasRequestedRelatedVideos", false);
			mHasRequestedComments = savedInstanceState.getBoolean("mHasRequestedComments", false);
		}
		if (mPlaylistPosition < 0 && mPlaylist.size() > 0) {
			mPlaylistPosition = 0; // just in case
		}

		mPlayerView.initialize(DeveloperKey.DEVELOPER_KEY, this);
	}

	private PluginConnectionDelegate.PluginMessageCallback mMessageReceivedCallback = new PluginConnectionDelegate
			.PluginMessageCallback() {
		@Override
		public void onMessageReceived(@NonNull BroadcastMessage message) {
			VideoActivity.this.onMessageReceived(message);
		}
	};

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putParcelableArrayList("mPlaylist", mPlaylist);
		outState.putInt("mPlaylistPosition", mPlaylistPosition);
		outState.putBoolean("mHasRequestedRelatedVideos", mHasRequestedRelatedVideos);
		outState.putBoolean("mHasRequestedComments", mHasRequestedComments);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDelegate.onDestroy();
		mStateUpdateHandler.removeCallbacks(mStateUpdateRunnable);
	}

	@Override
	public void finish() {
		super.finish();
		// close related remote clients
		BroadcastMessage finishMessage = new BroadcastMessage(MessageType.COMMAND_EXIT, null);
		sendMessage(finishMessage);
	}

	@Override
	public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer player, boolean wasRestored) {
		mPlayer = player;
		mPlayer.setPlayerStateChangeListener(mPlayerStateChangeListener);
		mPlayer.setPlaybackEventListener(mPlaybackEventListener);
		mPlayer.setPlaylistEventListener(mPlaylistEventListener);
		mPlayer.setShowFullscreenButton(false); // always fullscreen
		mPlayer.setFullscreen(true);
		// mPlayer.setPlayerStyle(YouTubePlayer.PlayerStyle.CHROMELESS);
	}

	@Override
	protected YouTubePlayer.Provider getYouTubePlayerProvider() {
		return mPlayerView;
	}

	private void playVideo(int playlistPosition) {
		YouTubeVideoItem selectedEntry = mPlaylist.get(playlistPosition);
		if (mPlayer != null) {
			// note: cueVideo/Playlist has ads, which breaks playback - see: https://stackoverflow.com/questions/29715990
			if (selectedEntry.mIsPlaylist) {
				mPlayer.loadPlaylist(selectedEntry.mId);
				// mPlayer.cuePlaylist(selectedEntry.mId);
			} else {
				mPlayer.loadVideo(selectedEntry.mId);
				// mPlayer.cueVideo(selectedEntry.mId);
			}
		}
	}

	private void sendMessage(@NonNull BroadcastMessage message) {
		mDelegate.sendMessage(message);
	}

	private void onMessageReceived(BroadcastMessage message) {
		YouTubeVideoItem currentVideo = null;
		if (mPlaylist.size() > 0) {
			currentVideo = mPlaylist.get(mPlaylistPosition);
		}

		final int command = message.getType();
		switch (command) {
			case MessageType.COMMAND_PLAY:
			case MessageType.COMMAND_PAUSE:
			case MessageType.COMMAND_SKIP:
			case MessageType.COMMAND_SEEK:
				if (currentVideo != null && mPlayer != null) {
					switch (command) {
						case MessageType.COMMAND_PLAY:
							mPlayer.play();
							break;

						case MessageType.COMMAND_PAUSE:
							mPlayer.pause();
							break;

						case MessageType.COMMAND_SKIP:
							skipVideo(currentVideo, message.getIntExtra());
							break;

						case MessageType.COMMAND_SEEK:
							mPlayer.seekToMillis(message.getIntExtra());
							break;

						default:
							break;
					}
				}
				break;

			case MessageType.COMMAND_GET_STATE:
				postDurationStateUpdate();
				postPlaybackStateUpdate();
				break;

			case MessageType.COMMAND_ADD:
				try {
					JSONObject videoJSONObject = new JSONObject(message.getMessage());
					YouTubeVideoItem videoItem = YouTubeVideoItem.fromJSONObject(videoJSONObject);
					if (videoItem != null) {
						mPlaylist.add(videoItem);
						if (mPlaylist.size() == 1) {  // auto play if this is the only item in the list
							mPlaylistPosition = 0;
							playVideo(mPlaylistPosition);
						}
						forwardPlaylistJSON(); // update the playlist on remote clients
					}
				} catch (JSONException ignored) {
					// TODO: handle errors
				}
				break;

			case MessageType.COMMAND_SELECT:
				try {
					JSONObject videoJSONObject = new JSONObject(message.getMessage());
					YouTubeVideoItem videoItem = YouTubeVideoItem.fromJSONObject(videoJSONObject);
					if (videoItem != null) {
						int i = 0;
						boolean videoFound = false;
						for (YouTubeVideoItem item : mPlaylist) {
							if (item.mId.equals(videoItem.mId)) {
								videoFound = true;
								mPlaylistPosition = i;
								playVideo(mPlaylistPosition);
								break;
							}
							i += 1;
						}

						if (!videoFound) { // add and play if not found
							mPlaylist.add(videoItem);
							mPlaylistPosition = mPlaylist.size() - 1;
							playVideo(mPlaylistPosition);
							forwardPlaylistJSON(); // update the playlist on remote clients
						}
					}
				} catch (JSONException ignored) {
					// TODO: handle errors
				}
				break;

			case MessageType.COMMAND_GET_SEARCH:
				try {
					getAndForwardCompactedJSON(command, URLEncoder.encode(message.getMessage(), Charset.defaultCharset().name
							()));
				} catch (UnsupportedEncodingException e) {
					// TODO: handle this
				}
				break;

			case MessageType.COMMAND_GET_RELATED:
				mHasRequestedRelatedVideos = true;
				if (currentVideo != null) {
					getAndForwardCompactedJSON(command, currentVideo.mId);
				}
				break;

			case MessageType.COMMAND_GET_COMMENTS:
				mHasRequestedComments = true;
				if (currentVideo != null) {
					getAndForwardCompactedJSON(command, currentVideo.mId);
				}
				break;

			case MessageType.COMMAND_GET_PLAYLIST:
				forwardPlaylistJSON();
				break;

			default:
				break;
		}
	}

	private void skipVideo(YouTubeVideoItem currentVideo, int direction) {
		if (currentVideo.mIsPlaylist) { // skip to the next item in a playlist
			if (direction > 0) {
				mPlayer.next();
			} else if (direction < 0) {
				mPlayer.previous();
			}
		} else {
			if (direction > 0 && mPlaylist.size() > mPlaylistPosition + 1) {
				mPlaylistPosition += 1;
				playVideo(mPlaylistPosition);
			} else if (direction < 0 && mPlaylistPosition > 0) {
				mPlaylistPosition -= 1;
				playVideo(mPlaylistPosition);
			}
		}
	}

	private void getAndForwardCompactedJSON(final int command, String query) {
		final String requestCommand;
		final int resultCommand;
		switch (command) {
			case MessageType.COMMAND_GET_COMMENTS:
				requestCommand = "get_comments";
				resultCommand = MessageType.JSON_COMMENTS;
				break;
			case MessageType.COMMAND_GET_PLAYLIST:
				requestCommand = "get_playlist";
				resultCommand = MessageType.JSON_PLAYLIST;
				break;
			case MessageType.COMMAND_GET_RELATED:
				requestCommand = "get_related";
				resultCommand = MessageType.JSON_RELATED;
				break;
			case MessageType.COMMAND_GET_SEARCH:
				requestCommand = "get_search";
				resultCommand = MessageType.JSON_SEARCH;
				break;
			default:
				requestCommand = "empty_command";
				resultCommand = BroadcastMessage.TYPE_DEFAULT; // nothing to do here
		}

		AndroidNetworking.get(JSON_INTERFACE_URL).addQueryParameter("q", query).addQueryParameter(requestCommand, "1").build()
				.getAsString(new StringRequestListener() {

			@Override
			public void onResponse(String response) {
				// TODO: note, we get the JSON result, but broadcast to other devices for them to process (hence get as string)
				BroadcastMessage jsonResult = new BroadcastMessage(resultCommand, response);
				sendMessage(jsonResult);
			}

			@Override
			public void onError(ANError anError) {
				// TODO: handle this
			}
		});
	}

	private void forwardPlaylistJSON() {
		// convert our playlist to JSON and forward
		JSONArray playlist = new JSONArray();
		for (YouTubeVideoItem item : mPlaylist) {
			JSONObject jsonItem = item.toJSONObject();
			if (jsonItem != null) {
				playlist.put(jsonItem);
			} else {
				// TODO: handle errors
			}
		}
		BroadcastMessage jsonResult = new BroadcastMessage(MessageType.JSON_PLAYLIST, playlist.toString());
		sendMessage(jsonResult);
	}

	private void postDurationStateUpdate() {
		if (mPlayer != null) {
			BroadcastMessage playbackDuration = new BroadcastMessage(MessageType.INFO_DURATION, null);
			playbackDuration.setIntExtra(mPlayer.getDurationMillis());
			sendMessage(playbackDuration);
		}
	}

	private void postPlaybackStateUpdate() {
		if (mPlayer != null) {
			BroadcastMessage playbackState = new BroadcastMessage(mPlayer.isPlaying() ? MessageType.COMMAND_PLAY : MessageType
					.COMMAND_PAUSE, null);
			playbackState.setIntExtra(mPlayer.getCurrentTimeMillis());
			sendMessage(playbackState);
		}
	}

	private Runnable mStateUpdateRunnable = new Runnable() {
		@Override
		public void run() {
			postPlaybackStateUpdate();
			mStateUpdateHandler.postDelayed(mStateUpdateRunnable, STATE_UPDATE_INTERVAL);
		}
	};

	private final class YouTubePlayerStateChangeListener implements PlayerStateChangeListener {
		@Override
		public void onLoading() {
		}

		@Override
		public void onLoaded(String videoId) {
			// mPlayer.play(); // TODO: will this always work (and be appropriate?)
			postDurationStateUpdate();
		}

		@Override
		public void onAdStarted() {
			// TODO: need to deal with adverts somehow (they can't be controlled by other devices)
		}

		@Override
		public void onVideoStarted() {
			if (mPlaylist.size() > 0) { // update comments and related videos if applicable
				YouTubeVideoItem currentVideo = mPlaylist.get(mPlaylistPosition);
				if (currentVideo != null) {
					if (mHasRequestedComments) {
						getAndForwardCompactedJSON(MessageType.COMMAND_GET_COMMENTS, currentVideo.mId);
					}
					if (mHasRequestedRelatedVideos) {
						getAndForwardCompactedJSON(MessageType.COMMAND_GET_RELATED, currentVideo.mId);
					}
				}
			}
		}

		@Override
		public void onVideoEnded() {
			if (mPlaylist.size() > 0) { // automatically go to the next video if there is one available
				skipVideo(mPlaylist.get(mPlaylistPosition), 1);
			}
		}

		@Override
		public void onError(ErrorReason reason) {
			if (reason == ErrorReason.UNEXPECTED_SERVICE_DISCONNECTION) {
				mPlayer = null; // when this error occurs the player is released and can no longer be used
			}
		}
	}

	private final class YouTubePlaybackEventListener implements PlaybackEventListener {
		@Override
		public void onPlaying() {
			postPlaybackStateUpdate();
			mStateUpdateHandler.postDelayed(mStateUpdateRunnable, STATE_UPDATE_INTERVAL);
		}

		@Override
		public void onBuffering(boolean isBuffering) {
		}

		@Override
		public void onStopped() {
			postPlaybackStateUpdate();
			mStateUpdateHandler.removeCallbacks(mStateUpdateRunnable);
		}

		@Override
		public void onPaused() {
			postPlaybackStateUpdate();
			mStateUpdateHandler.removeCallbacks(mStateUpdateRunnable);
		}

		@Override
		public void onSeekTo(int endPositionMillis) {
			postPlaybackStateUpdate();
		}
	}

	private final class YouTubePlaylistEventListener implements PlaylistEventListener {
		@Override
		public void onNext() {
		}

		@Override
		public void onPrevious() {
		}

		@Override
		public void onPlaylistEnded() {
			if (mPlaylist.size() > 0) { // blank comments and related videos if applicable while we load the new content
				YouTubeVideoItem currentVideo = mPlaylist.get(mPlaylistPosition);
				if (currentVideo != null) {
					if (mHasRequestedComments) {
						BroadcastMessage jsonResult = new BroadcastMessage(MessageType.JSON_COMMENTS, LOADING_JSON);
						sendMessage(jsonResult);
					}
					if (mHasRequestedRelatedVideos) {
						BroadcastMessage jsonResult = new BroadcastMessage(MessageType.JSON_RELATED, LOADING_JSON);
						sendMessage(jsonResult);
					}
				}
			}
		}
	}
}

