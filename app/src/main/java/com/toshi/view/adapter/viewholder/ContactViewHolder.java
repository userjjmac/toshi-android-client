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

package com.toshi.view.adapter.viewholder;


import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.toshi.R;
import com.toshi.model.local.User;
import com.toshi.util.ImageUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.custom.StarRatingView;

public class ContactViewHolder extends ClickableViewHolder {

    private static final int MAX_NAME_LENGHT = 25;

    private ImageView avatar;
    private TextView name;
    private TextView username;
    private StarRatingView ratingView;
    private TextView reviewCount;

    public ContactViewHolder(final View view) {
        super(view);
        this.name = (TextView) view.findViewById(R.id.name);
        this.username = (TextView) view.findViewById(R.id.username);
        this.avatar = (ImageView) view.findViewById(R.id.avatar);
        this.ratingView = (StarRatingView) view.findViewById(R.id.rating_view);
        this.reviewCount = (TextView) view.findViewById(R.id.review_count);
    }

    public void setUser(final User user) {
        final String displayName = user.getDisplayName().length() > MAX_NAME_LENGHT
                ? String.format("%s...", user.getDisplayName().substring(0, MAX_NAME_LENGHT))
                : user.getDisplayName();
        this.name.setText(displayName);

        final String username = user.getUsername().length() > MAX_NAME_LENGHT
                ? String.format("%s...", user.getUsername().substring(0, MAX_NAME_LENGHT))
                : user.getUsername();
        this.username.setText(username);

        ImageUtil.load(user.getAvatar(), this.avatar);
        this.ratingView.setStars(user.getAverageRating());
        final String reviewCount = BaseApplication.get().getString(R.string.parentheses, user.getReviewCount());
        this.reviewCount.setText(reviewCount);
    }
}
