package ac.robinson.bettertogether.youtube;

import org.json.JSONException;
import org.json.JSONObject;

public class YouTubeVideoItem {
	public String mId;
	public boolean mIsPlaylist;
	public String mTitle;
	public String mChannel;
	public String mBase64Thumbnail;

	public YouTubeVideoItem(String id, boolean isPlaylist, String title, String channel, String base64Thumbnail) {
		mId = id;
		mIsPlaylist = isPlaylist;
		mTitle = title;
		mChannel = channel;
		mBase64Thumbnail = base64Thumbnail;
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
}
