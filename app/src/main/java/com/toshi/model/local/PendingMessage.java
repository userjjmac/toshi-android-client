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

package com.toshi.model.local;


import com.toshi.model.sofa.SofaMessage;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class PendingMessage extends RealmObject {

    @PrimaryKey
    private String privateKey;
    private Recipient receiver;
    private SofaMessage sofaMessage;

    public PendingMessage() {}

    public Recipient getReceiver() {
        return receiver;
    }

    public PendingMessage setPrivateKey(final String privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public PendingMessage setReceiver(final Recipient receiver) {
        this.receiver = receiver;
        return this;
    }

    public SofaMessage getSofaMessage() {
        return sofaMessage;
    }

    public PendingMessage setSofaMessage(final SofaMessage sofaMessage) {
        this.sofaMessage = sofaMessage;
        this.privateKey = sofaMessage.getPrivateKey();
        return this;
    }
}
