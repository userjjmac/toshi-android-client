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

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.toshi.R;
import com.toshi.crypto.util.TypeConverter;
import com.toshi.model.local.Network;
import com.toshi.model.local.Networks;
import com.toshi.model.local.User;
import com.toshi.util.EthUtil;
import com.toshi.util.ImageUtil;
import com.toshi.util.LogUtil;
import com.toshi.util.OnSingleClickListener;
import com.toshi.util.PaymentType;
import com.toshi.view.BaseApplication;
import com.toshi.view.fragment.DialogFragment.PaymentConfirmationDialog;

import java.math.BigDecimal;
import java.math.BigInteger;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public class PaymentRequestConfirmPresenter implements Presenter<PaymentConfirmationDialog> {

    private PaymentConfirmationDialog view;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttached = true;

    private User user;
    private Bundle bundle;
    private String encodedEthAmount;
    private String memo;
    private String paymentAddress;
    private String toshiId;
    private @PaymentType.Type int paymentType;

    @Override
    public void onViewAttached(PaymentConfirmationDialog view) {
        this.view = view;

        if (this.firstTimeAttached) {
            this.firstTimeAttached = false;
            initLongLivingObjects();
        }

        initShortLivingObjects();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initShortLivingObjects() {
        initNetworkView();
        initClickListeners();
        processBundleData();
        updateView();
        tryLoadUser();
    }

    private void initNetworkView() {
        final Network network = Networks.getInstance().getCurrentNetwork();
        this.view.getBinding().network.setText(network.getName());
    }

    private void initClickListeners() {
        this.view.getBinding().reject.setOnClickListener(__ -> handleRejectClicked());
        this.view.getBinding().approve.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(View v) {
                handleApprovedClicked();
            }
        });
    }

    private void handleApprovedClicked() {
        this.view.getPaymentConfirmationListener().onPaymentApproved(this.bundle);
        this.view.dismiss();
    }

    private void handleRejectClicked() {
        this.view.getPaymentConfirmationListener().onPaymentRejected(this.bundle);
        this.view.dismiss();
    }

    @SuppressWarnings("WrongConstant")
    private void processBundleData() {
        this.bundle = this.view.getArguments();
        this.toshiId = this.bundle.getString(PaymentConfirmationDialog.TOSHI_ID);
        this.paymentAddress = this.bundle.getString(PaymentConfirmationDialog.PAYMENT_ADDRESS);
        this.encodedEthAmount = this.bundle.getString(PaymentConfirmationDialog.ETH_AMOUNT);
        this.memo = this.bundle.getString(PaymentConfirmationDialog.MEMO);
        this.paymentType = this.bundle.getInt(PaymentConfirmationDialog.PAYMENT_TYPE);
    }

    private void tryLoadUser() {
        if (this.toshiId == null) return;
        final Subscription sub =
                BaseApplication
                .get()
                .getRecipientManager()
                .getUserFromToshiId(this.toshiId)
                .doOnSuccess(user -> this.user = user)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        __ -> updateView(),
                        this::handleUserError
                );

        this.subscriptions.add(sub);
    }

    private void handleUserError(final Throwable throwable) {
        if (this.view == null) return;
        Toast.makeText(
                this.view.getContext(),
                this.view.getString(R.string.invalid_payment),
                Toast.LENGTH_SHORT
        ).show();
    }

    private void updateView() {
        setTitle();
        setUserInfo();
        setPaymentAddress();
        setMemo();
        setLocalCurrency();
    }

    private void setTitle() {
        final String title = this.paymentType == PaymentType.TYPE_SEND
                ? this.view.getString(R.string.confirmation_dialog_title_payment)
                : this.view.getString(R.string.confirmation_dialog_title_request);
        this.view.getBinding().title.setText(title);
    }

    private void setUserInfo() {
        if (this.user == null) return;
        this.view.getBinding().userInfoWrapper.setVisibility(View.VISIBLE);
        ImageUtil.loadFromNetwork(user.getAvatar(), this.view.getBinding().avatar);
        this.view.getBinding().displayName.setText(this.user.getDisplayName());
        this.view.getBinding().username.setText(this.user.getUsername());
        final String reviewCount = BaseApplication.get().getString(R.string.parentheses, this.user.getReviewCount());
        this.view.getBinding().numberOfRatings.setText(reviewCount);
        this.view.getBinding().ratingView.setStars(this.user.getAverageRating());
    }

    private void setPaymentAddress() {
        if (this.paymentAddress == null) return;
        this.view.getBinding().paymentAddress.setVisibility(View.VISIBLE);
        final String paymentAddress = this.view.getString(R.string.payment_address_with_value, this.paymentAddress);
        this.view.getBinding().paymentAddress.setText(paymentAddress);
    }

    private void setMemo() {
        if (this.memo == null) return;
        this.view.getBinding().memo.setVisibility(View.VISIBLE);
        this.view.getBinding().memo.setText(this.memo);
    }

    private void setLocalCurrency() {
        final BigInteger weiAmount = TypeConverter.StringHexToBigInteger(this.encodedEthAmount);
        final BigDecimal ethAmount = EthUtil.weiToEth(weiAmount);

        final Subscription sub =
                BaseApplication
                .get()
                .getBalanceManager()
                .convertEthToLocalCurrencyString(ethAmount)
                .subscribe((localCurrency) -> {
                    final String usdEth = this.view.getString(R.string.local_dot_eth_amount, localCurrency, getEthValue());
                    this.view.getBinding().ethUsd.setText(usdEth);
                },
                        throwable -> LogUtil.exception(getClass(), throwable)
                );

        this.subscriptions.add(sub);
    }

    private String getEthValue() {
        final BigInteger eth = TypeConverter.StringHexToBigInteger(this.encodedEthAmount);
        return EthUtil.weiAmountToUserVisibleString(eth);
    }

    @Override
    public void onViewDetached() {
        this.subscriptions.clear();
        this.view = null;
    }

    @Override
    public void onDestroyed() {
        this.subscriptions = null;
        this.view = null;
    }
}
