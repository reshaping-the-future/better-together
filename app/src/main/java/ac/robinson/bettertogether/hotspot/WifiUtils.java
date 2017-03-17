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

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

class WifiUtils {

	static boolean isWifiAvailable(Context context) {
		return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
	}

	// returns one of WifiManager.WIFI_AP_STATE_*
	static int getWifiHotspotState(WifiManager mWifiManager) throws NoSuchMethodException, InvocationTargetException,
			IllegalAccessException {
		Method getWifiApConfiguration = mWifiManager.getClass().getMethod("getWifiApState");
		return (Integer) getWifiApConfiguration.invoke(mWifiManager);
	}

	// get the current hotspot configuration
	static WifiConfiguration getWifiHotspotConfiguration(WifiManager mWifiManager) throws NoSuchMethodException,
			InvocationTargetException, IllegalAccessException {
		Method getWifiApConfiguration = mWifiManager.getClass().getMethod("getWifiApConfiguration");
		return (WifiConfiguration) getWifiApConfiguration.invoke(mWifiManager);
	}

	// update a WifiConfiguration with the given name (SSID) and password - see: https://stackoverflow.com/questions/2140133/
	static WifiConfiguration setConfigurationAttributes(@NonNull WifiConfiguration wifiConfiguration, @NonNull String name,
	                                                    @NonNull String password) {
		wifiConfiguration.SSID = name;
		wifiConfiguration.preSharedKey = password;
		return wifiConfiguration; // TODO: add other attributes? (see: http://stackoverflow.com/a/13875379)
	}

	// set the hotspot configuration without changing its on/off status
	static void setWifiHotspotConfiguration(WifiManager mWifiManager, @NonNull WifiConfiguration wifiConfiguration) throws
			NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		Method setWifiApConfiguration = mWifiManager.getClass().getMethod("setWifiApConfiguration", WifiConfiguration.class);
		setWifiApConfiguration.invoke(mWifiManager, wifiConfiguration);
	}

	// whether the wifi hotspot is on or off
	static boolean getHotspotEnabled(WifiManager mWifiManager) {
		try {
			Method isWifiApEnabled = mWifiManager.getClass().getMethod("isWifiApEnabled");
			return (Boolean) isWifiApEnabled.invoke(mWifiManager);
		} catch (Throwable ignored) {
		}
		return false;
	}

	static void setHotspotEnabled(WifiManager mWifiManager, WifiConfiguration wifiConfiguration, boolean enabled) throws
			NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		// TODO: wait for the hotspot to be enabled, then send clients a confirmation
		// TODO: do we need to wait for Wifi to finish being disabled before enabling the hotspot?
		mWifiManager.setWifiEnabled(false); // some devices require this to be called before enabling the hotspot
		Method setWifiApEnabled = mWifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
		setWifiApEnabled.invoke(mWifiManager, wifiConfiguration, enabled);
	}

	static int getWifiNetworkId(WifiManager mWifiManager, @NonNull String name) {
		// remove previous networks with the same SSID so we can update the password
		List<WifiConfiguration> configuredNetworks = mWifiManager.getConfiguredNetworks();
		if (configuredNetworks != null) { // can be null when Wifi is turned off
			for (WifiConfiguration network : configuredNetworks) {
				if (!TextUtils.isEmpty(network.SSID) && (network.SSID.equals(name))) {
					return network.networkId;
				}
			}
		}
		return -1;
	}

	// TODO: is this necessary for some devices? (see: http://stackoverflow.com/a/21968663)
	String getWifiIp() throws SocketException {
		for (Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces(); networkInterfaces
				.hasMoreElements(); ) {
			NetworkInterface networkInterface = networkInterfaces.nextElement();
			if (networkInterface.isLoopback()) {
				continue;
			}
			if (networkInterface.isVirtual()) {
				continue;
			}
			if (!networkInterface.isUp()) {
				continue;
			}
			if (networkInterface.isPointToPoint()) {
				continue;
			}
			if (networkInterface.getHardwareAddress() == null) {
				continue;
			}
			for (Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses(); inetAddresses.hasMoreElements();
					) {
				InetAddress inetAddress = inetAddresses.nextElement();
				if (inetAddress.getAddress().length == 4) {
					return inetAddress.getHostAddress();
				}
			}
		}
		return null;
	}
}
