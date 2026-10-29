package dev.memit.app.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.RemoteViews;
import dev.memit.app.ApiClient;
import dev.memit.app.AppSettings;

final class WidgetRefreshTask {
    private final Context context;
    private final java.util.function.BooleanSupplier stopped;
    WidgetRefreshTask(Context context, java.util.function.BooleanSupplier stopped) {
        this.context = context.getApplicationContext();
        this.stopped = stopped;
    }
    public void run() {
        AppSettings settings = AppSettings.load(context);
        if (!settings.configured) {
            render(settings, null, "Выберите пользователя и сервер", "Откройте Memit\nи настройте подключение", false);
            return;
        }
        WidgetCache cache = null;
        Bitmap cached = null;
        boolean requestingLatest = true;
        try {
            cache = new WidgetCache(context, settings);
            cached = cache.bitmap();
            if (!active(settings)) return;
            render(settings, cached, "Получаем новый мем…", "Загрузка…", true);
            ApiClient api = new ApiClient(settings);
            ApiClient.Photo photo = api.latest();
            requestingLatest = false;
            if (!active(settings)) return;
            if (cache.photoId() != photo.id || cached == null) {
                byte[] bytes = api.download(photo);
                if (!active(settings)) return;
                cache.save(photo.id, bytes);
                cached = cache.bitmap();
                if (cached == null) throw new java.io.IOException("Не удалось показать мем");
            }
            if (active(settings)) render(settings, cached, "Для «" + settings.userName() + "» · мем #" + photo.id, "Пока нет мемов", false);
            return;
        } catch (Exception e) {
            android.util.Log.w("MemitWidget", "Unable to refresh inbox for user " + settings.userId, e);
            if (!active(settings)) return;
            if (requestingLatest && e instanceof ApiClient.ApiException && ((ApiClient.ApiException) e).status == 404) {
                if (cache != null) cache.clear();
                render(settings, null, "Попросите друга отправить фото", "Пока нет мемов", false);
            } else {
                render(settings, cached, cached != null ? "Не удалось обновить · сохранённый мем" : "Нет связи с сервером · повторите", "Не удалось загрузить мем", false);
            }
            // A manual retry is deliberate: do not leave the refresh spinner waiting for backoff.
            return;
        }
        // RemoteViews/launcher may consume bitmap data asynchronously. Let GC
        // release these small bitmaps instead of recycling shared backing data.
    }

    private boolean active(AppSettings settings) { return !stopped.getAsBoolean() && settings.isCurrent(context); }

    private void render(AppSettings settings, Bitmap image, String status, String empty, boolean loading) {
        if (!active(settings)) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, MemitWidget.class));
        RemoteViews views = WidgetRefreshWorker.views(context, settings, image, status, empty, loading);
        // Full updates release the launcher's old bitmap; partial updates can accumulate image memory.
        if (ids.length != 0) manager.updateAppWidget(ids, views);
    }

}
