package com.rdxindia.poc_application;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

public class ListeningService extends Service {

    private static final String CHANNEL_ID = "ListeningServiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, getNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Listening Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification getNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Listening Service")
                .setContentText("Listening for commands...")
                .setSmallIcon(R.drawable.ic_mic_black_24dp) // Replace with your icon
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Show a Toast message to indicate that the service is running
        Toast.makeText(this, "Listening Service is running", Toast.LENGTH_SHORT).show();
        // Start listening for commands here
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}