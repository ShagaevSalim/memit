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
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingWorkPolicy;
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
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetRefreshWorker.class).build();
        WorkManager.getInstance(app).enqueueUniqueWork(WORK_NAME,
                settingsChanged ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP, request);
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        AppSettings settings = AppSettings.load(context);
        if (!settings.configured) {
            render(settings, null, "Выберите пользователя и сервер", "Откройте Memit\nи настройте подключение", false);
            return Result.success();
        }
        WidgetCache cache = null;
        Bitmap cached = null;
        boolean requestingLatest = true;
        try {
            cache = new WidgetCache(context, settings);
            cached = cache.bitmap();
            if (!active(settings)) return Result.success();
            render(settings, cached, "Получаем новый мем…", "Загрузка…", true);
            ApiClient api = new ApiClient(settings);
            ApiClient.Photo photo = api.latest();
            requestingLatest = false;
            if (!active(settings)) return Result.success();
            if (cache.photoId() != photo.id || cached == null) {
                byte[] bytes = api.download(photo);
                if (!active(settings)) return Result.success();
                cache.save(photo.id, bytes);
                if (cached != null) cached.recycle();
                cached = cache.bitmap();
                if (cached == null) throw new java.io.IOException("Не удалось показать мем");
            }
            if (active(settings)) render(settings, cached, "Для «" + settings.userName() + "» · мем #" + photo.id, "Пока нет мемов", false);
            return Result.success();
        } catch (Exception e) {
            if (!active(settings)) return Result.success();
            if (requestingLatest && e instanceof ApiClient.ApiException && ((ApiClient.ApiException) e).status == 404) {
                if (cache != null) cache.clear();
                render(settings, null, "Попросите друга отправить фото", "Пока нет мемов", false);
            } else {
                render(settings, cached, cached != null ? "Не удалось обновить · сохранённый мем" : "Нет связи с сервером · повторите", "Не удалось загрузить мем", false);
            }
            // A manual retry is deliberate: do not leave the refresh spinner waiting for backoff.
            return Result.success();
        } finally { if (cached != null && !cached.isRecycled()) cached.recycle(); }
    }

    private boolean active(AppSettings settings) { return !isStopped() && settings.isCurrent(getApplicationContext()); }

    private void render(AppSettings settings, Bitmap image, String status, String empty, boolean loading) {
        if (!active(settings)) return;
        Context context = getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, MemitWidget.class));
        RemoteViews views = views(context, settings, image, status, empty, loading);
        // Full updates release the launcher's old bitmap; partial updates can accumulate image memory.
        if (ids.length != 0) manager.updateAppWidget(ids, views);
    }

    public static RemoteViews views(Context context, AppSettings settings, Bitmap image, String status, String empty, boolean loading) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_memit);
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
