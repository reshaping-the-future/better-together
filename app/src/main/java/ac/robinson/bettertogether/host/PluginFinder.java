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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ac.robinson.bettertogether.api.messaging.PluginIntent;

public class PluginFinder {

	// a few contortions are necessary to allow plugins to be included in the same apk as the host application - these aspects
	// let us detect plugins that have the same package name as each other, separating them out into different sets of activities
	private static final String INTERNAL_EXTRA_PLUGIN_PACKAGE = "ac.robinson.bettertogether.intent.extra.PLUGIN_PACKAGE";
	private static final String INTERNAL_EXTRA_PLUGIN_ICON = "ac.robinson.bettertogether.intent.extra.PLUGIN_ICON";
	private static final String INTERNAL_EXTRA_PLUGIN_LABEL = "ac.robinson.bettertogether.intent.extra.PLUGIN_LABEL";

	// @formatter:off
	public static final Set<String> INTERNAL_PLUGIN_PACKAGES =  Collections.unmodifiableSet(new HashSet<>(Arrays.asList(new String[]{
		"ac.robinson.bettertogether.plugin.base.video",
		"ac.robinson.bettertogether.plugin.base.shopping"
	})));
	private static final Set<String> OVERRIDE_PLUGIN_PACKAGES =  Collections.unmodifiableSet(new HashSet<>(Arrays.asList(new String[]{
		"ac.robinson.bettertogether.plugin.video",
		"ac.robinson.bettertogether.plugin.shopping"
	})));
	// @formatter:on

	private static final String TAG = "PluginFinder";

	public static Map<String, Plugin> getValidPlugins(@NonNull Context context, @Nullable String specificPackage) {
		Map<String, Plugin> validPlugins = new HashMap<>();
		PackageManager packageManager = context.getPackageManager();
		List<ResolveInfo> pluginActivities = findPluginActivities(packageManager, specificPackage);

		for (ResolveInfo activity : pluginActivities) {
			if (validateActivity(activity)) {

				String pluginPackage = activity.activityInfo.packageName;
				Bundle activityMetaData = activity.activityInfo.metaData;
				boolean isDefaultPlugin = PluginIntent.HOST_PACKAGE.equals(pluginPackage);

				// inbuilt plugins all have the same package (ours) - update to use package set in meta-data to separate them
				String inbuiltPluginPackage = null;
				if (isDefaultPlugin) {
					if (activityMetaData != null) {
						inbuiltPluginPackage = activityMetaData.getString(INTERNAL_EXTRA_PLUGIN_PACKAGE);
						if (TextUtils.isEmpty(inbuiltPluginPackage)) {
							inbuiltPluginPackage = pluginPackage;
							Log.e(TAG, "Default plugin " + activity.activityInfo.name + " error: invalid package name - " +
									"continuing with default");
						}
					} else {
						Log.e(TAG, "Default plugin " + activity.activityInfo.name + " error: missing package name - " +
								"continuing with default");
					}
				}

				// create or retrieve plugin object
				Plugin plugin;
				if (validPlugins.containsKey(isDefaultPlugin ? inbuiltPluginPackage : pluginPackage)) {
					plugin = validPlugins.get(isDefaultPlugin ? inbuiltPluginPackage : pluginPackage);

				} else {
					if (isDefaultPlugin) {
						// note: we update inbuilt plugin attributes later (setting label to correct value)
						plugin = new Plugin(inbuiltPluginPackage, inbuiltPluginPackage);
						plugin.setIsInbuiltPlugin(true);
						validPlugins.put(inbuiltPluginPackage, plugin);

					} else {
						if (OVERRIDE_PLUGIN_PACKAGES.contains(pluginPackage)) {
							// note: currently we do this to ensure that everyone using an inbuilt plugin uses the same version,
							// regardless of whether it is inbuilt or not - in future, we could let newer versions override
							// the inbuilt plugins to extend/improve their functionality
							Log.d(TAG, "Plugin " + pluginPackage + " duplicates inbuilt plugin - skipping");
							continue;
						}

						ApplicationInfo applicationInfo = validateApplication(packageManager, pluginPackage);
						if (applicationInfo != null) {

							CharSequence pluginLabel = packageManager.getApplicationLabel(applicationInfo);
							if (TextUtils.isEmpty(pluginLabel)) {
								Log.e(TAG, "Plugin " + pluginPackage + " error: application has no label");
								continue; // can't add plugins with no label
							}

							plugin = new Plugin(pluginLabel.toString(), pluginPackage);

							Bundle applicationMetaData = applicationInfo.metaData;
							if (applicationMetaData != null) {
								String pluginTheme = applicationMetaData.getString(PluginIntent.EXTRA_PLUGIN_THEME);
								if (!plugin.setTheme(pluginTheme)) {
									// we can add plugins without themes, or with invalid themes, but warn regardless
									Log.w(TAG, "Plugin " + pluginPackage + " warning: theme invalid or not set");
								}
							}

							validPlugins.put(pluginPackage, plugin);
						} else {
							continue; // can't add plugins with no label
						}
					}
				}

				// if we get here then the activity's package has been found and validated - add to its collection
				plugin.addActivity(new PluginActivity(activity.activityInfo.name, pluginPackage));

				if (activityMetaData != null) {
					// check whether the plugin requires wifi (in which case we connect phones using bluetooth) or bluetooth (we
					// use wifi) - behaviour is undefined if plugins require both
					plugin.setRequiresWifi(plugin.getRequiresWifi() || activityMetaData.getBoolean(PluginIntent
							.EXTRA_REQUIRES_WIFI));
					plugin.setRequiresBluetooth(plugin.getRequiresBluetooth() || activityMetaData.getBoolean(PluginIntent
							.EXTRA_REQUIRES_BLUETOOTH));

					// special case for default plugins - load theme/icon/label separately (can't get from main application - us)
					// (only one activity in each plugin needs to have these items)
					if (isDefaultPlugin) {
						String pluginTheme = activityMetaData.getString(PluginIntent.EXTRA_PLUGIN_THEME);
						if (!plugin.setTheme(pluginTheme)) {
							Log.d(TAG, "Default plugin " + activity.activityInfo.name + " error: invalid or missing theme - " +
									"continuing with default");
						}
						int inbuiltPluginIcon = activityMetaData.getInt(INTERNAL_EXTRA_PLUGIN_ICON);
						if (inbuiltPluginIcon != 0) {
							plugin.setInbuiltPluginIcon(inbuiltPluginIcon);
						} else {
							Log.d(TAG, "Default plugin " + activity.activityInfo.name + " error: invalid or missing icon - " +
									"continuing with default");
						}
						int inbuiltPluginLabel = activityMetaData.getInt(INTERNAL_EXTRA_PLUGIN_LABEL);
						if (inbuiltPluginLabel != 0) {
							plugin.setInbuiltPluginLabel(context.getString(inbuiltPluginLabel));
						} else {
							Log.d(TAG, "Default plugin " + activity.activityInfo.name + " error: invalid or missing label - " +
									"continuing with default");
						}
					}
				}
			}
		}
		return validPlugins;
	}

