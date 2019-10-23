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

package ac.robinson.bettertogether.hotspot;

import android.net.UrlQuerySanitizer;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ac.robinson.bettertogether.BetterTogetherUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ConnectionOptions {
	// note: hotspot IP address is hardcoded in Android sources: http://stackoverflow.com/a/23183923
	public static final String DEFAULT_HOTSPOT_NAME_FORMAT = "%s-%s"; // [app_name_short]-[random MESSAGE_ID_SIZE length string]
	public static final String DEFAULT_HOTSPOT_URL_FORMAT = "http://%s?%s=%s"; // http://{plugin-package}?{ssid}={password}
	public static final Pattern DEFAULT_HOTSPOT_URL_MATCHER = Pattern.compile("http://([\\w\\d\\.]+)\\?.*");
	public static final String DEFAULT_HOTSPOT_IP_ADDRESS = "192.168.43.1"; // hardcoded in Android - see note above
	public static final int DEFAULT_HOTSPOT_PORT = 33113; // up to 65535

	public String mName;
	public String mPassword;
	public String mIPAddress;
	public int mPort;

	public String mPluginPackage;
	public boolean mUseWifi = true;
	public boolean mUseBluetooth = true;

	public String getHotspotUrl() {
		// reverse the package string so it can be used as a url if scanned normally
		return formatHotspotUrl(DEFAULT_HOTSPOT_URL_FORMAT, BetterTogetherUtils.reversePackageString(mPluginPackage), mName,
				mPassword);
	}

	@Nullable
	public static ConnectionOptions fromHotspotUrl(@NonNull String url) {
		ConnectionOptions connectionOptions = new ConnectionOptions();
		UrlQuerySanitizer sanitizer = new UrlQuerySanitizer(url);
		List<UrlQuerySanitizer.ParameterValuePair> parameters = sanitizer.getParameterList();
		if (parameters.size() == 1) {
			UrlQuerySanitizer.ParameterValuePair configuration = parameters.get(0);
			connectionOptions.mName = configuration.mParameter;
			connectionOptions.mPassword = configuration.mValue;
			connectionOptions.mIPAddress = DEFAULT_HOTSPOT_IP_ADDRESS; // custom ip not needed for now - hardcoded
			connectionOptions.mPort = DEFAULT_HOTSPOT_PORT; // custom port not needed for now - hardcoded

			connectionOptions.mPluginPackage = getPackageFromHotspotUrl(url);
			return connectionOptions;
		}
		return null;
	}

	public static String formatHotspotName(String format, String name, String uniqueId) {
		String hotspotName = String.format(Locale.US, format, name, uniqueId);
		return hotspotName.substring(0, Math.min(hotspotName.length(), 20)); // need to trim to 20 chars max for some devices
	}

	public static String formatHotspotUrl(String format, String packageName, String hotspotName, String password) {
		return String.format(Locale.US, format, packageName, hotspotName, password);
	}

	public static String getPackageFromHotspotUrl(String url) {
		Matcher matcher = DEFAULT_HOTSPOT_URL_MATCHER.matcher(url);
		if (matcher.matches()) {
			return BetterTogetherUtils.reversePackageString(matcher.group(1));
		}
		return null;
	}
}
