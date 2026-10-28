package dev.memit.app;

import android.appwidget.AppWidgetManager;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.testing.TestWorkerBuilder;
import dev.memit.app.widget.*;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetTest {
    private Context context;
    private MockWebServer server;
    private AppSettings settings;
    private java.util.concurrent.ExecutorService executor;
    @Before public void setup() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.setTheme(R.style.Theme_Memit);
        server = new MockWebServer(); server.start();
        settings = new AppSettings(2, server.url("/").toString(), true); settings.save(context);
        executor = Executors.newSingleThreadExecutor();
    }
    @After public void teardown() throws Exception { server.shutdown(); executor.shutdownNow(); }
    static byte[] jpeg() {
        Bitmap bitmap = Bitmap.createBitmap(1400, 1400, Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.YELLOW);
        ByteArrayOutputStream stream = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream); bitmap.recycle();
        return stream.toByteArray();
    }
    private void refresh() { TestWorkerBuilder.from(context, WidgetRefreshWorker.class, executor).build().doWork(); }

    @Test public void refreshDownloadsRelativeUrlAndPreservesCacheOnFailure() throws Exception {
        androidx.work.Configuration configuration = new androidx.work.Configuration.Builder()
                .setExecutor(command -> { /* Work is driven explicitly below, not by the provider callback. */ })
                .setTaskExecutor(Runnable::run).build();
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration);
        int widgetId = Shadows.shadowOf(AppWidgetManager.getInstance(context)).createWidget(MemitWidget.class, R.layout.widget_memit);
        server.enqueue(new MockResponse().setBody("{\"id\":12,\"imageUrl\":\"/api/photos/12/image\"}"));
        server.enqueue(new MockResponse().setBody(new okio.Buffer().write(jpeg())).setHeader("Content-Type", "image/jpeg"));
        refresh();
        assertEquals("/api/photos/latest?userId=2", server.takeRequest().getPath());
        assertEquals("/api/photos/12/image", server.takeRequest().getPath());
        WidgetCache cache = new WidgetCache(context, settings);
        assertEquals(12, cache.photoId());
        Bitmap bitmap = cache.bitmap(); assertNotNull(bitmap); assertTrue(bitmap.getWidth() <= 512); bitmap.recycle();
        server.enqueue(new MockResponse().setResponseCode(503));
        refresh();
        View widget = Shadows.shadowOf(AppWidgetManager.getInstance(context)).getViewFor(widgetId);
        assertEquals(View.VISIBLE, widget.findViewById(R.id.widget_image).getVisibility());
        assertTrue(((TextView) widget.findViewById(R.id.widget_status)).getText().toString().contains("сохранённый мем"));
        assertEquals("Обновить", ((TextView) widget.findViewById(R.id.widget_refresh)).getText().toString());
        assertNotNull(cache.bitmap());
        new AppSettings(1, settings.baseUrl.toString(), true).save(context);
        WidgetRefreshWorker.enqueue(context, true);
        View switched = Shadows.shadowOf(AppWidgetManager.getInstance(context)).getViewFor(widgetId);
        assertEquals(View.GONE, switched.findViewById(R.id.widget_image).getVisibility());
        assertTrue(((TextView) switched.findViewById(R.id.widget_title)).getText().toString().contains("Аня"));
    }

    @Test public void empty404ClearsCacheAndSwitchingOwnerNeverUsesOtherInbox() throws Exception {
        WidgetCache cache = new WidgetCache(context, settings); cache.save(5, jpeg());
        AppSettings other = new AppSettings(1, settings.baseUrl.toString(), true);
        assertNull(new WidgetCache(context, other).bitmap());
        assertNotEquals(settings.cacheKey(), new AppSettings(2, "http://127.0.0.1:9999/", true).cacheKey());
        server.enqueue(new MockResponse().setResponseCode(404));
        refresh();
        assertNull(cache.bitmap()); assertEquals(-1, cache.photoId());
    }

    @Test public void unchangedPhotoSkipsImageDownloadAndCorruptDownloadKeepsOldPhoto() throws Exception {
        WidgetCache cache = new WidgetCache(context, settings); cache.save(5, jpeg());
        server.enqueue(new MockResponse().setBody("{\"id\":5,\"imageUrl\":\"/api/photos/5/image\"}"));
        refresh(); assertEquals(1, server.getRequestCount());
        server.enqueue(new MockResponse().setBody("{\"id\":6,\"imageUrl\":\"/api/photos/6/image\"}"));
        server.enqueue(new MockResponse().setBody("not a jpeg"));
        refresh(); assertEquals(5, cache.photoId()); assertNotNull(cache.bitmap());
        server.enqueue(new MockResponse().setBody("{\"id\":6,\"imageUrl\":\"/api/photos/6/image\"}"));
        server.enqueue(new MockResponse().setResponseCode(404));
        refresh(); assertEquals(5, cache.photoId()); assertNotNull(cache.bitmap());
    }

    @Test public void settingsSurviveReloadAndRejectNonRootAddresses() {
        assertEquals(2, AppSettings.load(context).userId);
        assertEquals(settings.baseUrl, AppSettings.load(context).baseUrl);
        assertEquals(1, AppSettings.load(context).recipientId());
        for (String bad : new String[]{"", "ftp://example.com", "http://example.com/api", "http://user:pass@example.com", "http://example.com/?q=1"}) {
            try { new AppSettings(1, bad, true); fail(bad); } catch (IllegalArgumentException expected) { }
        }
    }
}
