package ac.robinson.bettertogether.mode;

import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;
import android.widget.TextView;

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
import java.util.Locale;

import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivityYouTube;
import ac.robinson.bettertogether.youtube.DeveloperKey;
import ac.robinson.bettertogether.youtube.YouTubeVideoItem;

public class YouTubeDataActivity extends BaseHotspotActivityYouTube {

	private static ArrayList<YouTubeVideoItem> sVideoPlaylist = new ArrayList<>(); // TODO: don't use static for saving state
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

	public static final String INFO_STATE = "state";
	public static final String COMMAND_PLAY = "play";
	public static final String COMMAND_PAUSE = "pause";
	public static final String COMMAND_SKIP = "skip"; // first extra: -1 for previous; +1 for next
	public static final String COMMAND_SEEK = "seek";
	public static final String COMMAND_ADD = "add"; // first extra: +1 to insert at start and play now
	public static final String COMMAND_SELECT = "select";
	public static final String GET_SEARCH_RESULTS = "get_search";
	public static final String GET_RELATED_VIDEOS = "get_related";
	public static final String GET_VIDEO_COMMENTS = "get_comments";
	public static final String GET_PLAYLIST = "get_playlist";

	public static final String LOADING_JSON = "[{}]";

	private TextView mCurrentStateDebug;
	private TextView mEventLogDebug;
	private StringBuilder mLogStringDebug;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.mode_youtube_video_player);
		mPlayerView = (YouTubePlayerView) findViewById(R.id.youtube_view);

		mPlayerStateChangeListener = new YouTubePlayerStateChangeListener();
		mPlaybackEventListener = new YouTubePlaybackEventListener();
		mPlaylistEventListener = new YouTubePlaylistEventListener();

		if (savedInstanceState != null) {
			mPlaylistPosition = savedInstanceState.getInt("mPlaylistPosition", -1);
			mHasRequestedRelatedVideos = savedInstanceState.getBoolean("mHasRequestedRelatedVideos", false);
			mHasRequestedComments = savedInstanceState.getBoolean("mHasRequestedComments", false);
		}
		if (mPlaylistPosition < 0 && sVideoPlaylist.size() > 0) {
			mPlaylistPosition = 0; // just in case
		}

		mPlayerView.initialize(DeveloperKey.DEVELOPER_KEY, this);

		mCurrentStateDebug = (TextView) findViewById(R.id.state_text);
		mEventLogDebug = (TextView) findViewById(R.id.event_log_text);
		mLogStringDebug = new StringBuilder();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt("mPlaylistPosition", mPlaylistPosition);
		outState.putBoolean("mHasRequestedRelatedVideos", mHasRequestedRelatedVideos);
		outState.putBoolean("mHasRequestedComments", mHasRequestedComments);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mStateUpdateHandler.removeCallbacks(mStateUpdateRunnable);
	}

	@Override
	public void finish() {
		super.finish();
		// close related remote clients
		BroadcastMessage finishMessage = new BroadcastMessage(BroadcastMessage.Type.YOUTUBE, null);
		finishMessage.setCommand(INFO_STATE);
		sendBroadcastMessage(finishMessage);
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
		// mPlayer.setShowFullscreenButton(true);
	}

	@Override
	protected YouTubePlayer.Provider getYouTubePlayerProvider() {
		return mPlayerView;
	}

	private void playVideo(int playlistPosition) {
		YouTubeVideoItem selectedEntry = sVideoPlaylist.get(playlistPosition);
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

	@Override
	public void onBroadcastMessageReceived(BroadcastMessage message) {
		YouTubeVideoItem currentVideo = null;
		if (sVideoPlaylist.size() > 0) {
			currentVideo = sVideoPlaylist.get(mPlaylistPosition);
		}

		if (message.mType == BroadcastMessage.Type.YOUTUBE) {
			// Log.d("message: ", "" + message.mMessage + "," + message.getCommand() + ", cv: " + currentVideo + "," + mPlayer);
			if (message.hasCommand()) {
				final String command = message.getCommand();
				switch (command) {
					case COMMAND_PLAY:
					case COMMAND_PAUSE:
					case COMMAND_SKIP:
					case COMMAND_SEEK:
						if (currentVideo != null && mPlayer != null) {
							switch (command) {
								case COMMAND_PLAY:
									mPlayer.play();
									break;

								case COMMAND_PAUSE:
									mPlayer.pause();
									break;

								case COMMAND_SKIP:
									skipVideo(currentVideo, message.getFirstExtra());
									break;

								case COMMAND_SEEK:
									mPlayer.seekToMillis(message.getFirstExtra());
									break;
							}
						}
						break;

					case COMMAND_ADD:
						try {
							JSONObject videoJSONObject = new JSONObject(message.mMessage);
							YouTubeVideoItem videoItem = YouTubeVideoItem.fromJSONObject(videoJSONObject);
							if (videoItem != null) {
								sVideoPlaylist.add(videoItem);
								if (sVideoPlaylist.size() == 1) {  // auto play if this is the only item in the list
									mPlaylistPosition = 0;
									playVideo(mPlaylistPosition);
								}
								forwardPlaylistJSON(); // update the playlist on remote clients
							}
						} catch (JSONException ignored) {
							// TODO: handle errors
						}
						break;

					case COMMAND_SELECT:
						try {
							JSONObject videoJSONObject = new JSONObject(message.mMessage);
							YouTubeVideoItem videoItem = YouTubeVideoItem.fromJSONObject(videoJSONObject);
							if (videoItem != null) {
								int i = 0;
								boolean videoFound = false;
								for (YouTubeVideoItem item : sVideoPlaylist) {
									if (item.mId.equals(videoItem.mId)) {
										videoFound = true;
										mPlaylistPosition = i;
										playVideo(mPlaylistPosition);
										break;
									}
									i += 1;
								}

								if (!videoFound) { // add and play if not found
									sVideoPlaylist.add(videoItem);
									mPlaylistPosition = sVideoPlaylist.size() - 1;
									playVideo(mPlaylistPosition);
									forwardPlaylistJSON(); // update the playlist on remote clients
								}
							}
						} catch (JSONException ignored) {
							// TODO: handle errors
						}
						break;

					case GET_SEARCH_RESULTS:
						try {
							getAndForwardCompactedJSON(command, URLEncoder.encode(message.mMessage, Charset.defaultCharset()
									.name()));
						} catch (UnsupportedEncodingException e) {
							// TODO: handle this
						}
						break;

					case GET_RELATED_VIDEOS:
						mHasRequestedRelatedVideos = true;
						if (currentVideo != null) {
							getAndForwardCompactedJSON(command, currentVideo.mId);
						}

					case GET_VIDEO_COMMENTS:
						mHasRequestedComments = true;
						if (currentVideo != null) {
							getAndForwardCompactedJSON(command, currentVideo.mId);
						}
						break;

					case GET_PLAYLIST:
						forwardPlaylistJSON();
						break;
				}
			}
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
			if (direction > 0 && sVideoPlaylist.size() > mPlaylistPosition + 1) {
				mPlaylistPosition += 1;
				playVideo(mPlaylistPosition);
			} else if (direction < 0 && mPlaylistPosition > 0) {
				mPlaylistPosition -= 1;
				playVideo(mPlaylistPosition);
			}
		}
	}

	public void getAndForwardCompactedJSON(final String command, String query) {
		AndroidNetworking.get("http://cs.swan.ac.uk/~cssimonr/projects/better-together/youtube.php").addQueryParameter("q",
				query).addQueryParameter(command, "1").build().getAsString(new StringRequestListener() {

			@Override
			public void onResponse(String response) {
				// TODO: note, we get the JSON result, but broadcast to other devices for them to process (hence get as string)
				BroadcastMessage jsonResult = new BroadcastMessage(BroadcastMessage.Type.JSON, response);
				jsonResult.setCommand(command);
				sendBroadcastMessage(jsonResult);
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
		for (YouTubeVideoItem item : sVideoPlaylist) {
			JSONObject jsonItem = item.toJSONObject();
			if (jsonItem != null) {
				playlist.put(jsonItem);
			} else {
				// TODO: handle errors
			}
		}
		BroadcastMessage jsonResult = new BroadcastMessage(BroadcastMessage.Type.JSON, playlist.toString());
		jsonResult.setCommand(GET_PLAYLIST);
		sendBroadcastMessage(jsonResult);
	}

	private void postStateUpdate() {
		if (mPlayer != null) {
			BroadcastMessage playbackState = new BroadcastMessage(BroadcastMessage.Type.YOUTUBE, mPlayer.isPlaying() ?
					COMMAND_PLAY : COMMAND_PAUSE);
			playbackState.setCommand(INFO_STATE);
			playbackState.setExtras(new int[]{mPlayer.getCurrentTimeMillis(), mPlayer.getDurationMillis()});
			sendBroadcastMessage(playbackState);
		}
	}

	private Runnable mStateUpdateRunnable = new Runnable() {
		@Override
		public void run() {
			postStateUpdate();
			mStateUpdateHandler.postDelayed(mStateUpdateRunnable, STATE_UPDATE_INTERVAL);
		}
	};

	@Override
	public void onServiceMessageReceived(int type, String data) {
		// nothing to do
	}

	private final class YouTubePlayerStateChangeListener implements PlayerStateChangeListener {
		String playerState = "UNINITIALIZED";

		@Override
		public void onLoading() {
			playerState = "LOADING";
			logStateDebug(playerState);
		}

		@Override
		public void onLoaded(String videoId) {
			// mPlayer.play(); // TODO: will this always work (and be appropriate?)

			playerState = String.format("LOADED %s", videoId);
			logStateDebug(playerState);
		}

		@Override
		public void onAdStarted() {
			playerState = "AD_STARTED";
			logStateDebug(playerState);
		}

		@Override
		public void onVideoStarted() {
			if (sVideoPlaylist.size() > 0) { // update comments and related videos if applicable
				YouTubeVideoItem currentVideo = sVideoPlaylist.get(mPlaylistPosition);
				if (currentVideo != null) {
					if (mHasRequestedComments) {
						getAndForwardCompactedJSON(GET_VIDEO_COMMENTS, currentVideo.mId);
					}
					if (mHasRequestedRelatedVideos) {
						getAndForwardCompactedJSON(GET_RELATED_VIDEOS, currentVideo.mId);
					}
				}
			}

			playerState = "VIDEO_STARTED";
			logStateDebug(playerState);
		}

		@Override
		public void onVideoEnded() {
			if (sVideoPlaylist.size() > 0) { // automatically go to the next video if there is one available
				skipVideo(sVideoPlaylist.get(mPlaylistPosition), 1);
			}

			playerState = "VIDEO_ENDED";
			logStateDebug(playerState);
		}

		@Override
		public void onError(ErrorReason reason) {
			if (reason == ErrorReason.UNEXPECTED_SERVICE_DISCONNECTION) {
				mPlayer = null; // when this error occurs the player is released and can no longer be used
			}

			playerState = "ERROR (" + reason + ")";
			logStateDebug(playerState);
		}
	}

	private final class YouTubePlaybackEventListener implements PlaybackEventListener {
		String playbackState = "NOT_PLAYING";
		String bufferingState = "";

		private String formatTime(int millis) {
			int seconds = millis / 1000;
			int minutes = seconds / 60;
			int hours = minutes / 60;

			return (hours == 0 ? "" : hours + ":") + String.format(Locale.US, "%02d:%02d", minutes % 60, seconds % 60);
		}

		private String getTimesText() {
			int currentTimeMillis = mPlayer.getCurrentTimeMillis();
			int durationMillis = mPlayer.getDurationMillis();
			return String.format("(%s/%s)", formatTime(currentTimeMillis), formatTime(durationMillis));
		}

		@Override
		public void onPlaying() {
			postStateUpdate();
			mStateUpdateHandler.postDelayed(mStateUpdateRunnable, STATE_UPDATE_INTERVAL);

			playbackState = "PLAYING";
			logStateDebug("\tPLAYING " + getTimesText());
		}

		@Override
		public void onBuffering(boolean isBuffering) {
			bufferingState = isBuffering ? "(BUFFERING)" : "";
			logStateDebug("\t\t" + (isBuffering ? "BUFFERING " : "NOT BUFFERING ") + getTimesText());
		}

		@Override
		public void onStopped() {
			postStateUpdate();
			mStateUpdateHandler.removeCallbacks(mStateUpdateRunnable);

			playbackState = "STOPPED";
			logStateDebug("\tSTOPPED");
		}

		@Override
		public void onPaused() {
			postStateUpdate();
			mStateUpdateHandler.removeCallbacks(mStateUpdateRunnable);

			playbackState = "PAUSED";
			logStateDebug("\tPAUSED " + getTimesText());
		}

		@Override
		public void onSeekTo(int endPositionMillis) {
			postStateUpdate();

			logStateDebug(String.format("\tSEEKTO: (%s/%s)", formatTime(endPositionMillis), formatTime(mPlayer.getDurationMillis
					())));
		}
	}

	private final class YouTubePlaylistEventListener implements PlaylistEventListener {
		@Override
		public void onNext() {
			logStateDebug("NEXT VIDEO");
		}

		@Override
		public void onPrevious() {
			logStateDebug("PREVIOUS VIDEO");
		}

		@Override
		public void onPlaylistEnded() {
			if (sVideoPlaylist.size() > 0) { // blank comments and related videos if applicable while we load the new content
				YouTubeVideoItem currentVideo = sVideoPlaylist.get(mPlaylistPosition);
				if (currentVideo != null) {
					if (mHasRequestedComments) {
						BroadcastMessage jsonResult = new BroadcastMessage(BroadcastMessage.Type.JSON, LOADING_JSON);
						jsonResult.setCommand(GET_VIDEO_COMMENTS);
						sendBroadcastMessage(jsonResult);
					}
					if (mHasRequestedRelatedVideos) {
						BroadcastMessage jsonResult = new BroadcastMessage(BroadcastMessage.Type.JSON, LOADING_JSON);
						jsonResult.setCommand(GET_RELATED_VIDEOS);
						sendBroadcastMessage(jsonResult);
					}
				}
			}

			logStateDebug("PLAYLIST ENDED");
		}
	}

	private void logStateDebug(String message) {
		mCurrentStateDebug.setText(String.format("Current state: %s %s %s", mPlayerStateChangeListener.playerState,
				mPlaybackEventListener.playbackState, mPlaybackEventListener.bufferingState));
		mLogStringDebug.append(message);
		mLogStringDebug.append("\n");
		mEventLogDebug.setText(mLogStringDebug);
	}
}

