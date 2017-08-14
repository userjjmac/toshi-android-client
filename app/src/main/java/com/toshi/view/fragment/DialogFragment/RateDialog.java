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

import android.app.Dialog;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import com.toshi.R;
import com.toshi.databinding.FragmentRateBinding;
import com.toshi.view.BaseApplication;

public class RateDialog extends DialogFragment {

    public static final String TAG = "RateDialog";

    private FragmentRateBinding binding;
    private int rating;
    public OnRateDialogClickListener listener;

    public interface OnRateDialogClickListener {
        void onRateClicked(final int rating, final String review);
    }

    public void setOnRateDialogClickListener(final OnRateDialogClickListener listener) {
        this.listener = listener;
    }

    public static RateDialog newInstance() {
        return new RateDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@NonNull Bundle state) {
        final Dialog dialog = super.onCreateDialog(state);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment_rate, container, false);
        initView();
        initClickListeners();
        return this.binding.getRoot();
    }

    private void initView() {
        final String title = BaseApplication.get().getString(R.string.rate_this_user);
        this.binding.title.setText(title);
    }

    private void initClickListeners() {
        this.binding.ratingView.setOnItemClickListener(rating -> this.rating = rating);
        this.binding.review.setOnClickListener(v -> {
            final String review = this.binding.reviewInput.getText().toString();
            this.listener.onRateClicked(this.rating, review);
            dismiss();
        });
        this.binding.noThanks.setOnClickListener(v -> dismiss());
    }
}
