package dev.memit.app.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Bitmap;
import dev.memit.app.AppSettings;

public final class MemitWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "dev.memit.app.REFRESH_WIDGET";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        restoreViews(context, manager, ids);
        // WorkManager toggles its RescheduleReceiver. That package change can cause
        // another onUpdate: enqueueing on every callback creates a refresh loop.
        // Persist before enqueueing so process restarts cannot restart the loop.
        android.content.SharedPreferences initialized = context.getSharedPreferences("widget_instances", Context.MODE_PRIVATE);
        boolean added = false;
        android.content.SharedPreferences.Editor editor = initialized.edit();
        for (int id : ids) {
            if (!initialized.getBoolean("initialized_" + id, false)) {
                editor.putBoolean("initialized_" + id, true);
                added = true;
            }
        }
        if (added && editor.commit()) WidgetRefreshWorker.enqueue(context, false);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) WidgetRefreshWorker.enqueue(context, false);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        restoreViews(context, manager, new int[]{id});
    }

    private static void restoreViews(Context context, AppWidgetManager manager, int[] ids) {
        // Package changes can discard RemoteViews in the launcher. Always restore
        // the image AND click handlers, but do not schedule network work here.
        AppSettings settings = AppSettings.load(context);
        Bitmap image = null;
        {
            WidgetCache cache = new WidgetCache(context, settings);
            if (settings.configured) image = cache.bitmap();
            String status = !settings.configured ? "Выберите пользователя и сервер"
                    : image != null ? "Для «" + settings.userName() + "» · мем #" + cache.photoId()
                    : "Нажмите «Обновить», чтобы получить мем";
            manager.updateAppWidget(ids, WidgetRefreshWorker.views(context, settings, image, status,
                    settings.configured ? "Пока нет мемов" : "Откройте Memit\nи настройте подключение", false));
        } // RemoteViews owns bitmap backing data; release through GC.
    }

    @Override public void onDeleted(Context context, int[] ids) {
        android.content.SharedPreferences.Editor editor = context.getSharedPreferences("widget_instances", Context.MODE_PRIVATE).edit();
        for (int id : ids) editor.remove("initialized_" + id);
        editor.commit();
    }

    @Override public void onDisabled(Context context) {
        context.getSystemService(android.app.job.JobScheduler.class).cancel(WidgetRefreshService.JOB_ID);
    }
}
