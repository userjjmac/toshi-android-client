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
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.toshi.R;
import com.toshi.model.local.User;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.GroupParticipantsActivity;
import com.toshi.view.activity.GroupSetupActivity;
import com.toshi.view.adapter.SelectGroupParticipantAdapter;
import com.toshi.view.custom.HorizontalLineDivider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public class GroupParticipantsPresenter implements Presenter<GroupParticipantsActivity> {

    private GroupParticipantsActivity activity;
    private CompositeSubscription subscriptions;
    private List<User> participants;

    private boolean firstTimeAttaching = true;

    @Override
    public void onViewAttached(GroupParticipantsActivity view) {
        this.activity = view;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }
        initShortLivingObjects();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
        this.participants = new ArrayList<>();
    }

    private void initShortLivingObjects() {
        initClickListeners();
        initRecyclerView();
        initSearch();
        addUserToView(this.participants);
        showOrHideNextButton(this.participants);
    }

    private void initClickListeners() {
        this.activity.getBinding().closeButton.setOnClickListener(__ -> this.activity.finish());
        this.activity.getBinding().next.setOnClickListener(__ -> handleNextClicked());
    }

    private void handleNextClicked() {
        final ArrayList<String> idList = transformsUserListToIdList(this.participants);
        final Intent intent = new Intent(this.activity, GroupSetupActivity.class)
                .putStringArrayListExtra(GroupSetupActivity.PARTICIPANTS, idList);
        this.activity.startActivity(intent);
    }

    private ArrayList<String> transformsUserListToIdList(final List<User> participants) {
        final ArrayList<String> idList = new ArrayList<>();
        for (final User user : participants) {
            idList.add(user.getToshiId());
        }
        return idList;
    }

    private void initRecyclerView() {
        final RecyclerView recyclerView = this.activity.getBinding().searchResults;
        recyclerView.setLayoutManager(new LinearLayoutManager(this.activity));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        final SelectGroupParticipantAdapter adapter = new SelectGroupParticipantAdapter()
                .setSelectedUsers(this.participants)
                .setOnItemClickListener(this::handleUserClicked);
        recyclerView.setAdapter(adapter);

        final int dividerLeftPadding = this.activity.getResources().getDimensionPixelSize(R.dimen.avatar_size_small)
                + this.activity.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        final HorizontalLineDivider lineDivider =
                new HorizontalLineDivider(ContextCompat.getColor(this.activity, R.color.divider))
                        .setLeftPadding(dividerLeftPadding);
        recyclerView.addItemDecoration(lineDivider);
    }

    private void handleUserClicked(final User user) {
        addOrRemoveUser(user);
        showOrHideNextButton(this.participants);
        addUserToView(this.participants);
    }

    private void addOrRemoveUser(final User user) {
        if (this.participants.contains(user)) {
            this.participants.remove(user);
        } else {
            this.participants.add(user);
        }
    }

    private void showOrHideNextButton(final List<User> participants) {
        if (participants.size() > 0) {
            showNextButton();
        } else {
            hideNextButton();
        }
    }

    private void showNextButton() {
        this.activity.getBinding().next.setVisibility(View.VISIBLE);
    }

    private void hideNextButton() {
        this.activity.getBinding().next.setVisibility(View.GONE);
    }

    private void addUserToView(final List<User> users) {
        final String stringList = getUsersAsString(users);
        this.activity.getBinding().participants.setText(stringList);
    }

    private String getUsersAsString(final List<User> users) {
        final StringBuilder usersString = new StringBuilder();
        for (int i = 0; i < users.size(); i++) {
            final User user = users.get(i);
            final String displayNameWithPrefix = i == 0
                    ? user.getDisplayName()
                    : String.format(", %s", user.getDisplayName());
            usersString.append(displayNameWithPrefix);
        }
        return usersString.toString();
    }

    private void initSearch() {
        final Subscription sub =
                RxTextView
                .textChangeEvents(this.activity.getBinding().userInput)
                .debounce(500, TimeUnit.MILLISECONDS)
                .map(event -> event.text().toString())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::submitQuery,
                        this::handleSearchError
                );

        this.subscriptions.add(sub);
    }

    private void submitQuery(final String query) {
        if (query.length() < 3) {
            this.getParticipantsAdapter().clear();
            return;
        }

        final Subscription sub =
                BaseApplication
                .get()
                .getRecipientManager()
                .searchOnlineUsers(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        users -> getParticipantsAdapter().setUsers(users),
                        this::handleSearchError
                );

        this.subscriptions.add(sub);
    }

    private void handleSearchError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error while searching for user", throwable);
    }

    private SelectGroupParticipantAdapter getParticipantsAdapter() {
        return (SelectGroupParticipantAdapter) this.activity.getBinding().searchResults.getAdapter();
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
