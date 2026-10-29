package dev.memit.app;

import android.graphics.BitmapFactory;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

public final class ApiClient {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).build();
    // Component changes by WorkManager can invalidate pooled sockets. Reads are
    // safe to retry; uploads keep retries disabled to avoid duplicate photos.
    private static final OkHttpClient READ_HTTP = HTTP.newBuilder().retryOnConnectionFailure(true).build();
    private final AppSettings settings;

    public ApiClient(AppSettings settings) { this.settings = settings; }

    public static final class ApiException extends IOException {
        public final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }

    public static final class Photo {
        public final long id;
        public final String imageUrl;
        public Photo(long id, String imageUrl) { this.id = id; this.imageUrl = imageUrl; }
    }

    public Photo upload(File file) throws IOException {
        RequestBody multipart = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "photo.jpg", RequestBody.create(file, MediaType.get("image/jpeg")))
                .addFormDataPart("senderId", Integer.toString(settings.userId))
                .addFormDataPart("recipientId", Integer.toString(settings.recipientId())).build();
        return metadata(new Request.Builder().url(settings.baseUrl.resolve("api/photos")).post(multipart).build());
    }

    public Photo latest() throws IOException {
        HttpUrl url = settings.baseUrl.newBuilder().addPathSegments("api/photos/latest")
                .addQueryParameter("userId", Integer.toString(settings.userId)).build();
        return metadata(new Request.Builder().url(url).build());
    }

    public void health() throws IOException {
        try (Response response = READ_HTTP.newCall(new Request.Builder().url(settings.baseUrl.resolve("api/health")).build()).execute()) {
            requireSuccess(response);
            try {
                if (!"UP".equals(new JSONObject(readText(response)).optString("status"))) throw new JSONException("status");
            } catch (JSONException e) { throw new IOException("Сервер вернул неверный ответ /api/health", e); }
        }
    }

    private Photo metadata(Request request) throws IOException {
        try (Response response = (request.method().equals("GET") ? READ_HTTP : HTTP).newCall(request).execute()) {
            requireSuccess(response);
            try {
                JSONObject json = new JSONObject(readText(response));
                long id = json.getLong("id");
                String path = json.getString("imageUrl");
                if (id <= 0 || !path.equals("/api/photos/" + id + "/image")) throw new JSONException("imageUrl");
                return new Photo(id, path);
            } catch (JSONException e) { throw new IOException("Сервер вернул неверные данные фотографии", e); }
        }
    }

    public byte[] download(Photo photo) throws IOException {
        HttpUrl url = settings.baseUrl.resolve(photo.imageUrl);
        if (url == null || !url.host().equals(settings.baseUrl.host()) || url.port() != settings.baseUrl.port()
                || !url.scheme().equals(settings.baseUrl.scheme())) throw new IOException("Неверный адрес фотографии");
        try (Response response = READ_HTTP.newCall(new Request.Builder().url(url).build()).execute()) {
            requireSuccess(response);
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > MAX_IMAGE_BYTES) throw new IOException("Фотография слишком большая");
            Buffer buffer = new Buffer();
            long count;
            while ((count = body.source().read(buffer, 8192)) != -1) {
                if (buffer.size() > MAX_IMAGE_BYTES) throw new IOException("Фотография слишком большая");
            }
            byte[] bytes = buffer.readByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth < 1 || bounds.outHeight < 1 || (long) bounds.outWidth * bounds.outHeight > 20_000_000) {
                throw new IOException("Сервер вернул повреждённое изображение");
            }
            return bytes;
        }
    }

    private static String readText(Response response) throws IOException {
        return response.peekBody(64 * 1024).string();
    }

    private static void requireSuccess(Response response) throws IOException {
        if (response.isSuccessful()) return;
        String message = "Ошибка сервера: " + response.code();
        try { message = new JSONObject(readText(response)).optString("message", message); }
        catch (JSONException ignored) { /* A proxy can return HTML instead of the API JSON. */ }
        if (response.code() == 413) message = "Снимок слишком большой. Переснимите фотографию.";
        throw new ApiException(response.code(), message);
    }

    public static String readableError(Throwable error) {
        if (error instanceof ApiException) return error.getMessage();
        if (error instanceof java.net.SocketTimeoutException || error instanceof java.io.InterruptedIOException)
            return "Сервер не ответил вовремя. Проверьте сеть и повторите.";
        if (error instanceof IOException)
            return "Не удалось связаться с сервером. Проверьте адрес, подключение и запуск backend.";
        return error.getMessage() == null ? "Что-то пошло не так. Попробуйте ещё раз." : error.getMessage();
    }
}
