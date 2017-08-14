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
import android.view.MenuItem;
import android.view.View;

import com.toshi.R;
import com.toshi.model.local.Contact;
import com.toshi.model.local.User;
import com.toshi.util.LogUtil;
import com.toshi.util.OnSingleClickListener;
import com.toshi.util.UserSearchType;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.ScannerActivity;
import com.toshi.view.activity.UserSearchActivity;
import com.toshi.view.activity.ViewUserActivity;
import com.toshi.view.adapter.ContactsAdapter;
import com.toshi.view.adapter.listeners.OnItemClickListener;
import com.toshi.view.custom.HorizontalLineDivider;
import com.toshi.view.fragment.toplevel.ContactsFragment;

import java.util.List;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public final class ContactsPresenter implements
        Presenter<ContactsFragment>,
        OnItemClickListener<User> {

    private ContactsFragment fragment;
    private boolean firstTimeAttaching = true;
    private ContactsAdapter adapter;
    private CompositeSubscription subscriptions;

    @Override
    public void onViewAttached(final ContactsFragment fragment) {
        this.fragment = fragment;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }
        initShortLivingObjects();
    }

    private void initShortLivingObjects() {
        initClickListeners();
        initRecyclerView();
        loadContacts();
    }

    private void initClickListeners() {
        this.fragment.getBinding().userSearch.setOnClickListener(this.handleUserSearchClicked);
        this.fragment.getBinding().inviteFriends.setOnClickListener(__ -> handleInviteFriends());
    }

    private void initRecyclerView() {
        final RecyclerView recyclerView = this.fragment.getBinding().contacts;
        final RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this.fragment.getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(this.adapter);

        final int dividerLeftPadding = fragment.getResources().getDimensionPixelSize(R.dimen.avatar_size_small)
                + fragment.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin)
                + fragment.getResources().getDimensionPixelSize(R.dimen.list_item_avatar_margin);
        final int dividerRightPadding = fragment.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        final HorizontalLineDivider lineDivider =
                new HorizontalLineDivider(ContextCompat.getColor(this.fragment.getContext(), R.color.divider))
                        .setRightPadding(dividerRightPadding)
                        .setLeftPadding(dividerLeftPadding);
        recyclerView.addItemDecoration(lineDivider);
    }

    private void loadContacts() {
        final Subscription sub = BaseApplication
                .get()
                .getRecipientManager()
                .loadAllContacts()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleContacts,
                        this::handleContactsError
                );

        this.subscriptions.add(sub);
    }

    private void handleContacts(final List<Contact> contacts) {
        this.adapter.mapContactsToUsers(contacts);
        updateEmptyState();
    }

    private void handleContactsError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error while fetching contacts", throwable);
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
        this.adapter = new ContactsAdapter()
                .setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(final User clickedUser) {
        final Intent intent = new Intent(this.fragment.getActivity(), ViewUserActivity.class);
        intent.putExtra(ViewUserActivity.EXTRA__USER_ADDRESS, clickedUser.getToshiId());
        this.fragment.startActivity(intent);
    }

    private void updateEmptyState() {
        if (this.fragment == null) return;
        
        // Hide empty state if we have some content
        final boolean shouldShowEmptyState = this.adapter.getItemCount() == 0;

        if (shouldShowEmptyState) {
            this.fragment.getBinding().emptyState.setVisibility(View.VISIBLE);
            this.fragment.getBinding().contacts.setVisibility(View.GONE);
        } else {
            this.fragment.getBinding().emptyState.setVisibility(View.GONE);
            this.fragment.getBinding().contacts.setVisibility(View.VISIBLE);
        }
    }

    private final OnSingleClickListener handleUserSearchClicked = new OnSingleClickListener() {
        @Override
        public void onSingleClick(final View v) {
            startUserSearchActivity();
        }
    };

    public void handleActionMenuClicked(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scan_qr:
                startScanQrActivity();
                break;
            case R.id.invite_friends:
                handleInviteFriends();
                break;
            case R.id.search_people:
                startUserSearchActivity();
                break;
        }
    }

    private void handleInviteFriends() {
        final Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, this.fragment.getActivity().getString(R.string.invite_friends_intent_message));
        sendIntent.setType("text/plain");
        this.fragment.getActivity().startActivity(sendIntent);
    }

    private void startUserSearchActivity() {
        if (this.fragment == null) return;
        final Intent intent = new Intent(this.fragment.getActivity(), UserSearchActivity.class)
                .putExtra(UserSearchActivity.VIEW_TYPE, UserSearchType.PROFILE);
        fragment.startActivity(intent);
    }

    private void startScanQrActivity() {
        if (this.fragment == null) return;
        final Intent intent = new Intent(this.fragment.getActivity(), ScannerActivity.class);
        this.fragment.getActivity().startActivity(intent);
    }

    @Override
    public void onViewDetached() {
        this.subscriptions.clear();
        this.fragment = null;
    }

    @Override
    public void onDestroyed() {
        this.adapter = null;
        this.fragment = null;
    }
}
