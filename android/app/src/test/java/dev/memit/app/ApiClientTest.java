package dev.memit.app;

import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class ApiClientTest {
    private MockWebServer server;
    @Before public void start() throws Exception { server = new MockWebServer(); server.start(); }
    @After public void stop() throws Exception { server.shutdown(); }
    private AppSettings settings(int id) { return new AppSettings(id, server.url("/").toString(), true); }
    private MockResponse metadata() { return new MockResponse().setResponseCode(201).setBody("{\"id\":12,\"imageUrl\":\"/api/photos/12/image\"}"); }

    @Test public void uploadsRealMultipartWithAutomaticRecipient() throws Exception {
        File photo = File.createTempFile("upload", ".jpg", ApplicationProvider.getApplicationContext().getCacheDir());
        Files.write(photo.toPath(), new byte[]{(byte) 0xff, (byte) 0xd8, 1, 2, (byte) 0xff, (byte) 0xd9});
        for (int owner : new int[]{1, 2}) {
            server.enqueue(metadata());
            assertEquals(12, new ApiClient(settings(owner)).upload(photo).id);
            RecordedRequest request = server.takeRequest(3, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("POST", request.getMethod());
            assertEquals("/api/photos", request.getPath());
            assertTrue(request.getHeader("Content-Type").startsWith("multipart/form-data; boundary="));
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("name=\"file\"; filename=\"photo.jpg\""));
            assertTrue(body.contains("Content-Type: image/jpeg"));
            assertTrue(body.contains("name=\"senderId\"\r\nContent-Length: 1\r\n\r\n" + owner));
            assertTrue(body.contains("name=\"recipientId\"\r\nContent-Length: 1\r\n\r\n" + (owner == 1 ? 2 : 1)));
        }
        photo.delete();
    }

    @Test public void latestUsesOwnerAndDoesNotConfuse404WithServerFailure() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"message\":\"Пока нет фото\"}"));
        try { new ApiClient(settings(2)).latest(); fail(); }
        catch (ApiClient.ApiException e) { assertEquals(404, e.status); }
        assertEquals("/api/photos/latest?userId=2", server.takeRequest().getPath());
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"message\":\"Ошибка хранения\"}"));
        try { new ApiClient(settings(1)).latest(); fail(); }
        catch (ApiClient.ApiException e) { assertEquals(500, e.status); assertEquals("Ошибка хранения", e.getMessage()); }
    }

    @Test public void rejectsInvalidMetadataAndCrossOriginImage() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"id\":1,\"imageUrl\":\"https://other.example/photo.jpg\"}"));
        try { new ApiClient(settings(1)).latest(); fail(); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("неверные данные")); }
        try { new ApiClient(settings(1)).download(new ApiClient.Photo(1, "https://other.example/photo.jpg")); fail(); }
        catch (java.io.IOException expected) { assertEquals("Неверный адрес фотографии", expected.getMessage()); }
    }
}
