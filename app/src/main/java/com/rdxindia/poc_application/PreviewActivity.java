package com.rdxindia.poc_application;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class PreviewActivity extends AppCompatActivity {

    private ImageView photoPreview;
    private ImageView backArrow;
    private Button galleryButton;

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

        // Back arrow returns to MainActivity
        backArrow.setOnClickListener(v -> finish());

        // Gallery button opens the device's gallery (or you can customize this)
        galleryButton.setOnClickListener(v -> {
            Intent galleryIntent = new Intent(Intent.ACTION_VIEW);
            galleryIntent.setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            startActivity(galleryIntent);
        });
    }
}
