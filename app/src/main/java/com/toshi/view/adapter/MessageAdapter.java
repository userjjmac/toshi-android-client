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

package com.toshi.view.adapter;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.toshi.R;
import com.toshi.model.local.ChainPosition;
import com.toshi.model.local.Recipient;
import com.toshi.model.local.User;
import com.toshi.model.sofa.Message;
import com.toshi.model.sofa.Payment;
import com.toshi.model.sofa.PaymentRequest;
import com.toshi.model.sofa.SofaAdapters;
import com.toshi.model.sofa.SofaMessage;
import com.toshi.model.sofa.SofaType;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.adapter.listeners.OnItemClickListener;
import com.toshi.view.adapter.viewholder.FileViewHolder;
import com.toshi.view.adapter.viewholder.ImageViewHolder;
import com.toshi.view.adapter.viewholder.PaymentRequestViewHolder;
import com.toshi.view.adapter.viewholder.PaymentViewHolder;
import com.toshi.view.adapter.viewholder.TextViewHolder;
import com.toshi.view.adapter.viewholder.TimestampMessageViewHolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.toshi.model.local.ChainPosition.FIRST;
import static com.toshi.model.local.ChainPosition.LAST;
import static com.toshi.model.local.ChainPosition.MIDDLE;
import static com.toshi.model.local.ChainPosition.NONE;


