package dev.memit.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Bake all eight EXIF orientations into pixels. Never crop or add a meme overlay here. */
public final class PhotoNormalizer {
    private PhotoNormalizer() {}

    public static void normalize(File input, File output) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(input.getPath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Не удалось прочитать снимок");
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap source = BitmapFactory.decodeFile(input.getPath(), options);
        if (source == null) throw new IOException("Не удалось прочитать снимок");
        Bitmap rotated = null;
        try {
            int orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix(orientation), true);
            try (FileOutputStream stream = new FileOutputStream(output)) {
                if (!rotated.compress(Bitmap.CompressFormat.JPEG, 90, stream)) throw new IOException("Не удалось сохранить снимок");
            }
            // New JPEG has no inherited camera EXIF; declare identity for every consumer.
            ExifInterface exif = new ExifInterface(output);
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(ExifInterface.ORIENTATION_NORMAL));
            exif.saveAttributes();
            if (output.length() > 10L * 1024 * 1024) throw new IOException("Снимок превышает 10 МБ");
        } finally {
            if (rotated != null && rotated != source) rotated.recycle();
            source.recycle();
        }
    }

    static Matrix matrix(int orientation) {
        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: m.setRotate(90); m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: m.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: m.setRotate(270); m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.setRotate(270); break;
            default: break;
        }
        return m;
    }
}
