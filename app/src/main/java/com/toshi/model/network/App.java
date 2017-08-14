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

package com.toshi.model.network;

import com.squareup.moshi.Json;
import com.toshi.model.local.ToshiEntity;
import com.toshi.view.adapter.SearchAppAdapter;

public class App implements ToshiEntity {
    private String toshi_id;
    private String about;
    private String name;
    private String avatar;
    private String payment_address;
    private String location;
    @Json(name = "public")
    private boolean is_public;
    private String username;
    private Double reputation_score;
    private Double average_rating;
    private int review_count;
    private boolean is_app;

    @Override
    public int getReviewCount() {
        return review_count;
    }

    @Override
    public Double getReputationScore() {
        if (this.reputation_score == null) return 0.0;
        return reputation_score;
    }

    @Override
    public Double getAverageRating() {
        if (this.average_rating == null) return 0.0;
        return average_rating;
    }

    @Override
    public String getToshiId() {
        return toshi_id;
    }

    public String getPaymentAddress() {
        return payment_address;
    }

    public boolean isApp() {
        return is_app;
    }

    @Override
    public String getAbout() {
        return about;
    }

    // Defaults to the username if no name is set.
    @Override
    public String getDisplayName() {
        if (this.name != null && this.name.length() > 0) {
            return this.name;
        }
        return username;
    }

    @Override
    public String getAvatar() {
        return avatar;
    }

    public String getUsername() {
        return username;
    }

    public @SearchAppAdapter.ViewType int getViewType() {
        return SearchAppAdapter.ITEM;
    }
}
