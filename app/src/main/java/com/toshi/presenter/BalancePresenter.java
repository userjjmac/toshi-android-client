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

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;

import com.toshi.R;
import com.toshi.model.local.ActivityResultHolder;
import com.toshi.model.network.Balance;
import com.toshi.util.DialogUtil;
import com.toshi.util.LogUtil;
import com.toshi.util.PaymentType;
import com.toshi.util.SharedPrefsUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.AmountActivity;
import com.toshi.view.activity.BackupPhraseInfoActivity;
import com.toshi.view.activity.BalanceActivity;
import com.toshi.view.activity.DepositActivity;
import com.toshi.view.activity.SendActivity;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public class BalancePresenter implements Presenter<BalanceActivity> {

    private static final int SEND_REQUEST_CODE = 100;

    private BalanceActivity activity;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttaching = true;
    private AlertDialog warningDialog;

    @Override
    public void onViewAttached(BalanceActivity view) {
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
        showWarningDialogIfNotBackedUp();
        initClickListeners();
        attachBalanceSubscriber();
    }

    private void showWarningDialogIfNotBackedUp() {
        if (SharedPrefsUtil.hasBackedUpPhrase()) return;

        final AlertDialog.Builder builder =
                DialogUtil.getBaseDialog(
                        this.activity,
                        R.string.balance_dialog_title,
                        R.string.balance_dialog_body,
                        R.string.setup,
                        R.string.cancel,
                        (dialog, __) -> handlePositiveButtonClicked(dialog)
                );

        this.warningDialog = builder.create();
        this.warningDialog.show();
    }

    private void handlePositiveButtonClicked(final DialogInterface dialog) {
        dialog.dismiss();
        final Intent intent = new Intent(this.activity, BackupPhraseInfoActivity.class);
        this.activity.startActivity(intent);
    }

    private void initClickListeners() {
        this.activity.getBinding().closeButton.setOnClickListener(__ -> this.activity.finish());
        this.activity.getBinding().sendMoney.setOnClickListener(__ -> startPaymentActivityForResult());
        this.activity.getBinding().depositMoney.setOnClickListener(__ -> goToActivity(DepositActivity.class));
    }

    private void startPaymentActivityForResult() {
        if (this.activity == null) return;
        final Intent intent = new Intent(this.activity, AmountActivity.class)
                .putExtra(AmountActivity.VIEW_TYPE, PaymentType.TYPE_SEND);
        activity.startActivityForResult(intent, SEND_REQUEST_CODE);
    }

    public boolean handleActivityResult(final ActivityResultHolder resultHolder) {
        if (resultHolder.getResultCode() != Activity.RESULT_OK
                || this.activity == null
                || resultHolder.getRequestCode() != SEND_REQUEST_CODE) {
            return false;
        }

        startSendActivityWithAmountIntent(resultHolder.getIntent());
        return true;
    }

    private void startSendActivityWithAmountIntent(final Intent amountIntent) {
        if (this.activity == null) return;
        final Intent intent = new Intent(this.activity, SendActivity.class)
                .putExtra(SendPresenter.EXTRA__INTENT, amountIntent);
        this.activity.startActivity(intent);
    }

    private void attachBalanceSubscriber() {
        final Subscription sub =
                BaseApplication
                        .get()
                        .getBalanceManager()
                        .getBalanceObservable()
                        .observeOn(AndroidSchedulers.mainThread())
                        .filter(balance -> balance != null)
                        .map(this::renderBalance)
                        .flatMap(balance -> balance.getFormattedLocalBalance().toObservable())
                        .subscribe(
                                this::renderFormattedBalance,
                                ex -> LogUtil.exception(getClass(), "Error during fetching balance", ex)
                        );

        this.subscriptions.add(sub);
    }

    private Balance renderBalance(final Balance balance) {
        if (this.activity != null) this.activity.getBinding().ethBalance.setText(balance.getFormattedUnconfirmedBalance());
        return balance;
    }

    private void renderFormattedBalance(final String formattedBalance) {
        if (this.activity != null) this.activity.getBinding().localCurrencyBalance.setText(formattedBalance);
    }

    private void goToActivity(final Class clz) {
        if (this.activity == null) return;
        final Intent intent = new Intent(this.activity, clz);
        this.activity.startActivity(intent);
    }

    @Override
    public void onViewDetached() {
        closeDialog();
        this.subscriptions.clear();
        this.activity = null;
    }

    private void closeDialog() {
        if (this.warningDialog != null) {
            this.warningDialog.dismiss();
            this.warningDialog = null;
        }
    }

    @Override
    public void onDestroyed() {
        this.subscriptions = null;
        this.activity = null;
    }
}
