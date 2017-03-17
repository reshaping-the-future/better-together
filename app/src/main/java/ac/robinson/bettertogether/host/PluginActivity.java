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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

public class PluginActivity {

	private String mActivityName;
	private String mPackageName;

	private ResolveInfo mActivityInfo;

	private String mLabel = null;
	private Drawable mIcon = null;

	PluginActivity(String activityName, String packageName) {
		mActivityName = activityName;
		mPackageName = packageName;
	}

	public String getActivityName() {
		return mActivityName;
	}

	public String getPackageName() {
		return mPackageName;
	}

	private ResolveInfo getActivityInfo(Context context) {
		if (mActivityInfo != null) {
			return mActivityInfo;
		}

		PackageManager packageManager = context.getPackageManager();
		Intent intent = new Intent();
		intent.setComponent(new ComponentName(getPackageName(), getActivityName()));
		mActivityInfo = packageManager.resolveActivity(intent, 0);
		return mActivityInfo;
	}

	public String getLabel(Context context) {
		if (mLabel != null) {
			return mLabel;
		}

		mLabel = getActivityInfo(context).loadLabel(context.getPackageManager()).toString();
		return mLabel;
	}

	public Drawable getIcon(Context context) {
		if (mIcon != null) {
			return mIcon;
		}

		mIcon = getActivityInfo(context).loadIcon(context.getPackageManager());
		return mIcon;
	}
}
