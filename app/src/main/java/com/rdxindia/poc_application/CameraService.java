package com.rdxindia.poc_application;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.rdxindia.poc_application.R;

public class CameraService extends Service {
    private static final String CHANNEL_ID = "CameraForegroundService";

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, createNotification());
    }

    private Notification createNotification() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Camera Service",
                NotificationManager.IMPORTANCE_LOW
        );
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Camera Running")
                .setContentText("Live preview is active")
                .setSmallIcon(R.drawable.ic_camera)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
