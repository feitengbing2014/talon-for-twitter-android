package com.klinker.android.twitter_l.services.background_refresh;
/*
 * Copyright 2014 Luke Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.klinker.android.twitter_l.data.sq_lite.MentionsDataSource;
import com.klinker.android.twitter_l.settings.AppSettings;
import com.klinker.android.twitter_l.utils.NotificationUtils;
import com.klinker.android.twitter_l.utils.Utils;

import java.util.List;

import twitter4j.Paging;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;

public class SecondMentionsRefreshService extends Worker {

    private final Context context;
    public SecondMentionsRefreshService(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    public static void startNow(Context context) {
        WorkManager.getInstance(context)
                .enqueue(new OneTimeWorkRequest.Builder(SecondMentionsRefreshService.class).build());
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences sharedPrefs = AppSettings.getSharedPreferences(context);

        Context context = getApplicationContext();
        AppSettings settings = AppSettings.getInstance(context);

        // if they have mobile data on and don't want to sync over mobile data
        if (Utils.getConnectionStatus(context) && !settings.syncMobile) {
            return Result.success();
        }

        boolean update = false;
        int numberNew = 0;

        try {
            Twitter twitter = Utils.getSecondTwitter(context);

            int currentAccount = sharedPrefs.getInt("current_account", 1);

            if(currentAccount == 1) {
                currentAccount = 2;
            } else {
                currentAccount = 1;
            }

            MentionsDataSource dataSource = MentionsDataSource.getInstance(context);

            long lastId = dataSource.getLastIds(currentAccount)[0];
            Paging paging;
            paging = new Paging(1, 200);
            if (lastId > 0) {
                paging.sinceId(lastId);
            }

            List<Status> statuses = twitter.getMentionsTimeline(paging);

            numberNew = MentionsDataSource.getInstance(context).insertTweets(statuses, currentAccount);

            if (numberNew > 0) {
                sharedPrefs.edit().putBoolean("refresh_me_mentions", true).apply();

                if (settings.notifications && settings.mentionsNot) {
                    NotificationUtils.notifySecondMentions(context, currentAccount);
                }

                context.sendBroadcast(new Intent("com.klinker.android.twitter.REFRESH_SECOND_MENTIONS"));
            }

        } catch (TwitterException e) {
            // Error in updating status
            Log.d("Twitter Update Error", e.getMessage());
        }

        return Result.success();
    }
}