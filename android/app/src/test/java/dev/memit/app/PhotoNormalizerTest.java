package dev.memit.app;

import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import androidx.test.core.app.ApplicationProvider;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhotoNormalizerTest {
    // Input corners are red, green, blue, yellow (TL, TR, BL, BR).
    @Test public void bakesAllEightExifOrientationsIntoPixelsWithoutCropping() throws Exception {
        int[][] corners = {{0,1,2,3}, {1,0,3,2}, {3,2,1,0}, {2,3,0,1}, {0,2,1,3}, {2,0,3,1}, {3,1,2,0}, {1,3,0,2}};
        int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
        for (int orientation = 1; orientation <= 8; orientation++) {
            File dir = ApplicationProvider.getApplicationContext().getCacheDir();
            File input = File.createTempFile("exif-input", ".jpg", dir);
            File output = File.createTempFile("exif-output", ".jpg", dir);
            Bitmap bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            for (int i = 0; i < 4; i++) {
                paint.setColor(colors[i]);
                canvas.drawRect((i % 2) * 40, (i / 2) * 60, (i % 2 + 1) * 40, (i / 2 + 1) * 60, paint);
            }
            try (FileOutputStream stream = new FileOutputStream(input)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream); }
            bitmap.recycle();
            ExifInterface exif = new ExifInterface(input);
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation)); exif.saveAttributes();
            PhotoNormalizer.normalize(input, output);
            Bitmap result = BitmapFactory.decodeFile(output.getPath());
            assertEquals(orientation >= 5 ? 120 : 80, result.getWidth());
            assertEquals(orientation >= 5 ? 80 : 120, result.getHeight());
            for (int i = 0; i < 4; i++) {
                int pixel = result.getPixel(i % 2 == 0 ? 10 : result.getWidth() - 10, i / 2 == 0 ? 10 : result.getHeight() - 10);
                int expected = colors[corners[orientation - 1][i]];
                assertTrue("orientation=" + orientation + " corner=" + i, Math.abs(Color.red(pixel) - Color.red(expected)) < 25
                        && Math.abs(Color.green(pixel) - Color.green(expected)) < 25 && Math.abs(Color.blue(pixel) - Color.blue(expected)) < 25);
            }
            assertEquals(ExifInterface.ORIENTATION_NORMAL, new ExifInterface(output).getAttributeInt(ExifInterface.TAG_ORIENTATION, -1));
            result.recycle(); input.delete(); output.delete();
        }
    }
}
