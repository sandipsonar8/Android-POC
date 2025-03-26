package com.rdxindia.poc_application;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SpeechRecognitionService extends Service {

    private static final String CHANNEL_ID = "SpeechServiceChannel";
    private static final int NOTIFICATION_ID = 101;
    private SpeechRecognizer speechRecognizer;
    private final Handler handler = new Handler();
    private boolean isDestroyed = false;
    private PowerManager.WakeLock wakeLock;

    // Supported voice commands
    private static final List<String> COMMANDS = Arrays.asList(
            "take a photo",
            "take photo",
            "zoom",
            "zoom in"
    );

    @Override
    public void onCreate() {
        super.onCreate();
        acquireWakeLock();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        startListeningCycle();
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "POCApp::SpeechWakeLock"
        );
        wakeLock.acquire(10 * 60 * 1000L); // 10 minutes
    }

    private void startListeningCycle() {
        if (isDestroyed) return;

        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            setupRecognitionListener();
            startListening();
        } catch (Exception e) {
            Log.e("SpeechService", "Init failed", e);
            restartListening(2000);
        }
    }

    private void setupRecognitionListener() {
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d("SpeechService", "Listening...");
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                restartListening(100);
            }

            @Override
            public void onError(int error) {
                handleError(error);
            }

            @Override
            public void onResults(Bundle results) {
                processResults(results);
                restartListening(100);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Log.e("SpeechService", "Listening failed", e);
        }
    }

    private void processResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String rawCommand = matches.get(0).toLowerCase();
            String detectedCommand = matchCommand(rawCommand);

            if (detectedCommand != null) {
                sendCommandToActivity(detectedCommand);
            }
        }
    }

    private String matchCommand(String input) {
        for (String command : COMMANDS) {
            if (input.contains(command)) {
                return command;
            }
        }
        return null;
    }

    private void sendCommandToActivity(String command) {
        Intent intent = new Intent("VOICE_COMMAND")
                .setPackage(getPackageName())
                .putExtra("command", command);
        sendBroadcast(intent);
    }

    private void handleError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
                restartListening(300);
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                restartListening(500);
                break;
            default:
                restartListening(1000);
        }
    }

    private void restartListening(int delay) {
        handler.postDelayed(this::startListeningCycle, delay);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Voice Control Active")
                .setContentText("Listening for commands...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Command Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background voice command processing");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        isDestroyed = true;
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}