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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.util.LinkedList;

import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import jp.wasabeef.recyclerview.animators.FadeInAnimator;

public class BasketActivity extends BaseShoppingActivity {

	private static final String TAG = "BasketActivity";

	public static final String BASKET_IDENTIFIER = "basket";

	private ImageView mBasketImage;
	private RecyclerView mBasketItemsView;
	private BasketItemsAdapter mBasketItemsViewAdapter;

	private final LinkedList<Integer> mBasketItems = new LinkedList<>();
	private LinkedList<Integer> mTouchedItems = new LinkedList<>();

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_basket);

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setDisplayShowTitleEnabled(true);
		}

		mBasketImage = findViewById(R.id.basket_image);
		mBasketImage.setOnTouchListener(mOnTouchListener);

		mBasketItemsViewAdapter = new BasketItemsAdapter();
		mBasketItemsView = findViewById(R.id.basket_items);
		mBasketItemsView.setLayoutManager(new VariableColumnGridLayoutManager(BasketActivity.this,
				getResources().getDimensionPixelSize(R.dimen.item_size)));
		mBasketItemsView.setAdapter(mBasketItemsViewAdapter);
		mBasketItemsView.setOnTouchListener(mOnTouchListener);

		RecyclerView.ItemAnimator animator = new FadeInAnimator();
		animator.setAddDuration(250);
		animator.setRemoveDuration(50);
		animator.setMoveDuration(100);
		animator.setChangeDuration(250);
		mBasketItemsView.setItemAnimator(animator);

		if (savedInstanceState != null) {
			String[] savedBasketItems = TextUtils.split(savedInstanceState.getString("mBasketItems"), ",");
			for (String savedItem : savedBasketItems) {
				mBasketItemsViewAdapter.addItem(Integer.parseInt(savedItem));
			}
			String[] savedTouchedItems = TextUtils.split(savedInstanceState.getString("mTouchedItems"), ",");
			for (String savedItem : savedTouchedItems) {
				mTouchedItems.add(Integer.parseInt(savedItem));
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString("mBasketItems", TextUtils.join(",", mBasketItems));
		outState.putString("mTouchedItems", TextUtils.join(",", mTouchedItems));
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onMessageReceived(@NonNull BroadcastMessage message) {
		Log.d(TAG, "Message: " + message.getType() + ", " + message.getMessage());
		String command = message.getMessage();
		if (TextUtils.isEmpty(command) || !command.startsWith(ItemActivity.ITEM_IDENTIFIER)) {
			return; // we only care about changes in items
		}

		int selectedItem = getResources().getIdentifier(command, "drawable", getPackageName());

		switch (message.getType()) {
			case TYPE_TOUCH_DOWN:
				mTouchedItems.add(selectedItem);
				break;

			case TYPE_TOUCH_CANCEL:
			case TYPE_TOUCH_UP:
				mTouchedItems.removeFirstOccurrence(selectedItem);
				break;

			case TYPE_CLICK:
				if (mViewIsTouched) {
					mBasketItemsViewAdapter.removeItem(selectedItem);
				}
				break;

			default:
				break;
		}
	}

	@Override
	protected void handleClick(View view) {
		super.handleClick(view);

		final int viewId = view.getId();
		if (viewId == R.id.basket_image || viewId == R.id.basket_items) {
			for (Integer item : mTouchedItems) {
				mBasketItemsViewAdapter.addItem(item);
			}
		}
	}

	@Override
	protected String getState() {
		return BASKET_IDENTIFIER;
	}

	private class BasketItemsAdapter extends RecyclerView.Adapter<BasketItemsAdapter.BasketViewHolder> {
		@Override
		public BasketViewHolder onCreateViewHolder(ViewGroup parent, int position) {
			View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_basket, parent, false);
			return new BasketViewHolder(view);
		}

		void addItem(int item) {
			mBasketItems.add(0, item);
			mBasketItemsViewAdapter.notifyItemInserted(0);

			DisplayMetrics metrics = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(metrics);
			RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBasketImage.getLayoutParams();
			params.height = metrics.heightPixels / 4;
			mBasketImage.setLayoutParams(params);
			mBasketItemsView.setVisibility(View.VISIBLE);

			mBasketItemsView.scrollToPosition(0); // hacky!
		}

		void removeItem(int item) {
			int position = mBasketItems.indexOf(item);
			if (position >= 0) {
				mBasketItems.removeFirstOccurrence(item);
				mBasketItemsViewAdapter.notifyItemRemoved(position);

				if (getItemCount() <= 0) {
					DisplayMetrics metrics = new DisplayMetrics();
					getWindowManager().getDefaultDisplay().getMetrics(metrics);
					RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBasketImage.getLayoutParams();
					params.height = metrics.heightPixels;
					mBasketImage.setLayoutParams(params);
					mBasketItemsView.setVisibility(View.GONE);
				}
			}
		}

		@Override
		public void onBindViewHolder(BasketViewHolder viewHolder, int position) {
			viewHolder.mItem.setImageResource(mBasketItems.get(position));
		}

		@Override
		public int getItemCount() {
			return mBasketItems.size();
		}

		class BasketViewHolder extends RecyclerView.ViewHolder {
			private final ImageView mItem;

			BasketViewHolder(View view) {
				super(view);
				mItem = view.findViewById(R.id.basket_item);
			}
		}
	}
}
