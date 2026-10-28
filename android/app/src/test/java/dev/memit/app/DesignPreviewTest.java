package dev.memit.app;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ApplicationProvider;
import dev.memit.app.widget.WidgetRefreshWorker;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Render the shipping Android layouts, including compact and enlarged-font variants. */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 35, qualifiers = "mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DesignPreviewTest {
    private Context context(float fontScale) {
        Context base = ApplicationProvider.getApplicationContext();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = fontScale;
        Context context = base.createConfigurationContext(config);
        context.setTheme(R.style.Theme_Memit);
        return context;
    }
    private void render(View view, int width, int height, String name) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        File directory = new File("build/reports/design");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        bitmap.recycle();
    }
    private Bitmap meme() {
        Bitmap b = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawColor(Color.rgb(234, 228, 245));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.rgb(81, 67, 130)); canvas.drawCircle(300, 300, 160, p);
        p.setColor(Color.rgb(255, 196, 52)); canvas.drawCircle(250, 275, 20, p); canvas.drawCircle(350, 275, 20, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(12); canvas.drawArc(240, 295, 360, 365, 0, 180, false, p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(48, 41, 79)); p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD)); p.setTextSize(54); p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Я В ШОКЕ", 300, 70, p);
        p.setTextSize(28); canvas.drawText("ТЕСТОВЫЙ МЕМ", 300, 565, p);
        return b;
    }
    @Test public void previewCameraAndWidgetAtSeveralSizes() throws Exception {
        Context normal = context(1f);
        View main = LayoutInflater.from(normal).inflate(R.layout.activity_main, null);
        ((TextView) main.findViewById(R.id.recipient)).setText("Аня → Максим · отправить другу");
        render(main, 360, 820, "camera-permission");
        Bitmap meme = meme();
        main.findViewById(R.id.camera_gate).setVisibility(View.GONE);
        main.findViewById(R.id.review).setVisibility(View.VISIBLE);
        ((ImageView) main.findViewById(R.id.review)).setImageBitmap(meme);
        ((TextView) main.findViewById(R.id.status)).setText("Всё нравится? Отправь этот момент другу.");
        main.findViewById(R.id.retake).setVisibility(View.VISIBLE);
        Button send = main.findViewById(R.id.action); send.setText("Отправить"); send.setEnabled(true);
        render(main, 360, 820, "camera-review");
        Context largeFont = context(1.3f);
        View compact = LayoutInflater.from(largeFont).inflate(R.layout.activity_main, null);
        ((TextView) compact.findViewById(R.id.recipient)).setText("Максим → Аня · отправить другу");
        render(compact, 320, 640, "camera-compact-large-font");
        AppSettings settings = new AppSettings(2, "http://10.0.2.2:8080/", true);
        for (int[] size : new int[][]{{220,280}, {300,400}, {460,280}}) {
            View widget = WidgetRefreshWorker.views(normal, settings, meme, "Для «Максим» · мем #12", "", false).apply(normal, new FrameLayout(normal));
            render(widget, size[0], size[1], "widget-" + size[0] + "x" + size[1]);
            assertEquals(ImageView.ScaleType.FIT_CENTER, ((ImageView) widget.findViewById(R.id.widget_image)).getScaleType());
            assertTrue(widget.findViewById(R.id.widget_refresh).getBottom() <= size[1]);
        }
        View empty = WidgetRefreshWorker.views(normal, settings, null, "Попросите друга отправить фото", "Пока нет мемов", false).apply(normal, new FrameLayout(normal));
        render(empty, 240, 280, "widget-empty");
        View offline = WidgetRefreshWorker.views(normal, settings, meme, "Не удалось обновить · сохранённый мем", "", false).apply(normal, new FrameLayout(normal));
        render(offline, 240, 280, "widget-offline");
        meme.recycle();
    }
}
