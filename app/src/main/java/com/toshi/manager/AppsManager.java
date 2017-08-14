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


import com.toshi.manager.network.DirectoryService;
import com.toshi.manager.network.IdService;
import com.toshi.model.network.App;
import com.toshi.model.network.AppSearchResult;
import com.toshi.model.network.ServerTime;

import java.util.List;

import rx.Observable;
import rx.Single;
import rx.schedulers.Schedulers;

public class AppsManager {

    public Observable<List<App>> searchApps(final String query) {
        return DirectoryService
                .getApi()
                .searchApps(query)
                .subscribeOn(Schedulers.io())
                .first((response) -> response.code() == 200)
                .flatMap((response) -> Observable.just(response.body().getApps()));
    }

    public Single<App> getApp(final String toshiId) {
        return DirectoryService
                .getApi()
                .getApp(toshiId)
                .toObservable()
                .first((response) -> response.code() == 200)
                .flatMap((response) -> Observable.just(response.body()))
                .subscribeOn(Schedulers.io())
                .toSingle();
    }

    public Single<List<App>> getTopRatedApps(final int limit) {
        return IdService
                .getApi()
                .getApps(true, false, limit)
                .map(AppSearchResult::getResults)
                .subscribeOn(Schedulers.io());
    }

    public Single<List<App>> getLatestApps(final int limit) {
        return IdService
                .getApi()
                .getApps(false, true, limit)
                .map(AppSearchResult::getResults)
                .subscribeOn(Schedulers.io());
    }

    public Single<ServerTime> getTimestamp() {
        return IdService
                .getApi()
                .getTimestamp();
    }
}
