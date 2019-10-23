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

package ac.robinson.bettertogether.plugin.base.shopping;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

public abstract class BaseItemActivity extends BaseShoppingActivity implements SensorEventListener {

	private static final String TAG = "BaseItemActivity";

	// for tracking face up/down for navigation
	private static final int SENSOR_READINGS_REQUIRED_FOR_ROTATION = 2; // how many readings required to count as a rotation
	private SensorManager mSensorManager;
	private int mRotationSensorReadingCount = 0;
	private float[] mRotationMatrix = new float[9];
	private boolean mHasFlipped = false;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE); // set up listening for sensor events
	}

	@Override
	protected void onResume() {
		super.onResume();
		// could use rotation vector, but it is sometimes claimed to be present when it isn't - instead manually choose sensor
		Log.d(TAG, "Unable to register rotation vector sensor listener - trying accelerometer");
		if (mSensorManager.registerListener(BaseItemActivity.this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_UI)) {
			Log.d(TAG, "Successfully registered accelerometer sensor listener");
		} else {
			Log.d(TAG, "Unable to register rotation accelerometer sensor listener - flip events will not work");
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mSensorManager.unregisterListener(BaseItemActivity.this);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
			case Sensor.TYPE_ROTATION_VECTOR:
				SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
				double inclination = Math.toDegrees(Math.acos(mRotationMatrix[8]));
				if (inclination <= 80) { // used to use full flip, but better if image has changed when back to face up
					mHasFlipped = false;
					mRotationSensorReadingCount = 0;
				} else if (inclination >= 100) {
					mRotationSensorReadingCount += 1;
					if (!mHasFlipped && mRotationSensorReadingCount >= SENSOR_READINGS_REQUIRED_FOR_ROTATION) {
						Log.d(TAG, "Detected phone flip");
						mHasFlipped = true;
						onFlip();
					}
				}
				break;

			case Sensor.TYPE_ACCELEROMETER:
				float zValue = event.values[2];
				if (zValue >= 1.5) { // used to use full flip, but better if image has changed when back to face up
					mHasFlipped = false;
					mRotationSensorReadingCount = 0;
				} else if (zValue <= -1.5) {
					mRotationSensorReadingCount += 1;
					if (!mHasFlipped && mRotationSensorReadingCount >= SENSOR_READINGS_REQUIRED_FOR_ROTATION) {
						Log.d(TAG, "Detected phone flip");
						mHasFlipped = true;
						onFlip();
					}
				}
				break;

			default:
				break;
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// currently not handled
	}

	protected abstract void onFlip();
}
