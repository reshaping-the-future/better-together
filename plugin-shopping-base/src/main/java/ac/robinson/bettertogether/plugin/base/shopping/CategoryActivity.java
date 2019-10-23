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
import android.widget.ImageView;

import java.security.SecureRandom;
import java.util.Locale;

import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

public class CategoryActivity extends BaseItemActivity {

	private static final String TAG = "CategoryActivity";

	public static final String CATEGORY_IDENTIFIER = "category";
	public static final String CATEGORY_FORMAT = CATEGORY_IDENTIFIER + "_%d"; // note: must match resources naming scheme

	private int mCurrentCategory;

	private ImageView mCategoryView;

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

		mCategoryView = findViewById(R.id.shop_item);
		mCategoryView.setOnTouchListener(mOnTouchListener);

		if (savedInstanceState != null) {
			mCurrentCategory = savedInstanceState.getInt("mCurrentCategory");
		} else {
			mCurrentCategory = new SecureRandom().nextInt(AVAILABLE_ITEMS.length);
		}

		setImage(mCurrentCategory);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt("mCurrentCategory", mCurrentCategory);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onMessageReceived(@NonNull BroadcastMessage message) {
		Log.d(TAG, "Message: " + message.getType() + ", " + message.getMessage());
	}

	@Override
	protected String getState() {
		return String.format(Locale.US, CATEGORY_FORMAT, mCurrentCategory);
	}

	@Override
	protected void onFlip() {
		mCurrentCategory = (mCurrentCategory + 1) % AVAILABLE_ITEMS.length;
		setImage(mCurrentCategory);
	}

	private void setImage(int currentCategory) {
		String drawableString = String.format(Locale.US, CATEGORY_FORMAT, currentCategory);
		int drawable = getResources().getIdentifier(drawableString, "drawable", getPackageName());
		Log.d(TAG, "Displaying category " + drawableString);
		mCategoryView.setImageResource(drawable);
	}
}
