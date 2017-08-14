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

package com.toshi.presenter;

import android.content.Intent;

import com.toshi.R;
import com.toshi.model.local.User;
import com.toshi.model.network.ReputationScore;
import com.toshi.util.ImageUtil;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.EditProfileActivity;
import com.toshi.view.activity.ViewProfileActivity;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public final class ViewProfilePresenter implements Presenter<ViewProfileActivity> {

    private ViewProfileActivity activity;
    private User localUser;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttaching = true;

    @Override
    public void onViewAttached(final ViewProfileActivity view) {
        this.activity = view;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }

        initShortLivingObjects();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initShortLivingObjects() {
        initClickListeners();
        attachButtonListeners();
        updateView();
        fetchUser();
    }

    private void initClickListeners() {
        this.activity.getBinding().closeButton.setOnClickListener(__ -> this.activity.finish());
        this.activity.getBinding().editProfileButton.setOnClickListener(__ -> goToEditProfileActivity());
    }

    private void goToEditProfileActivity() {
        final Intent intent = new Intent(this.activity, EditProfileActivity.class);
        this.activity.startActivity(intent);
    }

    private void attachButtonListeners() {
        final Subscription sub =
                BaseApplication.get()
                .isConnectedSubject()
                .subscribe(
                        this::handleConnectionChanged,
                        this::handleConnectionError
                );

        this.subscriptions.add(sub);
    }

    private void handleConnectionChanged(final Boolean isConnected) {
        if (this.activity == null) return;
        this.activity.getBinding().editProfileButton.setEnabled(isConnected);
    }

    private void handleConnectionError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error during fetching connection state", throwable);
    }

    private void updateView() {
        if (this.localUser == null || this.activity == null) return;
        this.activity.getBinding().name.setText(this.localUser.getDisplayName());
        this.activity.getBinding().username.setText(this.localUser.getUsername());
        this.activity.getBinding().about.setText(this.localUser.getAbout());
        this.activity.getBinding().location.setText(this.localUser.getLocation());
        loadAvatar();
    }

    private void loadAvatar() {
        ImageUtil.load(this.localUser.getAvatar(), this.activity.getBinding().avatar);
    }

    private void fetchUser() {
        final Subscription sub =
                BaseApplication
                .get()
                .getUserManager()
                .getUserObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleUserLoaded,
                        this::handleUserError
                );

        if (!BaseApplication.get()
                .getUserManager()
                .getUserObservable()
                .hasValue()) {
            handleNoUser();
        }

        this.subscriptions.add(sub);
    }

    private void handleUserLoaded(final User user) {
        if (user == null) {
            handleNoUser();
            return;
        }
        this.localUser = user;
        updateView();
        fetchUserReputation(user.getToshiId());
    }

    private void handleUserError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error while fetching user", throwable);
    }

   private void handleNoUser() {
        if (this.activity == null) return;

        this.activity.getBinding().name.setText(this.activity.getString(R.string.profile__unknown_name));
        this.activity.getBinding().username.setText("");
        this.activity.getBinding().about.setText("");
        this.activity.getBinding().location.setText("");
        this.activity.getBinding().ratingView.setStars(0.0);
    }

    private void fetchUserReputation(final String userAddress) {
        final Subscription reputationSub =
                BaseApplication
                .get()
                .getReputationManager()
                .getReputationScore(userAddress)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleReputationResponse,
                        this::handleReputationError
                );

        this.subscriptions.add(reputationSub);
    }

    private void handleReputationResponse(final ReputationScore reputationScore) {
        if (this.activity == null) return;
        final int revCount = reputationScore.getReviewCount();
        final String ratingText = this.activity.getResources().getQuantityString(R.plurals.ratings, revCount, revCount);
        this.activity.getBinding().reviewCount.setText(ratingText);
        this.activity.getBinding().ratingView.setStars(reputationScore.getAverageRating());
        this.activity.getBinding().reputationScore.setText(String.valueOf(reputationScore.getAverageRating()));
        this.activity.getBinding().ratingInfo.setRatingInfo(reputationScore);
    }

    private void handleReputationError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error during reputation fetching", throwable);
    }

    @Override
    public void onViewDetached() {
        this.subscriptions.clear();
        this.activity = null;
    }

    @Override
    public void onDestroyed() {
        this.subscriptions = null;
        this.activity = null;
    }
}
