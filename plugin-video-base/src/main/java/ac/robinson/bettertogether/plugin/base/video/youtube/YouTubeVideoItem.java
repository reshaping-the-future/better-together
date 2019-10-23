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

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

public class YouTubeVideoItem implements Parcelable {
	public String mId;
	public boolean mIsPlaylist;
	String mTitle;
	String mChannel;
	String mBase64Thumbnail;

	private YouTubeVideoItem(String id, boolean isPlaylist, String title, String channel, String base64Thumbnail) {
		mId = id;
		mIsPlaylist = isPlaylist;
		mTitle = title;
		mChannel = channel;
		mBase64Thumbnail = base64Thumbnail;
	}

	private YouTubeVideoItem(Parcel in) {
		mId = in.readString();
		mIsPlaylist = in.readByte() != 0;
		mTitle = in.readString();
		mChannel = in.readString();
		mBase64Thumbnail = in.readString();
	}

	public JSONObject toJSONObject() {
		try {
			JSONObject jsonItem = new JSONObject();
			jsonItem.put("id", mId);
			jsonItem.put("type", mIsPlaylist ? "playlist" : "video");
			jsonItem.put("title", mTitle);
			jsonItem.put("channel", mChannel);
			jsonItem.put("thumbnail", mBase64Thumbnail);
			return jsonItem;
		} catch (JSONException ignored) {
		}
		return null;
	}

	public static YouTubeVideoItem fromJSONObject(JSONObject source) {
		try {
			String id = source.getString("id");
			boolean isPlaylist = "playlist".equals(source.getString("type"));
			String title = source.getString("title");
			String channel = source.getString("channel");
			String base64Thumbnail = source.getString("thumbnail");
			return new YouTubeVideoItem(id, isPlaylist, title, channel, base64Thumbnail);
		} catch (JSONException ignored) {
		}
		return null;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeString(mId);
		out.writeByte((byte) (mIsPlaylist ? 1 : 0));
		out.writeString(mTitle);
		out.writeString(mChannel);
		out.writeString(mBase64Thumbnail);
	}

	public static final Creator<YouTubeVideoItem> CREATOR = new Creator<YouTubeVideoItem>() {
		public YouTubeVideoItem createFromParcel(Parcel in) {
			return new YouTubeVideoItem(in);
		}

		public YouTubeVideoItem[] newArray(int size) {
			return new YouTubeVideoItem[size];
		}
	};
}
