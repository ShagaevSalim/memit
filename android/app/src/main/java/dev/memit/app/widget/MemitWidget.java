package dev.memit.app.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.work.WorkManager;

public final class MemitWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "dev.memit.app.REFRESH_WIDGET";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        WidgetRefreshWorker.enqueue(context, false);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) WidgetRefreshWorker.enqueue(context, false);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        WidgetRefreshWorker.enqueue(context, false);
    }

    @Override public void onDisabled(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WidgetRefreshWorker.WORK_NAME);
    }
}
