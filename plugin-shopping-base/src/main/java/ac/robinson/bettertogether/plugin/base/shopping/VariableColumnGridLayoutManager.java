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

import android.content.Context;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;

// see: http://stackoverflow.com/a/31154956
// note: this doesn't do quite do what we want (variable width columns *and* items to keep everything on-screen)
class VariableColumnGridLayoutManager extends GridLayoutManager {

	private final int minItemWidth;

	VariableColumnGridLayoutManager(Context context, int minItemWidth) {
		super(context, 1);
		this.minItemWidth = minItemWidth;
	}

	@Override
	public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
		updateSpanCount();
		super.onLayoutChildren(recycler, state);
	}

	private void updateSpanCount() {
		int spanCount = getWidth() / minItemWidth;
		if (spanCount < 1) {
			spanCount = 1;
		}
		this.setSpanCount(spanCount);
	}
}
