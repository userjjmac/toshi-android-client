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


import com.toshi.model.local.Contact;
import com.toshi.model.local.User;
import com.toshi.view.BaseApplication;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import rx.Completable;
import rx.Single;
import rx.schedulers.Schedulers;

public class ContactStore {

    public Single<Boolean> userIsAContact(final User user) {
        return Single.fromCallable(() -> {
            final Realm realm = BaseApplication.get().getRealm();
            final boolean result = realm
                    .where(Contact.class)
                    .equalTo("owner_address", user.getToshiId())
                    .findFirst() != null;
            realm.close();
            return result;
        })
        .subscribeOn(Schedulers.io());

    }

    public Completable save(final User user) {
        return Completable.fromAction(() -> {
            final Realm realm = BaseApplication.get().getRealm();
            realm.beginTransaction();
            final User storedUser = realm.copyToRealmOrUpdate(user);
            final Contact contact = new Contact(storedUser);
            realm.insert(contact);
            realm.commitTransaction();
            realm.close();
        })
        .subscribeOn(Schedulers.io());
    }

    public Completable delete(final User user) {
        return Completable.fromAction(() -> {
            final Realm realm = BaseApplication.get().getRealm();
            realm.beginTransaction();
            realm
                    .where(Contact.class)
                    .equalTo("owner_address", user.getToshiId())
                    .findFirst()
                    .deleteFromRealm();
            realm.commitTransaction();
            realm.close();
        })
        .subscribeOn(Schedulers.io());

    }

    public Single<List<Contact>> loadAll() {
        return Single.fromCallable(() -> {
            final Realm realm = BaseApplication.get().getRealm();
            final RealmQuery<Contact> query = realm.where(Contact.class);
            final RealmResults<Contact> results = query.findAll();
            final List<Contact> allContacts = realm.copyFromRealm(results.sort("user.name"));
            realm.close();
            return allContacts;
        })
        .subscribeOn(Schedulers.io());

    }
}
