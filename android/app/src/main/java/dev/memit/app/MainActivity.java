package dev.memit.app;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.common.util.concurrent.ListenableFuture;
import dev.memit.app.widget.MemitWidget;
import dev.memit.app.widget.WidgetRefreshWorker;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private CaptureViewModel model;
    private PreviewView camera;
    private ImageView review;
    private Button action, retake, settingsButton, switchButton, permissionButton;
    private TextView status, recipient, cameraMessage;
    private View gate, progress;
    private ProcessCameraProvider provider;
    private ImageCapture imageCapture;
    private int lens = CameraSelector.LENS_FACING_BACK;
    private boolean cameraReady, cameraFailed;
    private int bindGeneration;
    private OrientationEventListener orientation;
    private final ExecutorService settingsIo = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else {
                    cameraReady = false;
                    cameraMessage.setText("Камера выключена. Разрешение можно включить в настройках приложения.");
                    render(model.current());
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        camera = findViewById(R.id.camera);
        camera.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        camera.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        review = findViewById(R.id.review);
        action = findViewById(R.id.action);
        retake = findViewById(R.id.retake);
        settingsButton = findViewById(R.id.settings);
        switchButton = findViewById(R.id.switch_camera);
        permissionButton = findViewById(R.id.permission);
        status = findViewById(R.id.status);
        recipient = findViewById(R.id.recipient);
        cameraMessage = findViewById(R.id.camera_message);
        gate = findViewById(R.id.camera_gate);
        progress = findViewById(R.id.progress);
        model = new ViewModelProvider(this).get(CaptureViewModel.class);
        model.state().observe(this, this::render);
        settingsButton.setOnClickListener(v -> showSettings());
        permissionButton.setOnClickListener(v -> {
            if (hasPermission()) { startCamera(); return; }
            boolean asked = getPreferences(MODE_PRIVATE).getBoolean("camera_requested", false);
            if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } else {
                getPreferences(MODE_PRIVATE).edit().putBoolean("camera_requested", true).apply();
                permission.launch(Manifest.permission.CAMERA);
            }
        });
        switchButton.setOnClickListener(v -> {
            lens = lens == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            startCamera();
        });
        action.setOnClickListener(v -> {
            if (model.current().busy) return;
            if (!AppSettings.load(this).configured) { showSettings(); return; }
            if (model.current().photo != null) model.send(AppSettings.load(this));
            else capture();
        });
        retake.setOnClickListener(v -> model.retake());
        findViewById(R.id.add_widget).setOnClickListener(v -> {
            AppWidgetManager manager = getSystemService(AppWidgetManager.class);
            if (manager.isRequestPinAppWidgetSupported()) {
                manager.requestPinAppWidget(new ComponentName(this, MemitWidget.class), null, null);
            } else {
                new AlertDialog.Builder(this).setTitle("Мемы на рабочем столе")
                        .setMessage("Зажмите свободное место на рабочем столе → Виджеты → Memit. Добавьте виджет и нажмите «Обновить».")
                        .setPositiveButton("Понятно", null).show();
            }
        });
        orientation = new OrientationEventListener(this) {
            @Override public void onOrientationChanged(int degrees) {
                if (degrees == ORIENTATION_UNKNOWN || imageCapture == null) return;
                int rotation = degrees >= 45 && degrees < 135 ? Surface.ROTATION_270
                        : degrees >= 135 && degrees < 225 ? Surface.ROTATION_180
                        : degrees >= 225 && degrees < 315 ? Surface.ROTATION_90 : Surface.ROTATION_0;
                imageCapture.setTargetRotation(rotation);
            }
        };
        if (savedInstanceState == null && !AppSettings.load(this).configured) root.post(this::showSettings);
    }

    @Override protected void onResume() {
        super.onResume();
        orientation.enable();
        if (hasPermission()) startCamera();
        else { cameraReady = false; render(model.current()); }
        WidgetRefreshWorker.enqueue(this, false);
    }

    @Override protected void onPause() { orientation.disable(); super.onPause(); }
    @Override protected void onDestroy() { settingsIo.shutdown(); super.onDestroy(); }
    private boolean hasPermission() { return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED; }

    private void startCamera() {
        if (!hasPermission()) return;
        int generation = ++bindGeneration;
        cameraReady = false;
        cameraFailed = false;
        cameraMessage.setText("Включаем камеру…");
        render(model.current());
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (isDestroyed() || generation != bindGeneration) return;
            try {
                provider = future.get();
                CameraSelector selector = new CameraSelector.Builder().requireLensFacing(lens).build();
                if (!provider.hasCamera(selector)) {
                    lens = lens == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
                    selector = new CameraSelector.Builder().requireLensFacing(lens).build();
                }
                Preview preview = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
                preview.setSurfaceProvider(camera.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3).setTargetRotation(camera.getDisplay() == null ? Surface.ROTATION_0 : camera.getDisplay().getRotation()).build();
                provider.unbindAll();
                Camera bound = provider.bindToLifecycle(this, selector, preview, imageCapture);
                bound.getCameraInfo().getCameraState().observe(this, state -> {
                    if (generation != bindGeneration) return;
                    if (state.getError() != null) {
                        cameraFailed = true;
                        cameraReady = false;
                        cameraMessage.setText("Камера недоступна. Закройте другие приложения с камерой и повторите.");
                    } else if (state.getType() == CameraState.Type.OPEN) {
                        cameraReady = true;
                        cameraFailed = false;
                    }
                    render(model.current());
                });
            } catch (Exception e) {
                cameraFailed = true;
                cameraReady = false;
                cameraMessage.setText("Не удалось открыть камеру. Проверьте разрешение и повторите.");
            }
            render(model.current());
        }, ContextCompat.getMainExecutor(this));
    }

    private void capture() {
        if (!cameraReady || imageCapture == null || model.current().busy) return;
        try {
            File output = model.beginCapture();
            if (output == null) return;
            imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(output).build(), ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
                @Override public void onImageSaved(ImageCapture.OutputFileResults results) { model.captured(output); }
                @Override public void onError(ImageCaptureException error) { model.captureError(); }
            });
        } catch (Exception e) { model.captureError(); }
    }

    private void render(CaptureViewModel.State state) {
        AppSettings settings = AppSettings.load(this);
        recipient.setText(getString(R.string.recipient_direction, settings.userName(), settings.recipientName()));
        boolean hasPhoto = state.photo != null;
        review.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        review.setImageBitmap(state.preview);
        gate.setVisibility(!hasPhoto && !cameraReady ? View.VISIBLE : View.GONE);
        permissionButton.setText(!hasPermission() ? "Разрешить камеру" : "Повторить");
        permissionButton.setVisibility(!hasPermission() || cameraFailed ? View.VISIBLE : View.GONE);
        status.setText(state.message);
        progress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        action.setText(state.busy ? "Подождите…" : hasPhoto ? "Отправить" : "Сделать снимок");
        action.setEnabled(!state.busy && (hasPhoto || cameraReady));
        action.setAlpha(action.isEnabled() ? 1f : 0.5f);
        retake.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        retake.setEnabled(!state.busy);
        settingsButton.setEnabled(!state.busy);
        switchButton.setVisibility(!hasPhoto && cameraReady ? View.VISIBLE : View.GONE);
        switchButton.setEnabled(!state.busy);
    }

    private void showSettings() {
        if (isFinishing() || model.current().busy) return;
        AppSettings current = AppSettings.load(this);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, 0);
        TextView heading = new TextView(this);
        heading.setText("Кто пользуется этим устройством? Получатель — второй участник.");
        content.addView(heading);
        RadioGroup users = new RadioGroup(this);
        for (int id = 1; id <= 2; id++) {
            RadioButton choice = new RadioButton(this);
            choice.setId(id);
            choice.setText(id == 1 ? "Аня · ID 1" : "Максим · ID 2");
            choice.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
            users.addView(choice);
        }
        users.check(current.userId);
        content.addView(users);
        EditText address = new EditText(this);
        address.setSingleLine();
        address.setText(current.baseUrl.toString());
        address.setHint("Адрес backend");
        address.setContentDescription("Адрес backend");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        content.addView(address);
        TextView help = new TextView(this);
        help.setText("Эмулятор: http://10.0.2.2:8080/\nТелефон USB: http://127.0.0.1:8080/\nпосле adb reverse tcp:8080 tcp:8080\nWi-Fi: http://IP-компьютера:8080/\n\nОдин пользователь и сервер для приложения и всех его виджетов.");
        help.setTextSize(13);
        content.addView(help);
        Button check = new Button(this);
        check.setText("Проверить подключение");
        content.addView(check);
        TextView checkResult = new TextView(this);
        checkResult.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(checkResult);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Твой Memit").setView(scroll)
                .setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
        check.setOnClickListener(v -> {
            try {
                AppSettings draft = new AppSettings(users.getCheckedRadioButtonId(), address.getText().toString(), true);
                check.setEnabled(false);
                checkResult.setText(getString(R.string.checking_server, draft.baseUrl.toString()));
                settingsIo.execute(() -> {
                    String result;
                    try { new ApiClient(draft).health(); result = "Сервер доступен: " + draft.baseUrl; }
                    catch (Exception e) { result = ApiClient.readableError(e); }
                    String finalResult = result;
                    runOnUiThread(() -> {
                        if (dialog.isShowing() && !isDestroyed()) { check.setEnabled(true); checkResult.setText(finalResult); }
                    });
                });
            } catch (IllegalArgumentException e) { address.setError(e.getMessage()); }
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                AppSettings updated = new AppSettings(users.getCheckedRadioButtonId(), address.getText().toString(), true);
                updated.save(this);
                if (!updated.cacheKey().equals(current.cacheKey())) model.retake();
                WidgetRefreshWorker.enqueue(this, true);
                render(model.current());
                dialog.dismiss();
            } catch (IllegalArgumentException e) { address.setError(e.getMessage()); }
            catch (IllegalStateException e) { checkResult.setText(e.getMessage()); }
        }));
        dialog.show();
    }
}
