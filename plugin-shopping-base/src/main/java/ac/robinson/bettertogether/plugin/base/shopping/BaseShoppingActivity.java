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

import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import ac.robinson.bettertogether.api.BasePluginActivity;
import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import androidx.annotation.Nullable;

public abstract class BaseShoppingActivity extends BasePluginActivity {

	private static final String TAG = "BaseShoppingActivity";

	// how many items of each filter type are available
	static final int[] AVAILABLE_ITEMS = { 8, 5, 7, 9, 7 };

	// broadcast message types
	static final int TYPE_TOUCH_DOWN = 1;
	static final int TYPE_TOUCH_UP = 2;
	static final int TYPE_TOUCH_CANCEL = 3;
	static final int TYPE_CLICK = 4;

	boolean mViewIsTouched;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	View.OnTouchListener mOnTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					Log.d(TAG, "Handling touch down");
					mViewIsTouched = true;
					sendMessage(new BroadcastMessage(TYPE_TOUCH_DOWN, getState()));
					break;

				case MotionEvent.ACTION_CANCEL:
					Log.d(TAG, "Handling touch cancel");
					mViewIsTouched = false;
					sendMessage(new BroadcastMessage(TYPE_TOUCH_CANCEL, getState()));
					break;

				case MotionEvent.ACTION_UP:
					Log.d(TAG, "Handling touch up / click");
					sendMessage(new BroadcastMessage(TYPE_TOUCH_UP, getState()));
					if (mViewIsTouched) {
						// we do this, rather than having a click listener, because the AppCompat ImageView steals all the
						// clicks from the RecyclerView, regardless of how we lay them out TODO: re-check after updates
						handleClick(v);
					}
					mViewIsTouched = false;
					break;

				default:
					break;
			}
			return false; // we still want click events
		}
	};

	void handleClick(View view) {
		sendMessage(new BroadcastMessage(TYPE_CLICK, getState()));
	}

	protected abstract String getState();
}
