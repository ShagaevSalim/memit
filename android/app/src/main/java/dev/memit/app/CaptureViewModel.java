package dev.memit.app;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CaptureViewModel extends AndroidViewModel {
    public static final class State {
        public final boolean busy;
        public final String message;
        public final File photo;
        public final Bitmap preview;
        State(boolean busy, String message, File photo, Bitmap preview) {
            this.busy = busy; this.message = message; this.photo = photo; this.preview = preview;
        }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, "Лови момент. Мем мы сделаем сами.", null, null));
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private File raw;
    private volatile boolean cleared;

    public CaptureViewModel(@NonNull Application app) { super(app); }
    public LiveData<State> state() { return state; }
    public State current() { return state.getValue(); }

    public File beginCapture() throws IOException {
        if (current().busy) return null;
        raw = File.createTempFile("capture-", ".jpg", getApplication().getCacheDir());
        state.setValue(new State(true, "Ловим момент…", null, null));
        return raw;
    }

    public void captured(File input) {
        io.execute(() -> {
            File result = null;
            try {
                result = File.createTempFile("ready-", ".jpg", getApplication().getCacheDir());
                PhotoNormalizer.normalize(input, result);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                byte[] jpeg = java.nio.file.Files.readAllBytes(result.toPath());
                Bitmap preview = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
                if (preview == null) throw new IOException("Не удалось показать снимок");
                if (cleared) { result.delete(); preview.recycle(); return; }
                state.postValue(new State(false, "Всё нравится? Отправь этот момент другу.", result, preview));
            } catch (Exception e) {
                if (result != null) result.delete();
                if (!cleared) state.postValue(new State(false, "Не удалось подготовить снимок. Попробуйте ещё раз.", null, null));
            } finally { input.delete(); }
        });
    }

    public void captureError() {
        if (raw != null) raw.delete();
        state.setValue(new State(false, "Не удалось сделать снимок. Проверьте камеру и повторите.", null, null));
    }

    public void retake() {
        State old = current();
        if (old.busy) return;
        if (old.photo != null) old.photo.delete();
        // ImageView can still reference the old bitmap until the next frame; let the GC release it.
        state.setValue(new State(false, "Лови момент. Мем мы сделаем сами.", null, null));
    }

    public void send(AppSettings settings) {
        State shot = current();
        if (shot.busy || shot.photo == null) return;
        state.setValue(new State(true, "Отправляем и превращаем фото в мем…", shot.photo, shot.preview));
        io.execute(() -> {
            try {
                new ApiClient(settings).upload(shot.photo);
                shot.photo.delete();
                if (!cleared) state.postValue(new State(false, "Отправлено! " + settings.recipientName() + " получит мем по кнопке «Обновить» в своём виджете.", null, null));
            } catch (Exception e) {
                if (!cleared) state.postValue(new State(false, ApiClient.readableError(e), shot.photo, shot.preview));
            }
        });
    }

    @Override protected void onCleared() {
        cleared = true;
        State last = current();
        File lastRaw = raw;
        io.execute(() -> {
            if (last.photo != null) last.photo.delete();
            if (lastRaw != null) lastRaw.delete();
        });
        io.shutdown();
    }
}
