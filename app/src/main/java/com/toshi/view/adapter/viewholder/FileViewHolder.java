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

package com.toshi.view.adapter.viewholder;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.toshi.R;
import com.toshi.model.local.SendState;
import com.toshi.model.sofa.SofaMessage;
import com.toshi.util.FileUtil;
import com.toshi.util.ImageUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.adapter.listeners.OnItemClickListener;

public class FileViewHolder extends RecyclerView.ViewHolder {

    private @NonNull LinearLayout wrapper;
    private @NonNull TextView displayName;
    private @NonNull TextView fileSize;
    private @Nullable ImageView avatar;
    private @Nullable ImageView sentStatus;

    private String path;
    private String avatarUri;
    private @SendState.State int sendState;

    public FileViewHolder(View v) {
        super(v);
        this.wrapper = (LinearLayout) v.findViewById(R.id.wrapper);
        this.displayName = (TextView) v.findViewById(R.id.display_name);
        this.fileSize = (TextView) v.findViewById(R.id.file_size);
        this.avatar = (ImageView) v.findViewById(R.id.avatar);
        this.sentStatus = (ImageView) v.findViewById(R.id.sent_status);
    }

    public FileViewHolder setAttachmentPath(final String path) {
        this.path = path;
        return this;
    }

    public FileViewHolder setAvatarUri(final String avatarUri) {
        this.avatarUri = avatarUri;
        return this;
    }

    public FileViewHolder setSendState(final @SendState.State int sendStatus) {
        this.sendState = sendStatus;
        return this;
    }

    public FileViewHolder draw() {
        setPath(this.path);
        renderAvatar();
        setSendState();
        return this;
    }

    private void setPath(final String path) {
        final String fileName = new FileUtil().getFilenameFromPath(path);
        this.displayName.setText(fileName);

        final long bytes = new FileUtil().getFileSize(path);
        final String fileSizeText = Formatter.formatFileSize(BaseApplication.get(), bytes);
        this.fileSize.setText(fileSizeText);
    }

    private void renderAvatar() {
        if (this.avatar == null) return;
        ImageUtil.load(this.avatarUri, this.avatar);
    }

    public FileViewHolder setOnClickListener(final OnItemClickListener<String> listener,
                                             final String attachmentPath) {
        this.wrapper.setOnClickListener(__ -> listener.onItemClick(attachmentPath));
        return this;
    }

    public FileViewHolder setOnResendListener(final OnItemClickListener<SofaMessage> listener,
                                              final SofaMessage sofaMessage) {
        final @SendState.State int sendState = sofaMessage.getSendState();
        if (sendState == SendState.STATE_PENDING || sendState == SendState.STATE_FAILED) {
            this.itemView.setOnClickListener(__ -> listener.onItemClick(sofaMessage));
        }
        return this;
    }

    private void setSendState() {
        if (this.sentStatus == null) return;
        final int visibility = this.sendState == SendState.STATE_FAILED || this.sendState == SendState.STATE_PENDING
                ? View.VISIBLE
                : View.GONE;
        this.sentStatus.setVisibility(visibility);
    }
}