public final class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final static int SENDER_MASK = 0x1000;

    private final List<SofaMessage> sofaMessages;
    private OnItemClickListener<SofaMessage> onPaymentRequestApproveListener;
    private OnItemClickListener<SofaMessage> onPaymentRequestRejectListener;
    private OnItemClickListener<String> onUsernameClickListener;
    private OnItemClickListener<String> onImageClickListener;
    private OnItemClickListener<String> onFileClickListener;
    private OnItemClickListener<SofaMessage> onResendListener;
    private Recipient recipient;

    public MessageAdapter() {
        this.sofaMessages = new ArrayList<>();
    }

    public final MessageAdapter addOnPaymentRequestApproveListener(final OnItemClickListener<SofaMessage> listener) {
        this.onPaymentRequestApproveListener = listener;
        return this;
    }

    public final MessageAdapter addOnPaymentRequestRejectListener(final OnItemClickListener<SofaMessage> listener) {
        this.onPaymentRequestRejectListener = listener;
        return this;
    }

    public final MessageAdapter addOnUsernameClickListener(final OnItemClickListener<String> listener) {
        this.onUsernameClickListener = listener;
        return this;
    }

    public final MessageAdapter addOnImageClickListener(final OnItemClickListener<String> listener) {
        this.onImageClickListener = listener;
        return this;
    }

    public final MessageAdapter addOnFileClickListener(final OnItemClickListener<String> listener) {
        this.onFileClickListener = listener;
        return this;
    }

    public final MessageAdapter addOnResendListener(final OnItemClickListener<SofaMessage> listener) {
        this.onResendListener = listener;
        return this;
    }

    private boolean shouldShowChatMessage(final SofaMessage sofaMessage) {
        return sofaMessage.getType() != SofaType.UNKNOWN
                && sofaMessage.getType() != SofaType.INIT
                && sofaMessage.getType() != SofaType.INIT_REQUEST;
    }

    public MessageAdapter setMessages(final List<SofaMessage> messages) {
        final List<SofaMessage> messagesToAdd = messages == null
                ? new ArrayList<>(0)
                : messages;
        addMessages(messagesToAdd);
        return this;
    }

    public MessageAdapter setRecipient(final Recipient recipient) {
        this.recipient = recipient;
        return this;
    }

    private void addMessages(final Collection<SofaMessage> sofaMessages) {
        this.sofaMessages.clear();
        notifyDataSetChanged();

        for (SofaMessage sofaMessage : sofaMessages) {
            addMessage(sofaMessage);
        }
    }

    private void addMessage(final SofaMessage sofaMessage) {
        if (!shouldShowChatMessage(sofaMessage)) return;
        this.sofaMessages.add(sofaMessage);
        notifyItemInserted(this.sofaMessages.size() - 1);
        if (this.sofaMessages.size() > 1) {
            // Update the previous message as well.
            notifyItemChanged(this.sofaMessages.size() - 2);
        }
    }

    public final void updateMessage(final SofaMessage sofaMessage) {
        if (!shouldShowChatMessage(sofaMessage)) return;
        final int position = this.sofaMessages.indexOf(sofaMessage);
        if (position == -1) {
            addMessage(sofaMessage);
            return;
        }

        this.sofaMessages.set(position, sofaMessage);
        notifyItemChanged(position);
    }

    @Override
    public int getItemViewType(final int position) {
        final SofaMessage sofaMessage = this.sofaMessages.get(position);
        final @SofaType.Type int sofaType = sofaMessage.hasAttachment() ? sofaMessage.getAttachmentType() : sofaMessage.getType();
        return sofaMessage.isSentBy(getCurrentLocalUser()) ? sofaType : sofaType | SENDER_MASK;
    }

    @Override
    public final RecyclerView.ViewHolder onCreateViewHolder(
            final ViewGroup parent,
            final int viewType) {

        final boolean isRemote = viewType >= SENDER_MASK;
        final int messageType = isRemote ? viewType ^ SENDER_MASK : viewType;

        switch (messageType) {

            case SofaType.PAYMENT_REQUEST: {
                final View v = isRemote
                        ? LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__request_remote, parent, false)
                        : LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__request_local, parent, false);
                return new PaymentRequestViewHolder(v);
            }

            case SofaType.PAYMENT: {
                final View v = isRemote
                        ? LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__payment_remote, parent, false)
                        : LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__payment_local, parent, false);
                return new PaymentViewHolder(v);
            }

            case SofaType.IMAGE: {
                final View v = isRemote
                        ? LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__image_message_remote, parent, false)
                        : LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__image_message_local, parent, false);
                return new ImageViewHolder(v);
            }

            case SofaType.TIMESTAMP: {
                final View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__timestamp, parent, false);
                return new TimestampMessageViewHolder(v);
            }

            case SofaType.FILE: {
                final View v = isRemote
                        ? LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__file_message_remote, parent, false)
                        : LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__file_message_local, parent, false);
                return new FileViewHolder(v);
            }

            case SofaType.UNKNOWN:
            case SofaType.PLAIN_TEXT:
            default: {
                final View v = isRemote
                    ? LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__text_message_remote, parent, false)
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__text_message_local, parent, false);
                return new TextViewHolder(v);
            }
        }
    }

    @Override
    public final void onBindViewHolder(
            final RecyclerView.ViewHolder holder,
            final int position) {

        final SofaMessage sofaMessage = this.sofaMessages.get(position);
        final String payload = sofaMessage.getPayload();
        if (payload == null) return;

        try {
            renderChatMessageIntoViewHolder(holder, sofaMessage, payload, position);
        } catch (final IOException ex) {
            LogUtil.error(getClass(), "Unable to render view holder: " + ex);
        }
    }

    private void renderChatMessageIntoViewHolder(
            final RecyclerView.ViewHolder holder,
            final SofaMessage sofaMessage,
            final String payload,
            final int position) throws IOException {

        final boolean isRemote = holder.getItemViewType() >= SENDER_MASK;
        final int messageType = isRemote ? holder.getItemViewType() ^ SENDER_MASK : holder.getItemViewType();

        switch (messageType) {
            case SofaType.COMMAND_REQUEST:
            case SofaType.PLAIN_TEXT: {
                final TextViewHolder vh = (TextViewHolder) holder;
                final Message message = SofaAdapters.get().messageFrom(payload);
                final @ChainPosition.Position int chainPosition = getChainPosition(position);
                final boolean showAvatar = chainPosition == LAST || chainPosition == NONE;

                vh
                        .setText(message.getBody())
                        .setAvatarUri(showAvatar ? sofaMessage.getSenderAvatar() : null)
                        .setSendState(sofaMessage.getSendState())
                        .setChainPosition(chainPosition)
                        .setIsSentByRemoteUser(isRemote)
                        .setOnResendListener(this.onResendListener, sofaMessage)
                        .draw()
                        .setClickableUsernames(this.onUsernameClickListener);
                break;
            }

            case SofaType.IMAGE: {
                final ImageViewHolder vh = (ImageViewHolder) holder;
                vh
                        .setAvatarUri(sofaMessage.getSenderAvatar())
                        .setSendState(sofaMessage.getSendState())
                        .setAttachmentFilePath(sofaMessage.getAttachmentFilePath())
                        .setClickableImage(this.onImageClickListener, sofaMessage.getAttachmentFilePath())
                        .setOnResendListener(this.onResendListener, sofaMessage)
                        .draw();
                break;
            }

            case SofaType.FILE: {
                final FileViewHolder vh = (FileViewHolder) holder;
                vh
                        .setAttachmentPath(sofaMessage.getAttachmentFilePath())
                        .setAvatarUri(sofaMessage.getSenderAvatar())
                        .setOnClickListener(this.onFileClickListener, sofaMessage.getAttachmentFilePath())
                        .setOnResendListener(this.onResendListener, sofaMessage)
                        .draw();
                break;
            }

            case SofaType.PAYMENT: {
                final PaymentViewHolder vh = (PaymentViewHolder) holder;
                final Payment payment = SofaAdapters.get().paymentFrom(payload);
                vh
                        .setPayment(payment)
                        .setAvatarUri(sofaMessage.getSenderAvatar())
                        .setSendState(sofaMessage.getSendState())
                        .draw();
                break;
            }

            case SofaType.PAYMENT_REQUEST: {
                final PaymentRequestViewHolder vh = (PaymentRequestViewHolder) holder;
                final PaymentRequest request = SofaAdapters.get().txRequestFrom(payload);
                if (this.recipient != null && this.recipient.isGroup()) {
                    // Todo - support group payment requests
                    LogUtil.i(getClass(), "Payment requests to groups currently not supported.");
                    return;
                }

                vh.setPaymentRequest(request)
                  .setAvatarUri(sofaMessage.getSenderAvatar())
                  .setRemoteUser(this.recipient.getUser())
                  .setSendState(sofaMessage.getSendState())
                  .__setIsFromRemote(isRemote)
                  .setOnApproveListener(this.handleOnPaymentRequestApproved)
                  .setOnRejectListener(this.handleOnPaymentRequestRejected)
                  .setOnResendListener(this.onResendListener, sofaMessage)
                  .draw();
                break;
            }

            case SofaType.TIMESTAMP: {
                final TimestampMessageViewHolder vh = (TimestampMessageViewHolder) holder;
                vh.setTime(sofaMessage.getCreationTime());
            }
        }
    }

    private @ChainPosition.Position int getChainPosition(final int position) {
        final SofaMessage currentSofaMessage = this.sofaMessages.get(position);
        final SofaMessage previousSofaMessage = getMessageAtPos(position - 1);
        final SofaMessage nextSofaMessage = getMessageAtPos(position + 1);
        final boolean previousMessageSentByCurrent = previousSofaMessage != null && previousSofaMessage.isSentBy(currentSofaMessage.getSender());
        final boolean nextMessageSentByCurrent = nextSofaMessage != null && nextSofaMessage.isSentBy(currentSofaMessage.getSender());

        if (!previousMessageSentByCurrent && !nextMessageSentByCurrent) return NONE;
        if (previousMessageSentByCurrent && nextMessageSentByCurrent) return MIDDLE;
        if (nextMessageSentByCurrent) return FIRST;
        return LAST;
    }

    private @Nullable SofaMessage getMessageAtPos(final int position) {
        try {
            return this.sofaMessages.get(position);
        } catch (final IndexOutOfBoundsException ex) {
            return null;
        }
    }

    @Override
    public final int getItemCount() {
        return this.sofaMessages.size();
    }

    private final OnItemClickListener<Integer> handleOnPaymentRequestApproved = new OnItemClickListener<Integer>() {
        @Override
        public void onItemClick(final Integer position) {
            if (onPaymentRequestApproveListener == null) return;

            final SofaMessage sofaMessage = sofaMessages.get(position);
            onPaymentRequestApproveListener.onItemClick(sofaMessage);
        }
    };

    private final OnItemClickListener<Integer> handleOnPaymentRequestRejected = new OnItemClickListener<Integer>() {
        @Override
        public void onItemClick(final Integer position) {
            if (onPaymentRequestRejectListener == null) return;

            final SofaMessage sofaMessage = sofaMessages.get(position);
            onPaymentRequestRejectListener.onItemClick(sofaMessage);
        }
    };

    /**
     * Returns the latest message that isn't a Payment or PaymentRequest
     * Useful for deciding on whether or not the control view should be shown
     *
     * @return      the latest non-payment message, or null if none
     * @see         SofaMessage
     */
    public @Nullable SofaMessage getLastNonPaymentMessage() {
        for (int i = this.sofaMessages.size() - 1; i >= 0; i--) {
            final SofaMessage message = this.sofaMessages.get(i);
            if (message.getType() == SofaType.PAYMENT || message.getType() == SofaType.PAYMENT_REQUEST) continue;
            return message;
        }

        return null;
    }

    public void clear() {
        this.sofaMessages.clear();
        notifyDataSetChanged();
    }

    private User getCurrentLocalUser() {
        // Yes, this blocks. But realistically, a value should be always ready for returning.
        return BaseApplication
                .get()
                .getUserManager()
                .getCurrentUser()
                .toBlocking()
                .value();
    }
}