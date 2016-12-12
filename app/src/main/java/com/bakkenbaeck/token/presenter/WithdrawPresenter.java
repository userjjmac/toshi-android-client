package com.bakkenbaeck.token.presenter;

import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import com.bakkenbaeck.token.R;
import com.bakkenbaeck.token.model.ActivityResultHolder;
import com.bakkenbaeck.token.model.LocalBalance;
import com.bakkenbaeck.token.model.User;
import com.bakkenbaeck.token.network.rest.TokenService;
import com.bakkenbaeck.token.network.rest.model.SignatureRequest;
import com.bakkenbaeck.token.network.rest.model.SignedWithdrawalRequest;
import com.bakkenbaeck.token.network.rest.model.TransactionSent;
import com.bakkenbaeck.token.network.rest.model.WithdrawalRequest;
import com.bakkenbaeck.token.network.ws.model.TransactionConfirmation;
import com.bakkenbaeck.token.util.EthUtil;
import com.bakkenbaeck.token.util.LocaleUtil;
import com.bakkenbaeck.token.util.LogUtil;
import com.bakkenbaeck.token.util.OnNextSubscriber;
import com.bakkenbaeck.token.util.OnSingleClickListener;
import com.bakkenbaeck.token.util.RetryWithBackoff;
import com.bakkenbaeck.token.util.SnackbarUtil;
import com.bakkenbaeck.token.view.BaseApplication;
import com.bakkenbaeck.token.view.Fragment.QrFragment;
import com.bakkenbaeck.token.view.activity.BarcodeScannerActivity;
import com.bakkenbaeck.token.view.activity.WithdrawActivity;
import com.bakkenbaeck.token.view.adapter.PreviousWalletAddress;
import com.bakkenbaeck.token.view.dialog.ProgressDialog;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.ParseException;

import retrofit2.Response;
import rx.Subscriber;

import static android.app.Activity.RESULT_OK;

public class WithdrawPresenter implements Presenter<WithdrawActivity>, QrFragment.OnFragmentClosed {
    static final String INTENT_WALLET_ADDRESS = "wallet_address";
    static final String INTENT_WITHDRAW_AMOUNT = "withdraw_amount";

    private WithdrawActivity activity;
    private User currentUser;
    private boolean firstTimeAttaching = true;
    private BigDecimal currentBalance = BigDecimal.ZERO;
    private ProgressDialog progressDialog;
    private final BigDecimal minWithdrawLimit = new BigDecimal("0.0000000001");
    private OnNextSubscriber<LocalBalance> newBalanceSubscriber;
    private OnNextSubscriber<User> userSubscriber;
    private OnNextSubscriber<Integer> newReputationSubscriber;

    private final PreviousWalletAddress previousWalletAddress = new PreviousWalletAddress();

    @Override
    public void onViewAttached(final WithdrawActivity activity) {
        this.activity = activity;
        initButtons();
        initToolbar();
        initPreviousAddress();
        registerObservables();

        if (firstTimeAttaching) {
            firstTimeAttaching = false;
            initProgressDialog();
        }
    }

    private void initProgressDialog() {
        progressDialog = ProgressDialog.newInstance();
    }

