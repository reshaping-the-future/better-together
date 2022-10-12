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
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import java.security.SecureRandom;
import java.util.Locale;

import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

public class ItemActivity extends BaseItemActivity {

	private static final String TAG = "ItemActivity";

	public static final String ITEM_IDENTIFIER = "item";
	public static final String ITEM_FORMAT = ITEM_IDENTIFIER + "_%d_%d"; // note: must match resources naming scheme

	private int mCurrentCategory;
	private int mCurrentItem;

	private ImageView mItemView;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_item);

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setDisplayShowTitleEnabled(true);
		}

		mItemView = findViewById(R.id.shop_item);
		mItemView.setOnTouchListener(mOnTouchListener);

		if (savedInstanceState != null) {
			mCurrentCategory = savedInstanceState.getInt("mCurrentCategory");
			mCurrentItem = savedInstanceState.getInt("mCurrentItem");
			if (mCurrentCategory >= 0 && mCurrentItem >= 0) {
				setImage(mCurrentCategory, mCurrentItem);
			}
		} else {
			// no item or category initially
			mCurrentCategory = -1;
			mCurrentItem = -1;
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putInt("mCurrentCategory", mCurrentCategory);
		outState.putInt("mCurrentItem", mCurrentItem);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onMessageReceived(@NonNull BroadcastMessage message) {
		Log.d(TAG, "Message: " + message.getType() + ", " + message.getMessage());
		String command = message.getMessage();
		if (TextUtils.isEmpty(command) || !command.startsWith(CategoryActivity.CATEGORY_IDENTIFIER)) {
			return;
		}

		int newCategory = Integer.parseInt(command.split("_")[1]);
		if (mCurrentCategory != newCategory) {
			mCurrentCategory = newCategory;
			mCurrentItem = new SecureRandom().nextInt(AVAILABLE_ITEMS[mCurrentCategory]);
			setImage(mCurrentCategory, mCurrentItem);
		}
	}

	@Override
	protected String getState() {
		if (mCurrentCategory >= 0) {
			return String.format(Locale.US, ITEM_FORMAT, mCurrentCategory, mCurrentItem);
		}
		return null; // no item selected
	}

	@Override
	protected void onFlip() {
		if (mCurrentCategory >= 0) {
			mCurrentItem = (mCurrentItem + 1) % AVAILABLE_ITEMS[mCurrentCategory];
			setImage(mCurrentCategory, mCurrentItem);
		}
	}

	private void setImage(int currentCategory, int currentItem) {
		String drawableString = String.format(Locale.US, ITEM_FORMAT, currentCategory, currentItem);
		int drawable = getResources().getIdentifier(drawableString, "drawable", getPackageName());
		Log.d(TAG, "Displaying item " + drawableString);
		mItemView.setImageResource(drawable);
	}
}
