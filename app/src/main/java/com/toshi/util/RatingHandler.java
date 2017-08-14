package com.toshi.util;

import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.toshi.R;
import com.toshi.model.local.Review;
import com.toshi.model.local.User;
import com.toshi.model.network.ServerTime;
import com.toshi.view.BaseApplication;
import com.toshi.view.fragment.DialogFragment.RateDialog;

import retrofit2.Response;
import rx.Single;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public class RatingHandler {

    private AppCompatActivity activity;
    private CompositeSubscription subscriptions;

    private RateDialog rateDialog;
    private User user;

    public RatingHandler(final AppCompatActivity activity) {
        this.activity = activity;
        this.subscriptions = new CompositeSubscription();
    }

    public void rateUser(final User user) {
        if (user == null) return;
        this.user = user;
        this.rateDialog = RateDialog.newInstance(this.user.getUsername());
        this.rateDialog.setOnRateDialogClickListener(this::onRateClicked);
        this.rateDialog.show(this.activity.getSupportFragmentManager(), RateDialog.TAG);
    }

    private void onRateClicked(final int rating, final String reviewText) {
        final Review review = new Review()
                .setRating(rating)
                .setReview(reviewText)
                .setReviewee(this.user.getToshiId());

        final Subscription sub =
                submitReview(review)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        __ -> handleSubmitSuccess(),
                        this::handleSubmitError
                );

        this.subscriptions.add(sub);
    }

    private Single<Response<Void>> submitReview(final Review review) {
        return BaseApplication
                .get()
                .getRecipientManager()
                .getTimestamp()
                .flatMap(serverTime -> submitReview(review, serverTime));
    }

    private Single<Response<Void>> submitReview(final Review review, final ServerTime serverTime) {
        return BaseApplication.get()
                .getReputationManager()
                .submitReview(review, serverTime.get());
    }

    private void handleSubmitSuccess() {
        if (this.activity == null) return;
        Toast.makeText(
                this.activity,
                this.activity.getString(R.string.review_submitted),
                Toast.LENGTH_SHORT
        ).show();
    }

    private void handleSubmitError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error when sending review", throwable);
    }

    public void clear() {
        closeDialog();
        this.subscriptions.clear();
        this.subscriptions = null;
        this.activity = null;
    }

    private void closeDialog() {
        if (this.rateDialog != null) {
            this.rateDialog.dismissAllowingStateLoss();
            this.rateDialog = null;
        }
    }
}
