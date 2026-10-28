package dev.memit.app.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import dev.memit.app.AppSettings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Key by both server and owner so switching demo accounts never leaks another inbox. */
public final class WidgetCache {
    private final Context context;
    private final AppSettings settings;
    private final AtomicFile file;

    public WidgetCache(Context context, AppSettings settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        File directory = new File(context.getFilesDir(), "widget-cache");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Не удалось создать кэш");
        file = new AtomicFile(new File(directory, settings.cacheKey() + ".jpg"));
    }

    public long photoId() { return context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE).getLong(settings.cacheKey(), -1); }

    public void save(long id, byte[] bytes) throws IOException {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inSampleSize = 8;
        Bitmap check = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, probe);
        if (check == null) throw new IOException("Не удалось прочитать полученный мем");
        check.recycle();
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(bytes);
            file.finishWrite(stream);
        } catch (IOException e) { file.failWrite(stream); throw e; }
        context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE).edit().putLong(settings.cacheKey(), id).apply();
    }

    public void clear() {
        file.delete();
        context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE).edit().remove(settings.cacheKey()).apply();
    }

    public Bitmap bitmap() {
        try {
            // AtomicFile.openRead also recovers an interrupted write after process death.
            byte[] bytes = file.readFully();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (options.outWidth < 1 || options.outHeight < 1) return null;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (IOException e) { return null; }
    }
}
