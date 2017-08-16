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

package com.toshi.view.notification;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import com.toshi.R;
import com.toshi.crypto.signal.model.DecryptedSignalMessage;
import com.toshi.model.local.Recipient;
import com.toshi.model.local.User;
import com.toshi.model.sofa.SofaAdapters;
import com.toshi.model.sofa.SofaMessage;
import com.toshi.model.sofa.SofaType;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.notification.model.ChatNotification;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatNotificationManager {

    private static String currentlyOpenConversation;
    private static final Map<String, ChatNotification> activeNotifications = new HashMap<>();

    public static void suppressNotificationsForConversation(final String conversationId) {
        currentlyOpenConversation = conversationId;
        final NotificationManager manager = (NotificationManager) BaseApplication.get().getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(conversationId, 1);
        handleNotificationDismissed(conversationId);
    }

    public static void handleNotificationDismissed(final String notificationTag) {
        activeNotifications.remove(notificationTag);
    }

    public static void stopNotificationSuppression() {
        currentlyOpenConversation = null;
    }

    public static void showNotification(final DecryptedSignalMessage signalMessage) {
        if (signalMessage == null) return;

        BaseApplication
            .get()
            .getRecipientManager()
            .getUserFromTokenId(signalMessage.getSource())
            .subscribe(
                    (user) -> handleUserLookup(user, signalMessage),
                    ChatNotificationManager::handleUserError
            );
    }

    private static void handleUserLookup(final User user, final DecryptedSignalMessage signalMessage) {
        final String body = getBodyFromMessage(signalMessage);
        if (body == null) {
            // This wasn't a SOFA::Message. Do not render.
            LogUtil.i(ChatNotificationManager.class, "Not rendering PN");
            return;
        }
        final Recipient recipient = new Recipient(user);
        // Todo - pass group avatar here (if it's a group)
        showChatNotification(recipient, body);
    }

    private static void handleUserError(final Throwable throwable) {
        LogUtil.exception(ChatNotificationManager.class, "Error during fetching user", throwable);
    }

    private static String getBodyFromMessage(final DecryptedSignalMessage dsm) {
        final SofaMessage sofaMessage = new SofaMessage().makeNew(dsm.getBody());
        try {
            if (sofaMessage.getType() == SofaType.PLAIN_TEXT) {
                return SofaAdapters.get().messageFrom(sofaMessage.getPayload()).getBody();
            }
        } catch (final IOException ex) {
            // Nop
        }
        return null;
    }

    public static void showChatNotification(
            final Recipient sender,
            final String content) {

        // Sender will be null if the transaction came from outside of the Token platform.
        final String notificationKey = sender == null ? ChatNotification.DEFAULT_TAG : sender.getThreadId();

        if (notificationKey.equals(currentlyOpenConversation) && !BaseApplication.get().isInBackground()) {
            return;
        }

        final ChatNotification activeChatNotification
                = activeNotifications.get(notificationKey) != null
                ? activeNotifications.get(notificationKey)
                : new ChatNotification(sender);

        activeNotifications.put(notificationKey, activeChatNotification);

        activeChatNotification
                .addUnreadMessage(content)
                .generateLargeIcon()
                .subscribe(() -> showChatNotification(activeChatNotification));
    }

    private static void showChatNotification(final ChatNotification chatNotification) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            createNotificationChannel(chatNotification);
        }

        final int lightOnRate = 1000 * 2;
        final int lightOffRate = 1000 * 15;
        final int notificationColor = ContextCompat.getColor(BaseApplication.get(), R.color.colorPrimary);
        final Uri notificationSound = Uri.parse("android.resource://" + BaseApplication.get().getPackageName() + "/" + R.raw.notification);
        final NotificationCompat.Style style = generateNotificationStyle(chatNotification);
        final CharSequence contextText = chatNotification.getLastMessage();

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(BaseApplication.get(), chatNotification.getId())
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(chatNotification.getLargeIcon())
                .setContentTitle(chatNotification.getTitle())
                .setContentText(contextText)
                .setTicker(contextText)
                .setAutoCancel(true)
                .setSound(notificationSound)
                .setColor(notificationColor)
                .setLights(notificationColor, lightOnRate, lightOffRate)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(style)
                .setNumber(chatNotification.getNumberOfUnreadMessages())
                .setContentIntent(chatNotification.getPendingIntent())
                .setDeleteIntent(chatNotification.getDeleteIntent());

        final int maxNumberMessagesWithSound = 3;
        if (chatNotification.getNumberOfUnreadMessages() > maxNumberMessagesWithSound) {
            builder
                .setSound(null)
                .setVibrate(null);
        }

        final NotificationManager manager = (NotificationManager) BaseApplication.get().getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(chatNotification.getTag(), 1, builder.build());
    }

    @RequiresApi(api = 26)
    private static void createNotificationChannel(final ChatNotification chatNotification) {
        final int notificationColor = ContextCompat.getColor(BaseApplication.get(), R.color.colorPrimary);
        final Uri notificationSound = Uri.parse("android.resource://" + BaseApplication.get().getPackageName() + "/" + R.raw.notification);
        final CharSequence channelName = chatNotification.getTitle();

        final NotificationChannel notificationChannel = new NotificationChannel(chatNotification.getId(), channelName, NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.enableLights(true);
        notificationChannel.setLightColor(notificationColor);
        notificationChannel.enableVibration(true);
        notificationChannel.setSound(notificationSound,
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
        );

        final NotificationManager manager = (NotificationManager) BaseApplication.get().getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(notificationChannel);
    }

    private static NotificationCompat.Style generateNotificationStyle(final ChatNotification chatNotification) {
        final int numberOfUnreadMessages = chatNotification.getNumberOfUnreadMessages();

        if (numberOfUnreadMessages == 1) {
            return new NotificationCompat
                    .BigTextStyle()
                    .setBigContentTitle(chatNotification.getTitle())
                    .bigText(chatNotification.getLastMessage());
        }

        final List<String> lastFewMessages = chatNotification.getLastFewMessages();
        final NotificationCompat.Style style =
                new NotificationCompat
                        .InboxStyle()
                        .setBigContentTitle(chatNotification.getTitle());
        for (final String message : lastFewMessages) {
            ((NotificationCompat.InboxStyle) style).addLine(message);
        }
        return style;
    }
}
