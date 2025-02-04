package com.rdxindia.poc_application;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS_CODE = 101;
    private final String keyword = "take a photo";

    // Voice recognition components
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;

    // Camera components
    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;

    // UI Components
    private ImageView micIcon;
    private TextView statusText;
    private Button restartButton;

    // Add these new variables to the class
    private ImageView photoPreview;
    private Button btnRetake;
    private boolean isPreviewVisible = false;

    // Permissions launcher
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean allGranted = true;
                        for (Boolean granted : result.values()) {
                            allGranted &= granted;
                        }
                        if (allGranted) {
                            initializeCamera();
                            initializeSpeechRecognizer();
                        } else {
                            Toast.makeText(this, "Permissions required!", Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        previewView = findViewById(R.id.cameraPreview);
        micIcon = findViewById(R.id.micIcon);
        statusText = findViewById(R.id.statusText);
        restartButton = findViewById(R.id.restartButton);

        photoPreview = findViewById(R.id.photoPreview);
        btnRetake = findViewById(R.id.btnRetake);

        // Set up retake button
        btnRetake.setOnClickListener(v -> {
            restartCameraAndListening();
            photoPreview.setVisibility(View.GONE);
            btnRetake.setVisibility(View.GONE);
            isPreviewVisible = false;

        });

        // Set up restart button
        restartButton.setOnClickListener(v -> {
            restartButton.setVisibility(View.GONE);
            startListening();
        });

        // Check and request permissions
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        List<String> requiredPermissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CAMERA);
        }

        if (!requiredPermissions.isEmpty()) {
            requestPermissionsLauncher.launch(requiredPermissions.toArray(new String[0]));
        } else {
            initializeCamera();
            initializeSpeechRecognizer();
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
        if (cameraProvider == null) return;

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
    }

    private void initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    runOnUiThread(() -> {
                        micIcon.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.listening_color));
                        statusText.setText("Listening...");
                    });
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    runOnUiThread(() -> {
                        micIcon.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.error_color));
                        statusText.setText("Error - tap to restart");
                        restartButton.setVisibility(View.VISIBLE);
                    });
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null) {
                        for (String phrase : matches) {
                            if (phrase.toLowerCase().contains(keyword)) {
                                takePhoto();
                                break;
                            }
                        }
                    }
                    startListening();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            startListening();
        }
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hide microphone UI when taking photo
        runOnUiThread(() -> {
            micIcon.setVisibility(View.INVISIBLE);
            statusText.setVisibility(View.INVISIBLE);
        });

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
                contentValues
        ).build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        runOnUiThread(() -> {
                            // Show preview of captured image
                            if (outputFileResults.getSavedUri() != null) {
                                photoPreview.setImageURI(outputFileResults.getSavedUri());
                                photoPreview.setVisibility(View.VISIBLE);
                                btnRetake.setVisibility(View.VISIBLE);
                                isPreviewVisible = true;

                                // Hide camera preview
                                previewView.setVisibility(View.GONE);
                            }

                            // Show confirmation
                            Toast.makeText(MainActivity.this, "Photo saved!", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage());
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Capture failed", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    // Add this new method
    private void restartCameraAndListening() {
        previewView.setVisibility(View.VISIBLE);
        micIcon.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
        // Restart voice listening
        startListening();
        // Re-initialize camera
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        initializeCamera();
    }

    private void startListening() {
        if (isPreviewVisible) return;
        runOnUiThread(() -> {
            if (speechRecognizer != null) {
                speechRecognizer.startListening(recognizerIntent);
                micIcon.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.listening_color));
                statusText.setText("Listening...");
                restartButton.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}