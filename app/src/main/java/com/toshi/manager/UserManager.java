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

package com.toshi.manager;


import android.content.Context;
import android.content.SharedPreferences;

import com.toshi.crypto.HDWallet;
import com.toshi.manager.network.IdService;
import com.toshi.model.local.User;
import com.toshi.model.network.ServerTime;
import com.toshi.model.network.UserDetails;
import com.toshi.model.network.UserSearchResults;
import com.toshi.util.FileNames;
import com.toshi.util.FileUtil;
import com.toshi.util.LogUtil;
import com.toshi.util.SharedPrefsUtil;
import com.toshi.view.BaseApplication;

import java.io.File;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.adapter.rxjava.HttpException;
import rx.Single;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

public class UserManager {

    private final static String FORM_DATA_NAME = "Profile-Image-Upload";
    private final static String OLD_USER_ID = "uid";
    private final static String USER_ID = "uid_v2";


    private final BehaviorSubject<User> userSubject = BehaviorSubject.create();
    private SharedPreferences prefs;
    private HDWallet wallet;

    /* package */ UserManager() {}

    public final BehaviorSubject<User> getUserObservable() {
        return this.userSubject;
    }

    public final Single<User> getCurrentUser() {
        return
                this.userSubject
                .filter(user -> user != null)
                .first()
                .toSingle()
                .doOnError(t -> LogUtil.exception(getClass(), "getCurrentUser", t))
                .onErrorReturn(null);
    }

    public UserManager init(final HDWallet wallet) {
        this.wallet = wallet;
        this.prefs = BaseApplication.get().getSharedPreferences(FileNames.USER_PREFS, Context.MODE_PRIVATE);
        attachConnectivityListener();
        return this;
    }

    private void attachConnectivityListener() {
        // Whenever the network changes init the user.
        // This is dumb and potentially inefficient but it shouldn't have
        // any adverse effects and it is easy to improve later.
        BaseApplication
                .get()
                .isConnectedSubject()
                .subscribe(
                        isConnected -> initUser(),
                        this::handleUserError
                );
    }

    private void initUser() {
        if (userNeedsToRegister()) {
            registerNewUser();
        } else if (userNeedsToMigrate()) {
            migrateUser();
        } else if (SharedPrefsUtil.shouldForceUserUpdate()) {
            forceUpdateUser();
        } else {
            getExistingUser();
        }
    }

    private boolean userNeedsToRegister() {
        final String oldUserId = this.prefs.getString(OLD_USER_ID, null);
        final String newUserId = this.prefs.getString(USER_ID, null);
        final String expectedAddress = this.wallet.getOwnerAddress();
        final String userId = newUserId == null ? oldUserId : newUserId;
        return userId == null || !userId.equals(expectedAddress);
    }

    private boolean userNeedsToMigrate() {
        final String userId = this.prefs.getString(USER_ID, null);
        final String expectedAddress = this.wallet.getOwnerAddress();
        return userId == null || !userId.equals(expectedAddress);
    }

    private void registerNewUser() {
        getTimestamp()
        .subscribe(
                this::registerNewUserWithTimestamp,
                this::handleUserError
        );
    }

    private void handleUserError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Unable to register/fetch user", throwable);
    }

    private void registerNewUserWithTimestamp(final ServerTime serverTime) {
        final UserDetails ud = new UserDetails().setPaymentAddress(this.wallet.getPaymentAddress());

        IdService
            .getApi()
            .registerUser(ud, serverTime.get())
            .subscribe(
                    this::updateCurrentUser,
                    this::handleUserRegistrationFailed
            );
        SharedPrefsUtil.setForceUserUpdate(false);
    }

    private void handleUserRegistrationFailed(final Throwable throwable) {
        LogUtil.error(getClass(), throwable.toString());
        if (throwable instanceof HttpException && ((HttpException)throwable).code() == 400) {
            getExistingUser();
        }
    }

    private void getExistingUser() {
        IdService
            .getApi()
            .getUser(this.wallet.getOwnerAddress())
            .subscribe(
                    this::updateCurrentUser,
                    this::handleUserError
            );
    }

    private void updateCurrentUser(final User user) {
        prefs
            .edit()
            .putString(USER_ID, user.getToshiId())
            .apply();
        this.userSubject.onNext(user);
    }

    private void migrateUser() {
        forceUpdateUser();
        SharedPrefsUtil.setWasMigrated(true);
    }

    private void forceUpdateUser() {
        final UserDetails ud = new UserDetails().setPaymentAddress(this.wallet.getPaymentAddress());
        updateUser(ud).subscribe(
                __ -> {},
                this::handleUserError
        );
        SharedPrefsUtil.setForceUserUpdate(false);
    }

    public Single<User> updateUser(final UserDetails userDetails) {
        return getTimestamp()
                .subscribeOn(Schedulers.io())
                .flatMap(serverTime -> updateUserWithTimestamp(userDetails, serverTime));
    }

    private Single<User> updateUserWithTimestamp(
            final UserDetails userDetails,
            final ServerTime serverTime) {

        return IdService.getApi()
                .updateUser(this.wallet.getOwnerAddress(), userDetails, serverTime.get())
                .subscribeOn(Schedulers.io())
                .doOnSuccess(this::updateCurrentUser);
    }

    public Single<User> uploadAvatar(final File file) {
        final FileUtil fileUtil = new FileUtil();
        final String mimeType = fileUtil.getMimeTypeFromFilename(file.getName());
        if (mimeType == null) {
            return Single.error(new IllegalArgumentException("Unable to determine file type from file."));
        }
        final MediaType mediaType = MediaType.parse(mimeType);
        final RequestBody requestFile = RequestBody.create(mediaType, file);
        final MultipartBody.Part body = MultipartBody.Part.createFormData(FORM_DATA_NAME, file.getName(), requestFile);

        return getTimestamp()
                .subscribeOn(Schedulers.io())
                .flatMap(serverTime ->
                        IdService
                        .getApi()
                        .uploadFile(body, serverTime.get()))
                .doOnSuccess(this.userSubject::onNext);
    }

    private Single<ServerTime> getTimestamp() {
        return IdService
                .getApi()
                .getTimestamp();
    }

    public Single<List<User>> getTopRatedPublicUsers(final int limit) {
        return IdService
                .getApi()
                .getUsers(true, true, false, limit)
                .map(UserSearchResults::getResults)
                .subscribeOn(Schedulers.io());
    }

    public Single<List<User>> getLatestPublicUsers(final int limit) {
        return IdService
                .getApi()
                .getUsers(true, false, true, limit)
                .map(UserSearchResults::getResults)
                .subscribeOn(Schedulers.io());
    }

    public Single<Void> webLogin(final String loginToken) {
        return getTimestamp()
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .flatMap((serverTime) -> webLoginWithTimestamp(loginToken, serverTime));
    }

    private Single<Void> webLoginWithTimestamp(final String loginToken, final ServerTime serverTime) {
        if (serverTime == null) {
            throw new IllegalStateException("ServerTime was null");
        }

        return IdService
                .getApi()
                .webLogin(loginToken, serverTime.get());
    }

    public void clear() {
        this.prefs
                .edit()
                .putString(USER_ID, null)
                .apply();
        this.userSubject.onNext(null);
    }
}
