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

package com.toshi.util;

import android.graphics.Bitmap;

import com.toshi.R;
import com.toshi.exception.InvalidQrCode;
import com.toshi.exception.InvalidQrCodePayment;
import com.toshi.model.local.Address;
import com.toshi.model.local.InternalUrl;
import com.toshi.model.local.QrCodePayment;
import com.toshi.view.BaseApplication;

import rx.Single;

public class QrCode {

    private final String payload;
    private final Address asAddress;
    private final InternalUrl asUrl;

    public QrCode(final String payload) {
        this.payload = payload;
        this.asAddress = new Address(payload);
        this.asUrl = new InternalUrl(payload);
    }

    public String getPayload() {
        return this.payload;
    }

    /* package */ Address getPayloadAsAddress() {
        return this.asAddress;
    }

    /* package */ InternalUrl getPayloadAsUrl() {
        return this.asUrl;
    }

    /* package */ @QrCodeType.Type int getQrCodeType() {
        if (this.asUrl.isValid()) {
            return asUrl.getType();
        } else if (this.asAddress.isValid()) {
            return getTypeForAddress(this.asAddress);
        } else {
            return QrCodeType.INVALID;
        }
    }

    private @QrCodeType.Type int getTypeForAddress(final Address address) {
        return address.getAmount().isEmpty()
                ? QrCodeType.PAYMENT_ADDRESS
                : QrCodeType.EXTERNAL_PAY;
    }


    public String getUsername() throws InvalidQrCode {
        if (!this.asUrl.isValid()) throw new InvalidQrCode();
        return this.asUrl.getUsername();
    }

    /* package */ QrCodePayment getToshiPayment() throws InvalidQrCodePayment {
        if (!this.asUrl.isValid()) throw new InvalidQrCodePayment();
        final QrCodePayment payment = new QrCodePayment()
                .setValue(this.asUrl.getAmount())
                .setMemo(this.asUrl.getMemo())
                .setUsername(this.asUrl.getUsername());
        if (payment.isValid()) return payment;
        else throw new InvalidQrCodePayment();
    }

    /* package */ QrCodePayment getExternalPayment() throws InvalidQrCodePayment {
        if (!this.asAddress.isValid()) throw new InvalidQrCodePayment();

        return new QrCodePayment()
                .setAddress(this.asAddress.getHexAddress())
                .setValue(this.asAddress.getAmount())
                .setMemo(this.asAddress.getMemo());
    }

    public static Single<Bitmap> generateAddQrCode(final String username) {
        final String baseUrl = BaseApplication.get().getString(R.string.qr_code_base_url);
        final String addParams = getAddUrl(username);
        final String url = String.format("%s%s", baseUrl, addParams);
        return ImageUtil.generateQrCode(url);
    }

    private static String getAddUrl(final String username) {
        return BaseApplication
                .get()
                .getString(
                        R.string.qr_code_add_url,
                        username
                );
    }

    public static Single<Bitmap> generatePayQrCode(final String username,
                                                   final String value,
                                                   final String memo) {
        final String baseUrl = BaseApplication.get().getString(R.string.qr_code_base_url);
        final String payParams = memo != null
                ? getPayUrl(username, value, memo)
                : getPayUrl(username, value);
        final String url = String.format("%s%s", baseUrl, payParams);
        return ImageUtil.generateQrCode(url);
    }

    private static String getPayUrl(final String username,
                                    final String value,
                                    final String memo) {
        return BaseApplication
                .get()
                .getString(
                        R.string.qr_code_pay_url,
                        username,
                        value,
                        memo
                );
    }

    private static String getPayUrl(final String username,
                                    final String value) {
        return BaseApplication
                .get()
                .getString(
                        R.string.qr_code_pay_url_without_memo,
                        username,
                        value
                );
    }

    public static Single<Bitmap> generatePaymentAddressQrCode(final String paymentAddress) {
        final String url = String.format("ethereum:%s", paymentAddress);
        return ImageUtil.generateQrCode(url);
    }
}
