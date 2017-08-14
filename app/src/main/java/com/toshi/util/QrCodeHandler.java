package com.toshi.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.toshi.R;
import com.toshi.exception.InvalidQrCode;
import com.toshi.exception.InvalidQrCodePayment;
import com.toshi.model.local.QrCodePayment;
import com.toshi.model.local.User;
import com.toshi.model.sofa.Payment;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.ChatActivity;
import com.toshi.view.activity.ViewUserActivity;
import com.toshi.view.fragment.DialogFragment.PaymentConfirmationDialog;
import com.toshi.view.fragment.DialogFragment.PaymentConfirmationType;

import rx.Single;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class QrCodeHandler implements PaymentConfirmationDialog.OnPaymentConfirmationListener {

    public interface QrCodeHandlerListener {
        void onInvalidQrCode();
    }

    private static final String WEB_SIGNIN = "web-signin:";

    private CompositeSubscription subscriptions;
    private AppCompatActivity activity;
    private QrCodeHandlerListener listener;

    public QrCodeHandler(final AppCompatActivity activity) {
        this.activity = activity;
        this.subscriptions = new CompositeSubscription();
    }

    public void setOnQrCodeHandlerListener(final QrCodeHandlerListener listener) {
        this.listener = listener;
    }

    public void handleResult(final String result) {
        final QrCode qrCode = new QrCode(result);
        final @QrCodeType.Type int qrCodeType = qrCode.getQrCodeType();

        if (qrCodeType == QrCodeType.EXTERNAL_PAY) {
            handleExternalPayment(qrCode);
        } else if (qrCodeType == QrCodeType.ADD) {
            handleAddQrCode(qrCode);
        } else if (qrCodeType == QrCodeType.PAY) {
            handlePaymentQrCode(qrCode);
        } else if (qrCodeType == QrCodeType.PAYMENT_ADDRESS) {
            handlePaymentAddressQrCode(qrCode);
        } else if (result.startsWith(WEB_SIGNIN)) {
            handleWebLogin(result);
        } else {
            handleInvalidQrCode();
        }
    }

    private void handleExternalPayment(final QrCode qrCode)  {
        try {
            final QrCodePayment payment = qrCode.getExternalPayment();
            final Subscription sub =
                    getUserFromPaymentAddress(payment.getAddress())
                    .subscribe(
                            user -> showToshiPaymentConfirmationDialog(user.getToshiId(), payment),
                            __ -> showExternalPaymentConfirmationDialog(payment)
                    );

            this.subscriptions.add(sub);
        } catch (InvalidQrCodePayment e) {
            handleInvalidQrCode();
        }
    }

    private Single<User> getUserFromPaymentAddress(final String paymentAddress) {
        return BaseApplication
                .get()
                .getRecipientManager()
                .getUserFromPaymentAddress(paymentAddress)
                .observeOn(AndroidSchedulers.mainThread());
    }

    private void showToshiPaymentConfirmationDialog(final String toshiId, final QrCodePayment payment) {
        if (this.activity == null) return;
        try {
            final PaymentConfirmationDialog dialog =
                    PaymentConfirmationDialog
                            .newInstanceToshiPayment(
                                    toshiId,
                                    payment.getValue(),
                                    payment.getMemo()
                            );
            dialog.show(this.activity.getSupportFragmentManager(), PaymentConfirmationDialog.TAG);
            dialog.setOnPaymentConfirmationListener(this);
        } catch (InvalidQrCodePayment e) {
            handleInvalidQrCode();
        }
    }

    private void showExternalPaymentConfirmationDialog(final QrCodePayment payment) {
        if (this.activity == null) return;
        try {
            final PaymentConfirmationDialog dialog =
                    PaymentConfirmationDialog
                            .newInstanceExternalPayment(
                                    payment.getAddress(),
                                    payment.getValue(),
                                    payment.getMemo()
                            );
            dialog.show(this.activity.getSupportFragmentManager(), PaymentConfirmationDialog.TAG);
            dialog.setOnPaymentConfirmationListener(this);
        } catch (InvalidQrCodePayment e) {
            handleInvalidQrCode();
        }
    }

    private void handleAddQrCode(final QrCode qrCode) {
        try {
            final Subscription sub =
                    getUserByUsername(qrCode.getUsername())
                    .doOnSuccess(__ -> playScanSound())
                    .subscribe(
                            this::handleUserToAdd,
                            __ -> handleInvalidQrCode()
                    );

            this.subscriptions.add(sub);
        } catch (InvalidQrCode e) {
            handleInvalidQrCode();
        }
    }

    private void handleUserToAdd(final User user) {
        if (user == null) {
            handleInvalidQrCode();
            return;
        }

        goToProfileView(user.getToshiId());
    }

    private void goToProfileView(final String toshiId) {
        if (this.activity == null) return;
        final Intent intent = new Intent(this.activity, ViewUserActivity.class)
                .putExtra(ViewUserActivity.EXTRA__USER_ADDRESS, toshiId)
                .putExtra(ViewUserActivity.EXTRA__PLAY_SCAN_SOUNDS, true);
        this.activity.startActivity(intent);
        this.activity.finish();
    }

    private void handlePaymentQrCode(final QrCode qrCode) {
        try {
            final QrCodePayment payment = qrCode.getToshiPayment();
            final Subscription sub =
                    getUserByUsername(payment.getUsername())
                    .doOnSuccess(__ -> playScanSound())
                    .subscribe(
                            user -> showToshiPaymentConfirmationDialog(user.getToshiId(), payment),
                            __ -> handleInvalidQrCode()
                    );

            this.subscriptions.add(sub);
        } catch (InvalidQrCodePayment e) {
            handleInvalidQrCode();
        }
    }

    private Single<User> getUserByUsername(final String username) {
        return BaseApplication
                .get()
                .getRecipientManager()
                .getUserFromUsername(username)
                .observeOn(AndroidSchedulers.mainThread());
    }

    private void handlePaymentAddressQrCode(final QrCode qrCode) {
        if (this.activity == null) return;
        final ClipboardManager clipboard = (ClipboardManager) this.activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newPlainText(this.activity.getString(R.string.payment_address), qrCode.getPayloadAsAddress().getHexAddress());
        clipboard.setPrimaryClip(clip);

        Toast.makeText(
                this.activity,
                this.activity.getString(R.string.copied_payment_address_to_clipboard, qrCode.getPayload()),
                Toast.LENGTH_LONG
        ).show();

        this.activity.finish();
    }

    private void handleWebLogin(final String result) {
        final String token = result.substring(WEB_SIGNIN.length());
        final Subscription sub =
                loginWithToken(token)
                .doOnSuccess(__ -> playScanSound())
                .subscribe(
                        __ -> handleLoginSuccess(),
                        this::handleLoginFailure
                );

        this.subscriptions.add(sub);
    }

    private Single<Void> loginWithToken(final String token) {
        return BaseApplication
                .get()
                .getUserManager()
                .webLogin(token)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private void playScanSound() {
        SoundManager.getInstance().playSound(SoundManager.SCAN);
    }

    private void handleLoginSuccess() {
        Toast.makeText(BaseApplication.get(), R.string.web_signin, Toast.LENGTH_LONG).show();
        if (this.activity == null) return;
        this.activity.finish();
    }

    private void handleLoginFailure(final Throwable throwable) {
        LogUtil.exception(getClass(), "Login failure", throwable);
        Toast.makeText(BaseApplication.get(), R.string.error__web_signin, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPaymentRejected(final Bundle bundle) {
        this.activity.finish();
    }

    @Override
    public void onPaymentApproved(final Bundle bundle) {
        final @PaymentConfirmationType.Type int type = (bundle.getInt(PaymentConfirmationDialog.CONFIRMATION_TYPE));

        final Payment payment = createPayment(bundle);
        if (type == PaymentConfirmationType.EXTERNAL) {
            handleExternalPayment(payment);
            return;
        }

        if (type == PaymentConfirmationType.TOSHI) {
            handleToshiPayment(bundle, payment);
            return;
        }

        LogUtil.i(getClass(), "Unhandled type in onPaymentApproved.");
    }

    private Payment createPayment(final Bundle bundle) {
        final String value = bundle.getString(PaymentConfirmationDialog.ETH_AMOUNT);
        final String paymentAddress = bundle.getString(PaymentConfirmationDialog.PAYMENT_ADDRESS);
        return new Payment()
                .setValue(value)
                .setToAddress(paymentAddress);
    }

    private void handleExternalPayment(final Payment payment) {
        try {
            sendExternalPayment(payment);
        } catch (InvalidQrCodePayment invalidQrCodePayment) {
            handleInvalidQrCode();
        }
    }
    private void sendExternalPayment(final Payment payment) throws InvalidQrCodePayment {
        BaseApplication
                .get()
                .getTransactionManager()
                .sendExternalPayment(payment.getToAddress(), payment.getValue());
    }

    private void handleToshiPayment(final Bundle bundle, final Payment payment) {
        try {
            final String toshiId = bundle.getString(PaymentConfirmationDialog.TOSHI_ID);
            goToChatActivityWithPayment(toshiId, payment);
        } catch (final InvalidQrCodePayment ex) {
            handleInvalidQrCode();
        }
    }

    private void goToChatActivityWithPayment(final String toshiId, final Payment payment) throws InvalidQrCodePayment {
        final Intent intent = new Intent(this.activity, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA__THREAD_ID, toshiId)
                .putExtra(ChatActivity.EXTRA__PAYMENT_ACTION, PaymentType.TYPE_SEND)
                .putExtra(ChatActivity.EXTRA__ETH_AMOUNT, payment.getValue())
                .putExtra(ChatActivity.EXTRA__PLAY_SCAN_SOUNDS, true);

        this.activity.startActivity(intent);
        this.activity.finish();
    }

    private void handleInvalidQrCode() {
        SoundManager.getInstance().playSound(SoundManager.SCAN_ERROR);
        this.listener.onInvalidQrCode();
    }

    public void clear() {
        this.subscriptions.clear();
        this.activity = null;
    }
}
