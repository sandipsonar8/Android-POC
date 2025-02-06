package com.rdxindia.poc_application;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String KEYWORD = "take a photo";
    //private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

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
//    private Button restartButton;
    private Button galleryButton;
    private FusedLocationProviderClient fusedLocationClient;

    // Handler and Runnable for periodic execution
    private Handler listeningHandler = new Handler();
    private Runnable listeningRunnable = new Runnable() {
        @Override
        public void run() {
            startListening();
            // Schedule the next execution in 10 seconds (5,000 milliseconds)
            listeningHandler.postDelayed(this, 5000);
        }
    };

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

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // Initialize UI components
        previewView = findViewById(R.id.cameraPreview);
        micIcon = findViewById(R.id.micIcon);
        statusText = findViewById(R.id.statusText);
        //restartButton = findViewById(R.id.restartButton);
        galleryButton = findViewById(R.id.galleryButton);

        // Set up restart button (if an error occurs)
//        restartButton.setOnClickListener(v -> {
//            restartButton.setVisibility(View.GONE);
//            startListening();
//        });

        galleryButton.setOnClickListener(v -> {
            Intent galleryIntent = new Intent(Intent.ACTION_VIEW);
            galleryIntent.setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            startActivity(galleryIntent);
        });
        // Check and request permissions
        checkAndRequestPermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Start the periodic execution when the activity starts
        listeningHandler.post(listeningRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Remove callbacks when the activity is no longer visible to avoid leaks
        listeningHandler.removeCallbacks(listeningRunnable);
    }

    private void checkAndRequestPermissions() {
        List<String> requiredPermissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
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
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }
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
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override
            public void onError(int error) {
                runOnUiThread(() -> {
                    micIcon.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.error_color));
                    statusText.setText("Error - tap to restart");
                    //restartButton.setVisibility(View.VISIBLE);
                });
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    for (String phrase : matches) {
                        if (phrase.toLowerCase().contains(KEYWORD)) {
                            takePhoto();
                            break;
                        }
                    }
                }
                startListening();
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });

        startListening();
    }

    private void takePhoto() {

        if (imageCapture == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hide microphone UI during capture
        runOnUiThread(() -> {
            micIcon.setVisibility(View.INVISIBLE);
            statusText.setVisibility(View.INVISIBLE);
        });

        // Check location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            saveImageWithLocation(null); // Save without location
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        saveImageWithLocation(location);
                    } else {
                        // Request fresh location
                        LocationRequest locationRequest = LocationRequest.create();
                        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                        locationRequest.setInterval(10000);
                        locationRequest.setFastestInterval(5000);
                        locationRequest.setNumUpdates(1);

                        fusedLocationClient.requestLocationUpdates(locationRequest,
                                new LocationCallback() {
                                    @Override
                                    public void onLocationResult(LocationResult locationResult) {
                                        fusedLocationClient.removeLocationUpdates(this);
                                        Location location = locationResult.getLastLocation();
                                        saveImageWithLocation(location);
                                    }
                                }, Looper.getMainLooper());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Location error: " + e.getMessage());
                    saveImageWithLocation(null);
                });

//        ContentValues contentValues = new ContentValues();
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
//        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + timeStamp);
//        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
//        }
//
//        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
//                getContentResolver(),
//                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//                contentValues)
//                .build();
//
//        imageCapture.takePicture(
//                outputOptions,
//                ContextCompat.getMainExecutor(this),
//                new ImageCapture.OnImageSavedCallback() {
//                    @Override
//                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
//                        runOnUiThread(() -> {
//                            Uri photoUri = outputFileResults.getSavedUri();
//                            if (photoUri != null) {
//                                // Launch PreviewActivity with the captured photo URI
//                                Intent intent = new Intent(MainActivity.this, PreviewActivity.class);
//                                intent.putExtra("photoUri", photoUri.toString());
//                                startActivity(intent);
//                            }
//                            Toast.makeText(MainActivity.this, "Photo saved!", Toast.LENGTH_SHORT).show();
//
//                            // Reset microphone UI visibility after capture
//                            micIcon.setVisibility(View.VISIBLE);
//                            statusText.setVisibility(View.VISIBLE);
//                        });
//                    }
//                    @Override
//                    public void onError(@NonNull ImageCaptureException exception) {
//                        Log.e(TAG, "Photo capture failed: " + exception.getMessage());
//                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Capture failed", Toast.LENGTH_SHORT).show());
//                    }
//                }
//        );
    }

    private void saveImageWithLocation(@Nullable Location location) {
        ContentValues contentValues = new ContentValues();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String displayName = "IMG_" + timeStamp;

        if (location != null) {
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            displayName += String.format(Locale.US, "_%.5f_%.5f", lat, lng);
        }

        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
        }

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
                .build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        runOnUiThread(() -> {
                            Uri photoUri = outputFileResults.getSavedUri();
                            if (photoUri != null) {
                                Intent intent = new Intent(MainActivity.this, PreviewActivity.class);
                                intent.putExtra("photoUri", photoUri.toString());
                                startActivity(intent);
                            }
                            Toast.makeText(MainActivity.this, "Photo saved!", Toast.LENGTH_SHORT).show();
                            micIcon.setVisibility(View.VISIBLE);
                            statusText.setVisibility(View.VISIBLE);
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Capture failed: " + exception.getMessage());
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Capture failed", Toast.LENGTH_SHORT).show();
                            micIcon.setVisibility(View.VISIBLE);
                            statusText.setVisibility(View.VISIBLE);
                        });
                    }
                });
    }

    private void startListening() {
        runOnUiThread(() -> {
            if (speechRecognizer != null) {
                speechRecognizer.startListening(recognizerIntent);
                micIcon.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.listening_color));
                statusText.setText("Listening...");
                //restartButton.setVisibility(View.GONE);
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