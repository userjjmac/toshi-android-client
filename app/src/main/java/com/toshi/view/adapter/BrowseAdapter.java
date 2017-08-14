/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.view.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.toshi.R;
import com.toshi.model.local.ToshiEntity;
import com.toshi.view.adapter.listeners.OnItemClickListener;
import com.toshi.view.adapter.viewholder.ToshiEntityViewHolder;

import java.util.ArrayList;
import java.util.List;

public class BrowseAdapter<T extends ToshiEntity> extends RecyclerView.Adapter<ToshiEntityViewHolder> {

    private final List<T> elems;
    private OnItemClickListener<T> listener;

    public BrowseAdapter() {
        this.elems = new ArrayList<>();
    }

    public BrowseAdapter setOnItemClickListener(final OnItemClickListener<T> listener) {
        this.listener = listener;
        return this;
    }

    public void setItems(final List<T> elems) {
        this.elems.clear();
        this.elems.addAll(elems);
        notifyDataSetChanged();
    }

    @Override
    public ToshiEntityViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__toshi_entity, parent, false);
        return new ToshiEntityViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ToshiEntityViewHolder holder, int position) {
        final ToshiEntity elem = this.elems.get(position);
        holder.setToshiEntity(elem)
                .setOnClickListener(this.listener, elem);
    }

    @Override
    public int getItemCount() {
        return this.elems.size();
    }
}