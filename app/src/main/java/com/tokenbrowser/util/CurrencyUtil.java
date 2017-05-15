/*
 * 	Copyright (c) 2017. Token Browser, Inc
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

package com.tokenbrowser.util;

import java.text.NumberFormat;
import java.util.Currency;

public class CurrencyUtil {

    public static NumberFormat getNumberFormat(final String currencyCode) {
        final NumberFormat numberFormat = NumberFormat.getCurrencyInstance(LocaleUtil.getLocale());
        final Currency currency = Currency.getInstance(currencyCode);
        numberFormat.setCurrency(currency);
        return numberFormat;
    }

    public static String getCurrencyFromLocale() {
        final Currency currency = Currency.getInstance(LocaleUtil.getLocale());
        return currency.getCurrencyCode();
    }

    public static String getSymbolFromCurrencyCode(final String currencyCode) {
        try {
            final Currency currency = Currency.getInstance(currencyCode);
            return currency.getSymbol();
        } catch (NullPointerException | IllegalArgumentException e) {
            LogUtil.exception(CurrencyUtil.class, "Error during getting symbol from currency code", e);
            return currencyCode;
        }
    }
}
