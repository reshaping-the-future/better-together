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

package ac.robinson.bettertogether.plugin.base.video.youtube;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import ac.robinson.bettertogether.api.BasePluginActivity;
import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.plugin.base.video.R;
import androidx.annotation.NonNull;

public class YouTubeVideoArrayAdapter extends ArrayAdapter<ac.robinson.bettertogether.plugin.base.video.youtube.YouTubeVideoItem> {

	private final ArrayList<YouTubeVideoItem> mVideoList;
	private boolean mPlaylistMode;
	private VideoClickListener mClickListener;

	interface VideoClickListener {
		void onVideoClick(YouTubeVideoItem videoItem, boolean playNow);
	}

	private YouTubeVideoArrayAdapter(Context context, ArrayList<YouTubeVideoItem> itemsArrayList, boolean playlistMode,
									 VideoClickListener listener) {
		super(context, R.layout.youtube_video_list_item, itemsArrayList);
		mVideoList = itemsArrayList;
		mPlaylistMode = playlistMode;
		mClickListener = listener;
	}

	public static YouTubeVideoArrayAdapter getInstance(final BasePluginActivity activity, String rawJSON, boolean playlistMode) {
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

		return new YouTubeVideoArrayAdapter(activity, videoResults, playlistMode,
				new YouTubeVideoArrayAdapter.VideoClickListener() {
			@Override
			public void onVideoClick(YouTubeVideoItem videoItem, boolean playNow) {
				BroadcastMessage addMessage = new BroadcastMessage(playNow ? MessageType.COMMAND_SELECT :
						MessageType.COMMAND_ADD, videoItem
						.toJSONObject()
						.toString());
				activity.sendMessage(addMessage);
			}
		});
	}

	private View.OnClickListener mButtonClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			// can't use resource id switch statements in library modules
			final int viewId = view.getId();
			if (view.getId() == R.id.video_play || viewId == R.id.video_queue) {
				YouTubeVideoHolder currentVideo = (YouTubeVideoHolder) ((RelativeLayout) view.getParent()).getTag();
				mClickListener.onVideoClick(currentVideo.mVideoItem, viewId == R.id.video_play);
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
			videoHolder.mTitle = currentView.findViewById(R.id.video_title);
			videoHolder.mChannel = currentView.findViewById(R.id.video_channel);
			videoHolder.mThumbnail = currentView.findViewById(R.id.video_thumbnail);
			videoHolder.mPlayButton = currentView.findViewById(R.id.video_play);
			videoHolder.mPlayButton.setOnClickListener(mButtonClickListener);
			videoHolder.mQueueButton = currentView.findViewById(R.id.video_queue);
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

	private static class YouTubeVideoHolder {
		YouTubeVideoItem mVideoItem;
		TextView mTitle;
		TextView mChannel;
		ImageView mThumbnail;
		ImageButton mPlayButton;
		ImageButton mQueueButton;
	}
}

