package com.rdxindia.poc_application;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class PreviewActivity extends AppCompatActivity {

    private ImageView photoPreview;
    private ImageView backArrow;
    private Button galleryButton;

    // Handler and Runnable for auto redirection
    private Handler autoRedirectHandler = new Handler();
    private Runnable autoRedirectRunnable = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        photoPreview = findViewById(R.id.photoPreview);
        backArrow = findViewById(R.id.backArrow);
        galleryButton = findViewById(R.id.galleryButton);

        // Retrieve the photo URI passed from MainActivity
        String photoUriString = getIntent().getStringExtra("photoUri");
        if (photoUriString != null) {
            Uri photoUri = Uri.parse(photoUriString);
            photoPreview.setImageURI(photoUri);
        } else {
            Toast.makeText(this, "Photo not found", Toast.LENGTH_SHORT).show();
        }

        // Start the auto redirection timer (10 seconds = 5000 ms)
        autoRedirectHandler.postDelayed(autoRedirectRunnable, 5000);

        // Back arrow returns to MainActivity (and cancels auto redirection)
        backArrow.setOnClickListener(v -> {
            autoRedirectHandler.removeCallbacks(autoRedirectRunnable);
            finish();
        });

        // Gallery button opens the device's gallery (and cancels auto redirection)
        galleryButton.setOnClickListener(v -> {
            autoRedirectHandler.removeCallbacks(autoRedirectRunnable);
            Intent galleryIntent = new Intent(Intent.ACTION_VIEW);
            galleryIntent.setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            startActivity(galleryIntent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove callbacks to avoid memory leaks if the activity is destroyed.
        autoRedirectHandler.removeCallbacks(autoRedirectRunnable);
    }
}