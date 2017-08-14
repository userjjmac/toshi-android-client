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

package com.toshi.manager.store;


import com.toshi.model.local.User;
import com.toshi.view.BaseApplication;

import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmQuery;
import rx.Observable;
import rx.Single;

public class UserStore {

    public Observable<User> loadForToshiId(final String toshiId) {
        return Observable.fromCallable(() -> loadWhere("owner_address", toshiId));
    }

    public User loadForPaymentAddress(final String address) {
        return loadWhere("payment_address", address);
    }

    public Single<List<User>> queryUsername(final String query) {
        return Single.fromCallable(() -> filter("username", query));
    }

    public void save(final User user) {
        final Realm realm = BaseApplication.get().getRealm();
        realm.beginTransaction();
        realm.insertOrUpdate(user);
        realm.commitTransaction();
        realm.close();
    }

    private User loadWhere(final String fieldName, final String value) {
        final Realm realm = BaseApplication.get().getRealm();
        final User user =
                realm.where(User.class)
                .equalTo(fieldName, value)
                .findFirst();

        final User queriedUser = user == null ? null : realm.copyFromRealm(user);
        realm.close();
        return queriedUser;
    }

    private List<User> filter(final String fieldName, final String value) {
        final Realm realm = BaseApplication.get().getRealm();
        final RealmQuery<User> query = realm.where(User.class);
        query.contains(fieldName, value, Case.INSENSITIVE);
        final List<User> result = realm.copyFromRealm(query.findAll());
        realm.close();
        return result;
    }
}
