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
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper;

import java.util.ArrayList;
import java.util.List;

import ac.robinson.bettertogether.R;

public class PluginAdapter extends RecyclerView.Adapter<PluginAdapter.PluginViewHolder> implements GravitySnapHelper
		.SnapListener {

	// a fake plugin for the "get more plugins" button
	private static final String EMPTY_PACKAGE = "ac.robinson.bettertogether.plugin.open_google_play";

	private Context mContext;
	private PluginClickListener mPluginClickListener;

	private static Drawable sDefaultBackground = null;

	private List<Plugin> mPlugins;

	public PluginAdapter(Context context, PluginClickListener listener) {
		mContext = context;
		mPluginClickListener = listener;

		mPlugins = new ArrayList<>();
	}

	public void clearPlugins() {
		mPlugins.clear();
		mPlugins.add(new Plugin(mContext.getString(R.string.get_plugins), EMPTY_PACKAGE));
		notifyDataSetChanged();
	}

	public void addPlugin(Plugin plugin) {
		mPlugins.add(0, plugin);
		notifyDataSetChanged();
	}

	@Override
	public PluginViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_plugins, parent, false);
		if (sDefaultBackground == null) {
			sDefaultBackground = itemView.findViewById(R.id.plugin_label).getBackground();
		}
		return new PluginViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(PluginViewHolder holder, int position) {
		Plugin plugin = mPlugins.get(position);
		holder.mTextView.setText(plugin.getFilteredPluginLabel(mContext));
		if (EMPTY_PACKAGE.equals(plugin.getPackageName())) {
			holder.mTextView.setCompoundDrawablesWithIntrinsicBounds(null, mContext.getResources().getDrawable(R.drawable
					.ic_add_grey_900_48dp), null, null);
			// holder.mTextView.setBackgroundDrawable(sDefaultBackground);
		} else {
			holder.mTextView.setCompoundDrawablesWithIntrinsicBounds(null, plugin.getIcon(mContext), null, null);
			// holder.mTextView.setBackgroundColor(BetterTogetherUtils.getThemeColour(mContext, plugin.getTheme(), R.attr
			// .colorButtonNormal));
		}
	}

	@Override
	public int getItemCount() {
		return mPlugins.size();
	}

	@Override
	public void onSnap(int position) {
	}

	class PluginViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		final TextView mTextView;

		PluginViewHolder(View itemView) {
			super(itemView);
			itemView.setOnClickListener(this);
			mTextView = (TextView) itemView.findViewById(R.id.plugin_label);
		}

		@Override
		public void onClick(View v) {
			Plugin plugin = mPlugins.get(getAdapterPosition());
			if (EMPTY_PACKAGE.equals(plugin.getPackageName())) {
				plugin = null; // null = empty plugin: "get more plugins" button
			}
			mPluginClickListener.onClick(plugin);
		}
	}
}
