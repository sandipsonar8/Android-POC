package com.rdxindia.poc_application;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.*;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private PreviewView previewView;
    private TextView statusText;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;

    private HashSet<Integer> detectedObjectIds = new HashSet<>();
    private Timer resetTimer = new Timer();
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService photoExecutor;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean allGranted = result.values().stream().allMatch(granted -> granted);
                        if (allGranted) initializeCamera();
                        else Toast.makeText(this, "Permissions required!", Toast.LENGTH_LONG).show();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.cameraPreview);
        statusText = findViewById(R.id.statusText);

        checkAndRequestPermissions();

        // Schedule reset of detected objects every 10 seconds
        resetTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                detectedObjectIds.clear();
            }
        }, 0, 10000);

        photoExecutor = Executors.newSingleThreadExecutor();
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.CAMERA});
        } else {
            initializeCamera();
        }
    }

    private void initializeCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(new Size(1920, 1080))
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::processImage);

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(ImageProxy imageProxy) {
        imageExecutor.execute(() -> {
            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

            ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .build();

            ObjectDetector objectDetector = ObjectDetection.getClient(options);

            objectDetector.process(image)
                    .addOnSuccessListener(detectedObjects -> {
                        for (DetectedObject obj : detectedObjects) {
                            if (obj.getTrackingId() != null && !detectedObjectIds.contains(obj.getTrackingId())) {
                                detectedObjectIds.add(obj.getTrackingId());
                                runOnUiThread(() -> statusText.setText("New object detected! Capturing..."));
                                takePhoto();
                                resetDetectedObjects();
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Object detection failed", e))
                    .addOnCompleteListener(task -> imageProxy.close());
        });
    }

    private void resetDetectedObjects() {
        new Handler(Looper.getMainLooper()).postDelayed(detectedObjectIds::clear, 5000);
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is null. Cannot take a photo.");
            return;
        }

        photoExecutor.execute(() -> {
            ContentValues contentValues = new ContentValues();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + timeStamp);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            }

            ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                    getContentResolver(),
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues)
                    .build();

            imageCapture.takePicture(outputOptions, photoExecutor,
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            runOnUiThread(() -> statusText.setText("Photo captured!"));
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(TAG, "Capture failed: " + exception.getMessage());
                        }
                    });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraProvider != null) {
            bindCameraUseCases();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        resetTimer.cancel();
        if (photoExecutor != null && !photoExecutor.isShutdown()) {
            photoExecutor.shutdown();
        }
    }
}