	private static List<ResolveInfo> findPluginActivities(@NonNull PackageManager packageManager, @Nullable String
			specificPackage) {
		final android.content.Intent activityIntent = new android.content.Intent(PluginIntent.ACTION_LAUNCH_PLUGIN);
		if (!TextUtils.isEmpty(specificPackage)) {
			activityIntent.setPackage(specificPackage);
		}

		List<ResolveInfo> activities = packageManager.queryIntentActivities(activityIntent, PackageManager.GET_META_DATA);
		if (activities == null) {
			activities = new ArrayList<>(0); // sometimes queryIntentActivities returns null (the documentation lies)
		}

		Collections.reverse(activities); // so that activities show up in the order they are listed in the manifest

		return activities;
	}

	private static ApplicationInfo validateApplication(@NonNull PackageManager packageManager, @NonNull String pluginPackage) {
		ApplicationInfo applicationInfo = null;
		try {
			applicationInfo = packageManager.getApplicationInfo(pluginPackage, PackageManager.GET_META_DATA);
		} catch (PackageManager.NameNotFoundException ignored) { // applicationInfo will be null
		}
		if (applicationInfo == null) {
			// perhaps it was uninstalled as we searched? (we track add/remove events, so this is ok)
			Log.e(TAG, "Plugin " + pluginPackage + " error: application not found");
			return null;
		}
		return applicationInfo;
	}

	private static boolean validateActivity(ResolveInfo plugin) {
		boolean isValid = true;
		if (!checkEnabled(plugin)) {
			Log.e(TAG, "Plugin " + plugin.activityInfo.name + " error: activity not enabled");
			isValid = false;
		}
		if (!checkExported(plugin)) {
			Log.e(TAG, "Plugin " + plugin.activityInfo.name + " error: activity not exported");
			isValid = false;
		}
		if (!checkLabel(plugin)) {
			Log.e(TAG, "Plugin " + plugin.activityInfo.name + " error: activity has no label");
			isValid = false;
		}
		if (!checkIcon(plugin)) {
			Log.e(TAG, "Plugin " + plugin.activityInfo.name + " error: activity has no icon");
			isValid = false;
		}
		return isValid;
	}

	private static boolean checkEnabled(ResolveInfo resolveInfo) {
		return resolveInfo.activityInfo.enabled;
	}

	private static boolean checkExported(ResolveInfo resolveInfo) {
		return resolveInfo.activityInfo.exported;
	}

	private static boolean checkLabel(ResolveInfo resolveInfo) {
		return resolveInfo.activityInfo.labelRes != 0;
	}

	private static boolean checkIcon(ResolveInfo resolveInfo) {
		return resolveInfo.activityInfo.icon != 0;
	}
}
