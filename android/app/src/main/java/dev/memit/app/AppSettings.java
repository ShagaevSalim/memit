package dev.memit.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import okhttp3.HttpUrl;

/** One persistent source of identity and server address for the activity and every widget. */
public final class AppSettings {
    public final int userId;
    public final HttpUrl baseUrl;
    public final boolean configured;

    public AppSettings(int userId, String baseUrl, boolean configured) {
        if (userId != 1 && userId != 2) throw new IllegalArgumentException("Выберите Аню или Максима");
        HttpUrl url = HttpUrl.parse(baseUrl.trim());
        if (url == null || !url.username().isEmpty() || !url.password().isEmpty()
                || url.query() != null || url.fragment() != null || !url.encodedPath().equals("/")) {
            throw new IllegalArgumentException("Введите адрес сервера: http://192.168.1.10:8080/");
        }
        if (!BuildConfig.DEBUG && !url.isHttps()) {
            throw new IllegalArgumentException("В release-сборке нужен HTTPS. Для локального HTTP установите debug-сборку.");
        }
        this.userId = userId;
        this.baseUrl = url;
        this.configured = configured;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("memit_settings", Context.MODE_PRIVATE);
    }

    public static AppSettings load(Context context) {
        SharedPreferences p = preferences(context);
        String defaultUrl = Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator")
                || Build.MODEL.contains("sdk_gphone") ? "http://10.0.2.2:8080/" : "http://127.0.0.1:8080/";
        // Release builds must start with a valid HTTPS value even before the user opens settings.
        if (!BuildConfig.DEBUG) defaultUrl = "https://example.com/";
        try {
            return new AppSettings(p.getInt("user_id", 1), p.getString("base_url", defaultUrl), p.getBoolean("configured", false));
        } catch (IllegalArgumentException invalidSavedSettings) {
            return new AppSettings(1, defaultUrl, false);
        }
    }

    public void save(Context context) {
        if (!preferences(context).edit().putInt("user_id", userId)
                .putString("base_url", baseUrl.toString()).putBoolean("configured", true).commit()) {
            throw new IllegalStateException("Не удалось сохранить настройки. Попробуйте ещё раз.");
        }
    }

    public int recipientId() { return userId == 1 ? 2 : 1; }
    public String userName() { return userId == 1 ? "Аня" : "Максим"; }
    public String recipientName() { return userId == 1 ? "Максим" : "Аня"; }

    public String cacheKey() {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest((baseUrl + "|" + userId).getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte b : bytes) key.append(String.format(Locale.ROOT, "%02x", b & 255));
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public boolean isCurrent(Context context) { return cacheKey().equals(load(context).cacheKey()); }
}
