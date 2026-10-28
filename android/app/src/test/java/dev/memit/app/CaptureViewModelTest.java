package dev.memit.app;

import android.app.Application;
import android.graphics.*;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import java.io.*;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CaptureViewModelTest {
    private CaptureViewModel model;
    private MockWebServer server;
    @Before public void setup() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        model = new CaptureViewModel(app);
        server = new MockWebServer(); server.start();
        File input = model.beginCapture();
        assertTrue(model.current().busy);
        assertNull(model.beginCapture());
        Bitmap bitmap = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.BLUE);
        try (FileOutputStream stream = new FileOutputStream(input)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream); }
        bitmap.recycle(); model.captured(input); awaitIdle();
        assertNotNull(model.current().photo);
    }
    @After public void teardown() throws Exception { server.shutdown(); model.onCleared(); }
    private void awaitIdle() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (model.current().busy && System.nanoTime() < deadline) { Thread.sleep(20); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
        assertFalse("Operation must terminate", model.current().busy);
    }
    @Test public void repeatedSendIsBlockedAndFailureCanBeRetriedWithSamePhoto() throws Exception {
        AppSettings settings = new AppSettings(1, server.url("/").toString(), true);
        File shot = model.current().photo;
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"message\":\"Попробуйте ещё раз\"}").setBodyDelay(300, TimeUnit.MILLISECONDS));
        model.send(settings); model.send(settings);
        assertTrue(model.current().busy); awaitIdle();
        assertEquals(1, server.getRequestCount());
        assertEquals(shot, model.current().photo); assertTrue(shot.isFile());
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"id\":1,\"imageUrl\":\"/api/photos/1/image\"}"));
        model.send(settings); awaitIdle();
        assertEquals(2, server.getRequestCount()); assertNull(model.current().photo); assertFalse(shot.exists());
        assertTrue(model.current().message.contains("Отправлено"));
    }
}
