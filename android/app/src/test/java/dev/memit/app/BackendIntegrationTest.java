package dev.memit.app;

import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import androidx.test.core.app.ApplicationProvider;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Opt-in integration using the shipping Android client against a real Spring Boot process. */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BackendIntegrationTest {
    @Test public void normalizedPhotoReachesBothRecipientsAndReturnsStableSquareJpeg() throws Exception {
        String address = System.getenv("MEMIT_TEST_BASE_URL");
        Assume.assumeTrue("Set MEMIT_TEST_BASE_URL to an isolated running demo backend", address != null);
        File directory = ApplicationProvider.getApplicationContext().getCacheDir();
        File input = File.createTempFile("integration-source", ".jpg", directory);
        File normalized = File.createTempFile("integration-normalized", ".jpg", directory);
        Bitmap image = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image); canvas.drawColor(Color.rgb(255, 196, 52));
        Paint paint = new Paint(); paint.setColor(Color.rgb(81, 67, 130)); canvas.drawRect(50, 50, 300, 500, paint);
        try (FileOutputStream stream = new FileOutputStream(input)) { image.compress(Bitmap.CompressFormat.JPEG, 95, stream); }
        image.recycle();
        ExifInterface exif = new ExifInterface(input);
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, "6"); exif.saveAttributes();
        PhotoNormalizer.normalize(input, normalized);
        for (int sender : new int[]{1, 2}) {
            ApiClient send = new ApiClient(new AppSettings(sender, address, true)); send.health();
            ApiClient.Photo uploaded = send.upload(normalized);
            ApiClient receive = new ApiClient(new AppSettings(sender == 1 ? 2 : 1, address, true));
            ApiClient.Photo incoming = receive.latest(); assertEquals(uploaded.id, incoming.id);
            byte[] bytes = receive.download(incoming);
            Bitmap meme = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            assertEquals(400, meme.getWidth()); assertEquals(400, meme.getHeight()); meme.recycle();
            assertArrayEquals(bytes, receive.download(incoming));
        }
        input.delete(); normalized.delete();
    }
}
