package dev.memit.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import dev.memit.app.ApiClient;
import dev.memit.app.AppSettings;
import dev.memit.app.MainActivity;
import dev.memit.app.R;

/** Manual, deduplicated refresh. No network constraint: offline clicks must finish with a retry state. */
public final class WidgetRefreshWorker extends Worker {
    public static final String WORK_NAME = "memit-inbox-refresh";

    public WidgetRefreshWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }

    public static void enqueue(Context context, boolean settingsChanged) {
        Context app = context.getApplicationContext();
        int[] ids = AppWidgetManager.getInstance(app).getAppWidgetIds(new ComponentName(app, MemitWidget.class));
        if (ids.length == 0) return;
        if (settingsChanged) {
            // Clear the previous inbox immediately, even if Android delays starting the new worker.
            RemoteViews loading = views(app, AppSettings.load(app), null, "Получаем новый мем…", "Загрузка…", true);
            AppWidgetManager.getInstance(app).updateAppWidget(ids, loading);
        }
        android.app.job.JobScheduler scheduler = app.getSystemService(android.app.job.JobScheduler.class);
        if (!settingsChanged && scheduler.getPendingJob(WidgetRefreshService.JOB_ID) != null) return;
        if (settingsChanged) scheduler.cancel(WidgetRefreshService.JOB_ID);
        scheduler.schedule(new android.app.job.JobInfo.Builder(WidgetRefreshService.JOB_ID,
                new ComponentName(app, WidgetRefreshService.class)).setMinimumLatency(0).build());
    }

    @NonNull @Override public Result doWork() {
        new WidgetRefreshTask(getApplicationContext(), this::isStopped).run();
        return Result.success();
    }

    public static RemoteViews views(Context context, AppSettings settings, Bitmap image, String status, String empty, boolean loading) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_memit_content);
        views.setTextViewText(R.id.widget_title, "memit · " + settings.userName());
        views.setTextViewText(R.id.widget_status, status);
        views.setTextViewText(R.id.widget_empty, empty);
        views.setViewVisibility(R.id.widget_image, image != null ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_empty, image != null ? View.GONE : View.VISIBLE);
        if (image != null) views.setImageViewBitmap(R.id.widget_image, image);
        views.setTextViewText(R.id.widget_refresh, loading ? "Обновляем…" : "Обновить");
        PendingIntent open = PendingIntent.getActivity(context, 10, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent refresh = new Intent(context, MemitWidget.class).setAction(MemitWidget.ACTION_REFRESH);
        PendingIntent retry = PendingIntent.getBroadcast(context, 11, refresh, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_refresh, settings.configured ? retry : open);
        views.setOnClickPendingIntent(R.id.widget_open, open);
        views.setOnClickPendingIntent(R.id.widget_empty, open);
        return views;
    }
}
