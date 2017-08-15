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


import com.toshi.model.local.PendingMessage;
import com.toshi.model.local.Recipient;
import com.toshi.model.sofa.SofaMessage;
import com.toshi.view.BaseApplication;

import io.realm.Realm;

public class PendingMessageStore {

    public void save(final Recipient receiver, final SofaMessage message) {
        final PendingMessage pendingMessage = new PendingMessage()
                .setPrivateKey(message.getPrivateKey())
                .setReceiver(receiver)
                .setSofaMessage(message);

        final Realm realm = BaseApplication.get().getRealm();
        realm.beginTransaction();
        realm.insertOrUpdate(pendingMessage);
        realm.commitTransaction();
        realm.close();
    }

    public PendingMessage fetchPendingMessage(final SofaMessage sofaMessage) {
        final Realm realm = BaseApplication.get().getRealm();
        final PendingMessage result = realm
                .where(PendingMessage.class)
                .equalTo("privateKey", sofaMessage.getPrivateKey())
                .findFirst();

        if (result == null) {
            realm.close();
            return null;
        }

        final PendingMessage pendingMessage = realm.copyFromRealm(result);

        realm.beginTransaction();
        result.deleteFromRealm();
        realm.commitTransaction();
        realm.close();

        return pendingMessage;
    }
}
