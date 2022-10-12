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

package ac.robinson.bettertogether;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;

import androidx.core.widget.PopupWindowCompat;

class QRPopupWindow {
	private View mAnchorView;
	private PopupWindow mPopupWindow;
	private WindowManager mWindowManager;

	// suppress because we don't have (and can't get) a root view for the PopupWindow
	@SuppressLint("InflateParams")
	QRPopupWindow(View anchorView) {
		mAnchorView = anchorView;
		mWindowManager = (WindowManager) mAnchorView.getContext().getSystemService(Context.WINDOW_SERVICE);
		mPopupWindow = new PopupWindow(mAnchorView.getContext());

		LayoutInflater inflater = (LayoutInflater) mAnchorView.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mPopupWindow.setContentView(inflater.inflate(R.layout.dialog_qr_code, null));

		mPopupWindow.setTouchInterceptor(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent event) {
				dismissPopUp();
				return true;
			}
		});
	}

	void setQRBitmap(Bitmap background) {
		mPopupWindow.setBackgroundDrawable(new BitmapDrawable(mAnchorView.getResources(), background));
	}

	void showPopUp() {
		// could use mPopupWindow.getMaxAvailableHeight() if only there was a width version!
		DisplayMetrics displayMetrics = new DisplayMetrics();
		mWindowManager.getDefaultDisplay().getMetrics(displayMetrics);
		int popupSize = (int) Math.round((Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels)) * 0.9);

		// normally: WindowManager.LayoutParams.WRAP_CONTENT);
		mPopupWindow.setWidth(popupSize);
		mPopupWindow.setHeight(popupSize);
		mPopupWindow.setTouchable(true);
		mPopupWindow.setFocusable(true);
		mPopupWindow.setOutsideTouchable(true);
		mPopupWindow.setAnimationStyle(android.R.style.Animation_Dialog);

		int elevation = mAnchorView.getContext().getResources().getDimensionPixelSize(R.dimen.default_elevation);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			mPopupWindow.setElevation(elevation);
		}

		// display as popup overlaying anchor view - as 4dp elevation is same as horizontal offset, use that precalculated value
		PopupWindowCompat.setOverlapAnchor(mPopupWindow, true);
		PopupWindowCompat.showAsDropDown(mPopupWindow, mAnchorView, -elevation, 0, Gravity.TOP | Gravity.END);
	}

	private void dismissPopUp() {
		mPopupWindow.dismiss();
	}
}