    private void initButtons() {
        this.activity.getBinding().barcodeButton.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(final View v) {
                showBarcodeActivity();
            }
        });

        this.activity.getBinding().balanceBar.setOnBalanceClicked(new View.OnClickListener() {

            @Override
            public void onClick(final View v) {
                showQrFragment();
            }
        });

        reEnableDialogListeners();

        this.activity.getBinding().maxButton.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(View v) {
                BaseApplication.get().getLocalBalanceManager().getObservable().subscribe(new OnNextSubscriber<LocalBalance>() {
                    @Override
                    public void onNext(final LocalBalance localBalance) {
                        this.unsubscribe();
                        tryPopulateAmountField(localBalance);
                    }
                });
            }
        });

        this.activity.getBinding().sendButton.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(final View view) {
                handleSendClicked();
            }
        });

        this.activity.getBinding().walletAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence charSequence, final int i, final int i1, final int i2) {}

            @Override
            public void onTextChanged(final CharSequence charSequence, final int i, final int i1, final int i2) {
                updateSendButtonEnabledState();
            }

            @Override
            public void afterTextChanged(final Editable editable) {}
        });

        this.activity.getBinding().amount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonEnabledState();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        //refreshButtonStates();
    }

    private void updateSendButtonEnabledState() {
        final Editable walletAddress = this.activity.getBinding().walletAddress.getText();
        String amount = this.activity.getBinding().amount.getText().toString();
        final boolean shouldEnableButton = walletAddress.length() > 0 && userHasEnoughReputationScore() && amount.length() > 0;
        enableSendButton(shouldEnableButton);
        activity.getBinding().sendButton.setEnabled(shouldEnableButton);
    }

    private void enableSendButton(boolean enabled) {
        if (enabled) {
            activity.getBinding().sendButton.setTextColor(Color.parseColor("#FFFFFF"));
            activity.getBinding().sendButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.btn_with_radius));
        } else {
            activity.getBinding().sendButton.setTextColor(Color.parseColor("#33565A64"));
            activity.getBinding().sendButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.disabled_background));
        }
    }

    private void initToolbar() {
        final String title = this.activity.getResources().getString(R.string.withdraw__title);
        final Toolbar toolbar = this.activity.getBinding().toolbar;
        this.activity.setSupportActionBar(toolbar);
        this.activity.getSupportActionBar().setTitle(title);
        this.activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void initPreviousAddress() {
        final EditText walletAddress = this.activity.getBinding().walletAddress;
        walletAddress.setText(this.previousWalletAddress.getAddress());
        walletAddress.setSelection(walletAddress.getText().length());
    }

    private OnNextSubscriber<LocalBalance> generateNewBalanceSubscriber() {
        return new OnNextSubscriber<LocalBalance>() {
            @Override
            public void onNext(final LocalBalance newBalance) {
                if (activity != null && newBalance != null) {
                    currentBalance = newBalance.getConfirmedBalanceAsEthMinusTransferFee();
                    activity.getBinding().balanceBar.setEthValue(newBalance.getEthValue(), newBalance.getUnconfirmedBalanceAsEth());
                    activity.getBinding().balanceBar.setBalance(newBalance);
                }
            }
        };
    }

    private OnNextSubscriber<Integer> generateNewReputationSubscriber() {
        return new OnNextSubscriber<Integer>() {
            @Override
            public void onNext(Integer reputationScore) {
                if (activity != null) {
                    activity.getBinding().balanceBar.setReputation(reputationScore);
                }
            }
        };
    }

    private void tryPopulateAmountField(final LocalBalance localBalance) {

        if(localBalance.getConfirmedBalanceAsEthMinusTransferFee().equals(BigDecimal.ZERO) || localBalance.getUnconfirmedBalanceAsEthMinusTransferFee().equals(BigDecimal.ZERO)) {
            SnackbarUtil.make(activity.getBinding().root, this.activity.getString(R.string.balanceErrorLessTxFee)).show();
            return;
        }

        final String amount = generateMaxAmountFromBalance(localBalance);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if(activity != null) {
                    activity.getBinding().amount.setText(amount);
                    activity.getBinding().amount.setSelection(activity.getBinding().amount.getText().length());
                }
            }
        });
    }

    private String generateMaxAmountFromBalance(final LocalBalance localBalance) {
        final BigInteger unconfirmedBalance = localBalance.getUnconfirmedBalance();
        final BigInteger confirmedBalance = localBalance.getConfirmedBalance();
        if (unconfirmedBalance.compareTo(confirmedBalance) == -1) {
            return localBalance.unconfirmedBalanceStringMinusTransferFee();
        } else if(confirmedBalance.compareTo(unconfirmedBalance) == -1) {
            return localBalance.confirmedBalanceStringMinusTransferFee();
        }
        return localBalance.confirmedBalanceStringMinusTransferFee();
    }

    @Override
    public void onViewDetached() {
        String walletAddress = this.activity.getBinding().walletAddress.getText().toString();
        previousWalletAddress.setAddress(walletAddress);
        unregisterObservables();
        this.activity = null;
    }

    @Override
    public void onViewDestroyed() {
        this.activity = null;
    }

    public void handleActivityResult(final ActivityResultHolder activityResultHolder) {
        if (activityResultHolder.getResultCode() != RESULT_OK) {
            return;
        }

        tryParseBarcodeResult(activityResultHolder);
    }

    private void showBarcodeActivity() {
        new IntentIntegrator(this.activity)
                .setCaptureActivity(BarcodeScannerActivity.class)
                .setOrientationLocked(true)
                .setPrompt("")
                .setBeepEnabled(true)
                .initiateScan();
    }

    private void tryParseBarcodeResult(final ActivityResultHolder activityResultHolder) {
        final IntentResult result = IntentIntegrator.parseActivityResult(
                activityResultHolder.getRequestCode(),
                activityResultHolder.getResultCode(),
                activityResultHolder.getIntent());
        if(result == null || result.getContents() == null || result.getContents().length() <= 0) {
            return;
        }

        this.activity.getBinding().walletAddress.setText(result.getContents().replaceFirst("ethereum:", ""));
    }

    private void handleSendClicked() {
        if (!validate()) {
            return;
        }

        try {
            final DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(LocaleUtil.getLocale());
            nf.setParseBigDecimal(true);
            final String inputtedText = this.activity.getBinding().amount.getText().toString();

            final BigDecimal amountInEth = (BigDecimal) nf.parse(inputtedText);

            final BigInteger amountInWei = EthUtil.ethToWei(amountInEth);
            final String toAddress = this.activity.getBinding().walletAddress.getText().toString();
            final WithdrawalRequest withdrawalRequest = new WithdrawalRequest(amountInWei, toAddress);

            TokenService.getApi()
                    .postWithdrawalRequest(withdrawalRequest)
                    .subscribe(generateSigningSubscriber());
            progressDialog.show(this.activity.getSupportFragmentManager(), "progressDialog");
            this.previousWalletAddress.setAddress(toAddress);
        } catch (final ParseException ex) {
            LogUtil.e(getClass(), ex.toString());
        }
    }

    private Subscriber<Response<SignatureRequest>> generateSigningSubscriber() {
        return new Subscriber<Response<SignatureRequest>>() {
            @Override
            public void onCompleted() {}

            @Override
            public void onError(final Throwable ex) {
                if(ex instanceof UnknownHostException) {
                    SnackbarUtil.make(activity.getBinding().root, activity.getString(R.string.networkStateNotConnected)).show();
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                    }
                });
                LogUtil.e(getClass(), "postWithdrawalRequest: " + ex);
            }

            @Override
            public void onNext(final Response<SignatureRequest> signatureRequest) {
                if(signatureRequest.code() == 400 || signatureRequest.code() == 500) {
                    if (activity != null) {
                        showAddressError(activity.getString(R.string.invalidEthAddress));
                    }
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                        }
                    });
                }else if(signatureRequest.code() == 200) {

                    final String unsignedTransaction = signatureRequest.body().getTransaction();
                    final String signature = BaseApplication.get().getUserManager().signTransaction(unsignedTransaction);
                    final SignedWithdrawalRequest request = new SignedWithdrawalRequest(unsignedTransaction, signature);

                    TokenService.getApi()
                            .postSignedWithdrawal(request)
                            .retryWhen(new RetryWithBackoff(5))
                            .subscribe(generateSignedWithdrawalSubscriber());
                }
            }

            private Subscriber<Response<TransactionSent>> generateSignedWithdrawalSubscriber() {
                return new Subscriber<Response<TransactionSent>>() {
                    @Override
                    public void onCompleted() {}

                    @Override
                    public void onError(final Throwable ex) {
                        if(ex instanceof UnknownHostException){
                            SnackbarUtil.make(activity.getBinding().root, activity.getString(R.string.networkStateNotConnected)).show();
                        }

                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                            }
                        });
                        LogUtil.e(getClass(), "postSignedWithdrawal: " + ex);
                    }

                    @Override
                    public void onNext(final Response<TransactionSent> transactionSent) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if(progressDialog != null) {
                                    progressDialog.dismiss();
                                }
                            }
                        });

                        if(transactionSent.code() == 400) {
                            if(activity != null) {
                                showBalanceError(activity.getString(R.string.notEnoughFunds));
                            }
                            return;
                        }

                        BigInteger t1 = transactionSent.body().getConfirmedBalance();
                        BigInteger t2 = transactionSent.body().getUnconfirmedBalance();
                        TransactionConfirmation t = new TransactionConfirmation(t1.toString(), t2.toString());
                        BaseApplication.get().getSocketObservables().emitTransactionConfirmation(t);
                        BaseApplication.get().getSocketObservables().emitTransactionSent(transactionSent.body());

                        final DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(LocaleUtil.getLocale());
                        nf.setParseBigDecimal(true);
                        String parsedInput = activity.getBinding().amount.getText().toString();
                        final BigDecimal amountInEth;

                        try {
                            amountInEth = (BigDecimal) nf.parse(parsedInput);
                        }catch (ParseException e){
                            LogUtil.e(getClass(), e.toString());
                            return;
                        }

                        final Intent intent = new Intent();
                        intent.putExtra(INTENT_WALLET_ADDRESS, activity.getBinding().walletAddress.getText().toString());
                        intent.putExtra(INTENT_WITHDRAW_AMOUNT, amountInEth);

                        activity.setResult(RESULT_OK, intent);
                        activity.finish();
                        activity.overridePendingTransition(R.anim.enter_fade_in, R.anim.exit_fade_out);
                    }
                };
            }
        };
    }

    private void showAddressError(final String errorMessage) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                final EditText address = activity.getBinding().walletAddress;
                address.requestFocus();
                address.setError(errorMessage, null);
            }
        });
    }

    private void showBalanceError(final String errorMessage) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                final EditText amount = activity.getBinding().amount;
                amount.requestFocus();
                amount.setError(errorMessage, null);
            }
        });
    }

    private boolean validate() {
        BigDecimal amountRequested = BigDecimal.ZERO;

        try {
            final DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(LocaleUtil.getLocale());
            nf.setParseBigDecimal(true);
            final String inputtedText = this.activity.getBinding().amount.getText().toString();
            amountRequested = (BigDecimal) nf.parse(inputtedText);

            if (amountRequested.compareTo(minWithdrawLimit) > 0 && amountRequested.compareTo(this.currentBalance) <= 0) {
                return true;
            }
        } catch (final NumberFormatException | ParseException ex) {
            LogUtil.e(getClass(), ex.toString());
            showBalanceError(this.activity.getResources().getString(R.string.withdraw__amount_error));
        }

        if (this.currentBalance.compareTo(BigDecimal.ZERO) == 0) {
            showBalanceError(this.activity.getResources().getString(R.string.withdraw__amount_error_zero));
        } else if(amountRequested != null && amountRequested.compareTo(currentBalance) == 1) {
            showBalanceError(this.activity.getResources().getString(R.string.withdraw__amount_error_bigger));
        } else {
            showBalanceError(this.activity.getResources().getString(R.string.withdraw__amount_error));
        }

        return false;
    }

    private boolean userHasEnoughReputationScore() {
        // Todo: Reputation required for withdrawal should be dictated by the server
        if (currentUser == null || currentUser.getLevel() == 0) {
            return false;
        }
        return true;
    }

    private void registerObservables() {
        this.newBalanceSubscriber = generateNewBalanceSubscriber();
        this.userSubscriber = generateUserSubscriber();
        this.newReputationSubscriber = generateNewReputationSubscriber();

        BaseApplication.get().getLocalBalanceManager().getObservable().subscribe(this.newBalanceSubscriber);
        BaseApplication.get().getUserManager().getObservable().subscribe(this.userSubscriber);
        BaseApplication.get().getLocalBalanceManager().getLevelObservable().subscribe(this.newReputationSubscriber);
    }

    private void unregisterObservables() {
        this.newBalanceSubscriber.unsubscribe();
        this.userSubscriber.unsubscribe();
        this.newReputationSubscriber.unsubscribe();

        this.newBalanceSubscriber = null;
        this.userSubscriber = null;
        this.newReputationSubscriber = null;
    }

    private OnNextSubscriber<User> generateUserSubscriber() {
        return new OnNextSubscriber<User>() {
            @Override
            public void onNext(final User user) {
                currentUser = user;
                refreshButtonStates();
                this.onCompleted();
            }
        };
    }

    private void refreshButtonStates() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (activity == null) {
                    return;
                }
                updateSendButtonEnabledState();
            }
        });
    }

    private void showQrFragment() {
        FragmentManager fm = this.activity.getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.setCustomAnimations(R.anim.enter_fade_in, R.anim.exit_fade_out);
        QrFragment qrFragment = QrFragment.newInstance();
        ft.add(R.id.fragmentRoot, qrFragment, QrFragment.TAG).addToBackStack(QrFragment.TAG).commit();
        qrFragment.setOnFragmentClosed(this);
    }

    public void removeQrFragment() {
        FragmentManager fm = this.activity.getSupportFragmentManager();
        Fragment qrFragment = fm.findFragmentByTag(QrFragment.TAG);

        if(qrFragment != null) {
            FragmentTransaction ft = fm.beginTransaction();
            ft.setCustomAnimations(R.anim.enter_fade_in, R.anim.exit_fade_out);
            ft.remove(qrFragment).commit();
            fm.popBackStackImmediate(QrFragment.TAG,  FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    private void reEnableDialogListeners() {
        QrFragment durationDialog = (QrFragment) this.activity.getSupportFragmentManager().findFragmentByTag(QrFragment.TAG);
        if(durationDialog != null) {
            durationDialog.setOnFragmentClosed(this);
        }
    }

    @Override
    public void onClose() {
        removeQrFragment();
    }
}
