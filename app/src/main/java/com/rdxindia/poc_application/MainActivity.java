package com.rdxindia.poc_application;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView statusText;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private Camera camera;
    private ExecutorService cameraExecutor;
    private FusedLocationProviderClient fusedLocationClient;
    private Location lastKnownLocation;

    // Receiver for voice commands
    private final BroadcastReceiver voiceCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("VOICE_COMMAND".equals(intent.getAction())) {
                String command = intent.getStringExtra("command");
                if (command != null) {
                    Log.d("MainActivity", "Voice command received: " + command);
                    onVoiceCommand(command);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.cameraPreview);
        statusText = findViewById(R.id.statusText);

        // Initialize the camera
        cameraExecutor = Executors.newSingleThreadExecutor();
        // Start Speech Recognition Service
        Intent speechServiceIntent = new Intent(this, SpeechRecognitionService.class);
        startService(speechServiceIntent);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fetchLocation();
        requestPermissions();
    }
    // Add location fetch method
    private void fetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            lastKnownLocation = location;
                        }
                    });
        }
    }

    // Update onResume() for proper receiver registration
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("VOICE_COMMAND");
        registerReceiver(voiceCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        if (cameraProvider == null) {
            initializeCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(voiceCommandReceiver);
    }

    // Request necessary permissions
    private void requestPermissions() {
        String[] permissions = { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION };

        ActivityResultLauncher<String[]> requestPermissionsLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                        result -> {
                            boolean allGranted = true;
                            for (Boolean granted : result.values()) {
                                if (!granted) {
                                    allGranted = false;
                                    break;
                                }
                            }
                            if (!allGranted) {
                                Toast.makeText(this, "Permissions required!", Toast.LENGTH_LONG).show();
                            } else {
                                initializeCamera();
                            }
                        });
        requestPermissionsLauncher.launch(permissions);
    }

    // Initialize the CameraProvider
    private void initializeCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                Log.d("MainActivity", "Camera initialized successfully.");
            } catch (Exception e) {
                Log.e("MainActivity", "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Handle voice command
    private void onVoiceCommand(String command) {
        Log.d("MainActivity", "Processing voice command: " + command);
        boolean containsPhotoCommand = command.contains("take a photo") || command.contains("zoom");
        boolean zoomCommand = command.contains("zoom");

        if (zoomCommand) {
            // Set zoom first
            setZoom(0.5f);
            updateStatusText("Zooming and capturing...");

            // Capture after 500ms delay to ensure zoom applied
            new Handler().postDelayed(() -> {
                takePhoto(true); // true = reset zoom after capture
            }, 500);
        }
        else if (containsPhotoCommand) {
            takePhoto(false); // Normal capture without zoom
        }
        else {
            updateStatusText("Unrecognized command: " + command);
        }
    }

    private void setZoom(float zoomLevel) {
        if (camera == null) {
            Log.e("MainActivity", "Camera not initialized, cannot set zoom.");
            updateStatusText("Camera not ready for zoom");
            return;
        }
        try {
            CameraControl control = camera.getCameraControl();
            control.setLinearZoom(zoomLevel); // Use parameter instead of hardcoded 0.5
            updateStatusText("Zoom set to " + (zoomLevel * 100) + "%");
        } catch (Exception e) {
            Log.e("MainActivity", "Error setting zoom", e);
            updateStatusText("Zoom failed");
        }
    }

    private void takePhoto(boolean resetZoom) {
        if (imageCapture == null) {
            Log.e("MainActivity", "ImageCapture is null");
            updateStatusText("Camera not ready");
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());

        // Create filename with location
        String locationString = "";
        if (lastKnownLocation != null) {
            locationString = String.format(Locale.US,
                    "_%.5f_%.5f",
                    lastKnownLocation.getLatitude(),
                    lastKnownLocation.getLongitude());
        }

        File picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File appDir = new File(picturesDir, "POC_App");
        if (!appDir.exists()) appDir.mkdirs();

        final File file = new File(appDir,
                "IMG_" + timeStamp + locationString + ".jpg");

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(file).build();

        imageCapture.takePicture(outputOptions, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        try {
                            // EXIF metadata handling
                            if (lastKnownLocation != null) {
                                ExifInterface exif = new ExifInterface(file.getAbsolutePath());
                                setExifLocation(exif, lastKnownLocation);
                                exif.saveAttributes();
                            }

                            // Overlay text on image
                            overlayCoordinatesOnImage(file);
                        } catch (IOException e) {
                            Log.e("PhotoSave", "Error processing image", e);
                        }

                        runOnUiThread(() -> {
                            String msg = "Photo saved: " + file.getName();
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                            updateStatusText("Photo saved!");
                        });

                        // Reset zoom if requested
                        if (resetZoom) setZoom(0.0f);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.e("MainActivity", "Capture failed: " + e.getMessage());
                        updateStatusText("Error taking photo");
                    }
                });

        updateStatusText("Capturing...");
    }

    // Helper methods
    private void setExifLocation(ExifInterface exif, Location location) throws IOException {
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDMS(lat));
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, lat >= 0 ? "N" : "S");
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDMS(lon));
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lon >= 0 ? "E" : "W");
    }

    private String convertToDMS(double coordinate) {
        coordinate = Math.abs(coordinate);
        int degrees = (int) coordinate;
        double minutes = (coordinate - degrees) * 60;
        int seconds = (int) ((minutes - (int) minutes) * 60);
        return degrees + "/1," + (int) minutes + "/1," + seconds + "/1";
    }

    private void overlayCoordinatesOnImage(File imageFile) {
        if (lastKnownLocation == null) return;

        try {
            String text = String.format(Locale.US,
                    "Lat: %.5f\nLon: %.5f\n%s",
                    lastKnownLocation.getLatitude(),
                    lastKnownLocation.getLongitude(),
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date()));

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setTextSize(48);
            paint.setAntiAlias(true);
            paint.setShadowLayer(5f, 2f, 2f, Color.BLACK);

            // Position text at bottom-left
            Rect bounds = new Rect();
            paint.getTextBounds(text, 0, text.length(), bounds);
            float x = 20;
            float y = bitmap.getHeight() - 20;

            canvas.drawText(text, x, y, paint);

            try (FileOutputStream out = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            }
        } catch (IOException e) {
            Log.e("Overlay", "Text overlay failed", e);
        }
    }

    private void updateStatusText(String text) {
        runOnUiThread(() -> statusText.setText(text));
    }
}