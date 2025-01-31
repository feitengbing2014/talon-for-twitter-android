package com.klinker.android.twitter_l.widget.timeline

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.klinker.android.twitter_l.R
import com.klinker.android.twitter_l.activities.MainActivity
import com.klinker.android.twitter_l.activities.compose.WidgetCompose
import com.klinker.android.twitter_l.activities.tweet_viewer.TweetActivity
import com.klinker.android.twitter_l.services.background_refresh.WidgetRefreshService
import com.klinker.android.twitter_l.settings.AppSettings
import com.klinker.android.twitter_l.utils.Utils
import com.klinker.android.twitter_l.utils.glide.CircleBitmapTransform
import java.util.*

class TimelineWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val mgr = getAppWidgetManager(context)

        val action = intent.action
        if (action != null && action == OPEN_ACTION) {
            val viewTweet = Intent(context, TweetActivity::class.java)

            viewTweet.putExtra("name", intent.getStringExtra("name"))
            viewTweet.putExtra("screenname", intent.getStringExtra("screenname"))
            viewTweet.putExtra("time", intent.getLongExtra("time", 0))
            viewTweet.putExtra("tweet", intent.getStringExtra("tweet"))
            viewTweet.putExtra("retweeter", intent.getStringExtra("retweeter"))
            viewTweet.putExtra("webpage", intent.getStringExtra("webpage"))
            viewTweet.putExtra("picture", intent.getBooleanExtra("picture", false))
            viewTweet.putExtra("tweetid", intent.getLongExtra("tweetid", 0))
            viewTweet.putExtra("proPic", intent.getStringExtra("propic"))
            viewTweet.putExtra("from_widget", true)
            viewTweet.putExtra("users", intent.getStringExtra("users"))
            viewTweet.putExtra("hashtags", intent.getStringExtra("hashtags"))
            viewTweet.putExtra("other_links", intent.getStringExtra("other_links"))
            viewTweet.putExtra("animated_gif", intent.getStringExtra("animated_gif"))

            TweetActivity.applyDragDismissBundle(context, viewTweet)
            viewTweet.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            context.startActivity(viewTweet)
            return
        }

        val thisAppWidget = ComponentName(context.packageName, TimelineWidgetProvider::class.java.name)
        val appWidgetIds = mgr.getAppWidgetIds(thisAppWidget)

        try {
            mgr.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widgetList)
        } catch (e: NullPointerException) {
            Log.e(TAG, "failed to notify of widget changed", e)
        }

    }

    private fun getAppWidgetManager(context: Context): AppWidgetManager {
        return AppWidgetManager.getInstance(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {

        var material = false
        var layout = 0
        when (Integer.parseInt(AppSettings.getSharedPreferences(context)
                .getString("widget_theme", "4")!!)) {
            0 -> layout = R.layout.widget_light
            1 -> layout = R.layout.widget_dark
            2 -> layout = R.layout.widget_trans_light
            3 -> layout = R.layout.widget_trans_black
            4 -> {
                layout = R.layout.widget_material_light
                material = true
            }
            5 -> {
                layout = R.layout.widget_material_dark
                material = true
            }
            6 -> {
                layout = R.layout.widget_material_transparent
                material = false
            }
        }

        for (i in appWidgetIds.indices) {
            val intent = Intent(context, TimelineWidgetService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i])
            intent.putExtra("nonce", Random().nextInt())
            intent.data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))

            val compose = Intent(context, WidgetCompose::class.java)
            compose.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            val pendingCompose = PendingIntent.getActivity(context, 0, compose, Utils.withImmutability(PendingIntent.FLAG_UPDATE_CURRENT))

            val open = Intent(context, MainActivity::class.java)
            open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            val pendingOpen = PendingIntent.getActivity(context, 0, open, Utils.withImmutability(PendingIntent.FLAG_UPDATE_CURRENT))

            val refresh = Intent(context, WidgetRefreshService::class.java)
            val pendingRefresh = PendingIntent.getService(context, 0, refresh, Utils.withImmutability(PendingIntent.FLAG_UPDATE_CURRENT))


            val rv = RemoteViews(context.packageName, layout)
            rv.setRemoteAdapter(R.id.widgetList, intent)

            rv.setOnClickPendingIntent(R.id.textView1, pendingOpen)
            rv.setOnClickPendingIntent(R.id.launcherIcon, pendingOpen)
            rv.setOnClickPendingIntent(R.id.replyButton, pendingCompose)
            rv.setOnClickPendingIntent(R.id.syncButton, pendingRefresh)

//            val handler = Handler()
//            Thread {
//                val bitmap = getCachedPic(context, AppSettings.getInstance(context).myProfilePicUrl)
//                handler.post { rv.setImageViewBitmap(R.id.widget_pro_pic, bitmap) }
//            }.start()
            rv.setViewVisibility(R.id.widget_pro_pic, View.GONE)
            rv.setViewVisibility(R.id.replyButton, View.VISIBLE)

            if (AppSettings.getInstance(context).widgetDisplayScreenname) {
                rv.setTextViewText(R.id.textView1, "@" + AppSettings.getInstance(context).myScreenName)
            } else {
                rv.setTextViewText(R.id.textView1, "")
            }

            if (material) {
                rv.setInt(R.id.relLayout, "setBackgroundColor", AppSettings.getInstance(context).themeColors.primaryColor)
            }

            val openIntent = Intent(context, TimelineWidgetProvider::class.java)
            openIntent.action = TimelineWidgetProvider.OPEN_ACTION
            openIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i])
            val openPendingIntent = PendingIntent.getBroadcast(context, 0, openIntent,
                    Utils.withMutability(0))
            rv.setPendingIntentTemplate(R.id.widgetList, openPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetIds[i], rv)
        }

        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    fun getCachedPic(context: Context, url: String): Bitmap? {
        try {
            /*return ImageUtils.getCircleBitmap(Glide.
                    with(mContext).
                    load(url).
                    asBitmap().
                    into(200, 200).
                    get());*/
            return Glide.with(context)
                    .load(url)
                    .asBitmap()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .transform(CircleBitmapTransform(context))
                    .into(200, 200)
                    .get()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

    }

    companion object {
        private val TAG = "AppWidgetProvider"

        val REFRESH_ACTION = "com.klinker.android.twitter_l.widget.REFRESH"
        val OPEN_ACTION = "com.klinker.android.twitter_l.widget.OPEN"

        fun refreshWidget(context: Context) {
            val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, TimelineWidgetProvider::class.java))

            val intent = Intent(context, TimelineWidgetProvider::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)

            context.sendBroadcast(intent)
            context.sendBroadcast(Intent(REFRESH_ACTION))
        }
    }

}