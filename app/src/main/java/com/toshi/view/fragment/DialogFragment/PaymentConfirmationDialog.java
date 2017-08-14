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

package com.toshi.view.fragment.DialogFragment;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.toshi.R;
import com.toshi.databinding.FragmentPaymentRequestConfirmationBinding;
import com.toshi.presenter.LoaderIds;
import com.toshi.presenter.PaymentRequestConfirmPresenter;
import com.toshi.presenter.factory.PaymentRequestConfirmPresenterFactory;
import com.toshi.presenter.factory.PresenterFactory;
import com.toshi.util.PaymentType;

public class PaymentConfirmationDialog extends BasePresenterDialogFragment<PaymentRequestConfirmPresenter, PaymentConfirmationDialog> {

    public static final String CALLBACK_ID = "callback_id";
    public static final String CONFIRMATION_TYPE = "confirmation_type";
    public static final String ETH_AMOUNT = "eth_amount";
    public static final String MEMO = "memo";
    public static final String PAYMENT_ADDRESS = "payment_address";
    public static final String PAYMENT_TYPE = "payment_type";
    public static final String TAG = "PaymentConfirmationDialog";
    public static final String TOSHI_ID = "toshi_id";
    public static final String UNSIGNED_TRANSACTION = "unsigned_transaction";

    private FragmentPaymentRequestConfirmationBinding binding;
    private OnPaymentConfirmationListener listener;

    public static PaymentConfirmationDialog newInstanceToshiPayment(@NonNull final String toshiId,
                                                                    @NonNull final String value,
                                                                    @Nullable final String memo) {
        final Bundle bundle = new Bundle();
        bundle.putInt(CONFIRMATION_TYPE, PaymentConfirmationType.TOSHI);
        bundle.putString(TOSHI_ID, toshiId);
        return newInstance(bundle, value, memo);
    }

    public static PaymentConfirmationDialog newInstanceExternalPayment(@NonNull final String paymentAddress,
                                                                       @NonNull final String value,
                                                                       @Nullable final String memo) {
        final Bundle bundle = new Bundle();
        bundle.putInt(CONFIRMATION_TYPE, PaymentConfirmationType.EXTERNAL);
        bundle.putString(PAYMENT_ADDRESS, paymentAddress);
        return newInstance(bundle, value, memo);
    }

    public static PaymentConfirmationDialog newInstanceWebPayment(@NonNull final String unsignedTransaction,
                                                                  @NonNull final String paymentAddress,
                                                                  @NonNull final String value,
                                                                  @NonNull final String callbackId,
                                                                  @Nullable final String memo) {
        final Bundle bundle = new Bundle();
        bundle.putInt(CONFIRMATION_TYPE, PaymentConfirmationType.WEB);
        bundle.putString(UNSIGNED_TRANSACTION, unsignedTransaction);
        bundle.putString(PAYMENT_ADDRESS, paymentAddress);
        bundle.putString(CALLBACK_ID, callbackId);
        return newInstance(bundle, value, memo);
    }

    private static PaymentConfirmationDialog newInstance(final Bundle bundle,
                                                         final String value,
                                                         final String memo) {
        bundle.putString(ETH_AMOUNT, value);
        bundle.putString(MEMO, memo);
        bundle.putInt(PAYMENT_TYPE, PaymentType.TYPE_SEND);
        final PaymentConfirmationDialog fragment = new PaymentConfirmationDialog();
        fragment.setArguments(bundle);
        return fragment;
    }

    public interface OnPaymentConfirmationListener {
        void onPaymentRejected(final Bundle bundle);
        void onPaymentApproved(final Bundle bundle);
    }

    public PaymentConfirmationDialog setOnPaymentConfirmationListener(final OnPaymentConfirmationListener listener) {
        this.listener = listener;
        return this;
    }

    public OnPaymentConfirmationListener getPaymentConfirmationListener() {
        return this.listener;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, final @Nullable Bundle inState) {
        this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment__payment_request_confirmation, container, false);
        return binding.getRoot();
    }

    public FragmentPaymentRequestConfirmationBinding getBinding() {
        return this.binding;
    }

    @NonNull
    @Override
    protected PresenterFactory<PaymentRequestConfirmPresenter> getPresenterFactory() {
        return new PaymentRequestConfirmPresenterFactory();
    }

    @Override
    protected void onPresenterPrepared(@NonNull PaymentRequestConfirmPresenter presenter) {}

    @Override
    protected int loaderId() {
        return LoaderIds.get(this.getClass().getCanonicalName());
    }
}
