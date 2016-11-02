package ac.robinson.bettertogether.youtube;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivity;
import ac.robinson.bettertogether.mode.YouTubeDataActivity;

public class YouTubeVideoArrayAdapter extends ArrayAdapter<YouTubeVideoItem> {

	private final ArrayList<YouTubeVideoItem> mVideoList;
	private boolean mPlaylistMode;
	private VideoClickListener mClickListener = null;

	public interface VideoClickListener {
		void onVideoClick(YouTubeVideoItem videoItem, boolean playNow);
	}

	public YouTubeVideoArrayAdapter(Context context, ArrayList<YouTubeVideoItem> itemsArrayList, boolean playlistMode,
	                                VideoClickListener listener) {
		super(context, R.layout.youtube_video_list_item, itemsArrayList);
		mVideoList = itemsArrayList;
		mPlaylistMode = playlistMode;
		mClickListener = listener;
	}

	public static YouTubeVideoArrayAdapter getInstance(final BaseHotspotActivity activity, String rawJSON, boolean
			playlistMode) {
		JSONArray jsonArray;
		try {
			jsonArray = new JSONArray(rawJSON);
		} catch (JSONException e) {
			return null; // TODO: handle error
		}

		ArrayList<YouTubeVideoItem> videoResults = new ArrayList<>();
		for (int i = 0; i < jsonArray.length(); i++) {
			try {
				YouTubeVideoItem newItem = YouTubeVideoItem.fromJSONObject(jsonArray.getJSONObject(i));
				if (newItem != null) {
					videoResults.add(newItem);
				} else {
					// TODO: handle errors
				}
			} catch (JSONException ignored) {
			}
		}

		return new YouTubeVideoArrayAdapter(activity, videoResults, playlistMode, new YouTubeVideoArrayAdapter
				.VideoClickListener() {
			@Override
			public void onVideoClick(YouTubeVideoItem videoItem,boolean playNow) {
				BroadcastMessage addMessage = new BroadcastMessage(BroadcastMessage.Type.YOUTUBE, videoItem.toJSONObject()
						.toString());
				addMessage.setCommand(playNow ? YouTubeDataActivity.COMMAND_SELECT : YouTubeDataActivity.COMMAND_ADD);
				activity.sendBroadcastMessage(addMessage);
			}
		});
	}

	private View.OnClickListener mButtonClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			final int viewId = view.getId();
			switch (viewId) {
				case R.id.video_play:
				case R.id.video_queue:
					YouTubeVideoHolder currentVideo = (YouTubeVideoHolder) ((RelativeLayout) view.getParent()).getTag();
					mClickListener.onVideoClick(currentVideo.mVideoItem, viewId == R.id.video_play);
					break;
			}
		}
	};

	@NonNull
	@Override
	public View getView(int position, View convertView, @NonNull ViewGroup parent) {
		View currentView;
		YouTubeVideoHolder videoHolder;

		if (convertView == null) {
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			currentView = inflater.inflate(R.layout.youtube_video_list_item, parent, false);
			videoHolder = new YouTubeVideoHolder();
			videoHolder.mTitle = (TextView) currentView.findViewById(R.id.video_title);
			videoHolder.mChannel = (TextView) currentView.findViewById(R.id.video_channel);
			videoHolder.mThumbnail = (ImageView) currentView.findViewById(R.id.video_thumbnail);
			videoHolder.mPlayButton = (ImageButton) currentView.findViewById(R.id.video_play);
			videoHolder.mPlayButton.setOnClickListener(mButtonClickListener);
			videoHolder.mQueueButton = (ImageButton) currentView.findViewById(R.id.video_queue);
			videoHolder.mQueueButton.setOnClickListener(mButtonClickListener);
			currentView.setTag(videoHolder);

			if (mPlaylistMode) {
				videoHolder.mQueueButton.setVisibility(View.GONE);
			}
		} else {
			currentView = convertView;
			videoHolder = (YouTubeVideoHolder) convertView.getTag();
		}

		YouTubeVideoItem currentVideo = mVideoList.get(position);
		videoHolder.mVideoItem = currentVideo;
		videoHolder.mTitle.setText(currentVideo.mTitle);
		videoHolder.mChannel.setText(currentVideo.mChannel);

		byte[] decodedString = Base64.decode(currentVideo.mBase64Thumbnail, Base64.DEFAULT);
		Bitmap thumbnailBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
		videoHolder.mThumbnail.setImageBitmap(thumbnailBitmap);
		return currentView;
	}

	private class YouTubeVideoHolder {
		public YouTubeVideoItem mVideoItem;
		public TextView mTitle;
		public TextView mChannel;
		public ImageView mThumbnail;
		public ImageButton mPlayButton;
		public ImageButton mQueueButton;
	}
}

