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

package ac.robinson.bettertogether.host;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import ac.robinson.bettertogether.R;
import androidx.annotation.Nullable;

public class Plugin {

	private String mPluginLabel;
	private String mPackageName;
	private boolean mIsInbuiltPlugin = false;

	private int mTheme = 0;
	private int mInbuiltPluginIconResource = 0;
	private Drawable mIcon = null;

	private List<PluginActivity> mActivities;

	private boolean mRequiresWifi;
	private boolean mRequiresBluetooth;

	Plugin(String pluginLabel, String packageName) {
		mPluginLabel = pluginLabel;
		mPackageName = packageName;
		mActivities = new ArrayList<>();
	}

	void setIsInbuiltPlugin(boolean isInbuiltPlugin) {
		mIsInbuiltPlugin = isInbuiltPlugin;
	}

	void setInbuiltPluginLabel(String label) {
		mPluginLabel = label;
	}

	public boolean isInbuiltPlugin() {
		return mIsInbuiltPlugin;
	}

	public String getRawPluginLabel() {
		return mPluginLabel;
	}

	public String getFilteredPluginLabel(Context context) {
		if (TextUtils.isEmpty(mPluginLabel)) {
			return mPluginLabel;
		}
		String pluginLabelPrefix = context.getString(R.string.bt_plugin_prefix);
		if (mPluginLabel.startsWith(pluginLabelPrefix)) {
			return mPluginLabel.replaceFirst(pluginLabelPrefix, "").trim();
		}
		return mPluginLabel;
	}

	public String getPackageName() {
		return mPackageName;
	}

	boolean setTheme(@Nullable String themeName) {
		if (TextUtils.isEmpty(themeName)) {
			return false;
		}
		switch (themeName) {
			case "red":
				mTheme = R.style.BetterTogether_PluginStyle_red;
				return true;
			case "pink":
				mTheme = R.style.BetterTogether_PluginStyle_pink;
				return true;
			case "purple":
				mTheme = R.style.BetterTogether_PluginStyle_purple;
				return true;
			case "deep_purple":
				mTheme = R.style.BetterTogether_PluginStyle_deep_purple;
				return true;
			case "indigo":
				mTheme = R.style.BetterTogether_PluginStyle_indigo;
				return true;
			case "blue":
				mTheme = R.style.BetterTogether_PluginStyle_blue;
				return true;
			case "light_blue":
				mTheme = R.style.BetterTogether_PluginStyle_light_blue;
				return true;
			case "cyan":
				mTheme = R.style.BetterTogether_PluginStyle_cyan;
				return true;
			case "teal":
				mTheme = R.style.BetterTogether_PluginStyle_teal;
				return true;
			case "green":
				mTheme = R.style.BetterTogether_PluginStyle_green;
				return true;
			case "light_green":
				mTheme = R.style.BetterTogether_PluginStyle_light_green;
				return true;
			case "lime":
				mTheme = R.style.BetterTogether_PluginStyle_lime;
				return true;
			case "yellow":
				mTheme = R.style.BetterTogether_PluginStyle_yellow;
				return true;
			case "amber":
				mTheme = R.style.BetterTogether_PluginStyle_amber;
				return true;
			case "orange":
				mTheme = R.style.BetterTogether_PluginStyle_orange;
				return true;
			case "deep_orange":
				mTheme = R.style.BetterTogether_PluginStyle_deep_orange;
				return true;
			case "brown":
				mTheme = R.style.BetterTogether_PluginStyle_brown;
				return true;
			case "grey":
				mTheme = R.style.BetterTogether_PluginStyle_grey;
				return true;
			case "blue_grey":
				mTheme = R.style.BetterTogether_PluginStyle_blue_grey;
				return true;
			default:
				return false;
		}
	}

	public boolean hasTheme() {
		return mTheme != 0;
	}

	public int getTheme() {
		return mTheme;
	}

	void setInbuiltPluginIcon(int iconResource) {
		mInbuiltPluginIconResource = iconResource;
	}

	public Drawable getIcon(Context context) {
		if (mIcon != null) {
			return mIcon;
		}

		if (mInbuiltPluginIconResource != 0) {
			mIcon = context.getResources().getDrawable(mInbuiltPluginIconResource);
			return mIcon;
		}

		try {
			mIcon = context.getPackageManager().getApplicationIcon(getPackageName());
		} catch (PackageManager.NameNotFoundException ignored) {
			return null; // application doesn't exist
		}

		return mIcon;
	}

	void addActivity(PluginActivity activity) {
		mActivities.add(activity);
	}

	public List<PluginActivity> getActivities() {
		return mActivities;
	}

	void setRequiresWifi(boolean requiresWifi) {
		mRequiresWifi = requiresWifi;
	}

	public boolean getRequiresWifi() {
		return mRequiresWifi;
	}

	void setRequiresBluetooth(boolean requiresBluetooth) {
		mRequiresBluetooth = requiresBluetooth;
	}

	public boolean getRequiresBluetooth() {
		return mRequiresBluetooth;
	}
}
