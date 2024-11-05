package com.example.emotiontrack;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_AUDIO_PERMISSION_CODE = 1;
    private static final String TAG = "MainActivity";
    private static final String API_URL = "https://erv-api.onrender.com/predict";
    private MediaRecorder mediaRecorder;
    private ImageButton micButton;
    private TextView resultTextView;
    private SoundWaveView soundWaveView;
    private ImageView logoImageView;
    private boolean isRecording = false;
    private String audioFilePath;
    private Animation pulseAnimation, fadeInAnimation;
    private OkHttpClient httpClient;
    private ExecutorService executorService;
    private Map<String, String> emotionEmojiMap;
    private Map<String, Integer> emotionColorMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        micButton = findViewById(R.id.micButton);
        resultTextView = findViewById(R.id.resultTextView);
        soundWaveView = findViewById(R.id.soundWaveView);
        logoImageView = findViewById(R.id.logoImageView);

        pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse);
        fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);

        httpClient = new OkHttpClient();
        executorService = Executors.newSingleThreadExecutor();

        // Emoji and color mapping for each emotion
        emotionEmojiMap = new HashMap<>();
        emotionEmojiMap.put("neutral", "😐");
        emotionEmojiMap.put("calm", "😌");
        emotionEmojiMap.put("happy", "😊");
        emotionEmojiMap.put("sad", "😢");
        emotionEmojiMap.put("angry", "😡");
        emotionEmojiMap.put("fearful", "😨");
        emotionEmojiMap.put("disgust", "🤢");
        emotionEmojiMap.put("surprised", "😲");
        emotionEmojiMap.put("excited", "🤩");

        emotionColorMap = new HashMap<>();
        emotionColorMap.put("neutral", Color.parseColor("#808080"));     // Grey
        emotionColorMap.put("calm", Color.parseColor("#ADD8E6"));       // Light Blue
        emotionColorMap.put("happy", Color.parseColor("#FFD700"));      // Gold
        emotionColorMap.put("sad", Color.parseColor("#4682B4"));        // Steel Blue
        emotionColorMap.put("angry", Color.parseColor("#FF6347"));      // Tomato
        emotionColorMap.put("fearful", Color.parseColor("#FF4500"));    // Orange Red
        emotionColorMap.put("disgust", Color.parseColor("#8B4513"));    // Saddle Brown
        emotionColorMap.put("surprised", Color.parseColor("#DA70D6"));  // Orchid
        emotionColorMap.put("excited", Color.parseColor("#FF69B4"));    // Hot Pink

        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION_CODE);
                } else {
                    startRecording();
                }
            }
        });
    }

    private void startRecording() {
        if (!isRecording) {
            if (mediaRecorder == null) {
                setupMediaRecorder();
            }

            startListeningAnimation();

            mediaRecorder.start();
            isRecording = true;

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopRecording();
                }
            }, 5000); // Record for 5 seconds
        }
    }

    private void setupMediaRecorder() {
        audioFilePath = getExternalCacheDir().getAbsolutePath() + "/audio.3gp";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(audioFilePath);
        try {
            mediaRecorder.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        mediaRecorder.stop();
        mediaRecorder.release();
        mediaRecorder = null;

        isRecording = false;
        logoImageView.clearAnimation();

        resultTextView.setText("Recording stopped. Analyzing emotion Please Wait...");
        soundWaveView.stopAnimation();
        soundWaveView.setVisibility(View.GONE);
        // Convert 3GP to WAV and send to API
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                File wavFile = convert3gpToWav(audioFilePath);
                if (wavFile != null) {
                    sendAudioToApi(wavFile);
                }
            }
        });
    }

    private File convert3gpToWav(String inputFilePath) {
        // Conversion logic from 3GP to WAV can be added here.
        // For this placeholder, we return the input file itself.
        // You can implement conversion using libraries like JAVE (Java Audio Video Encoder) if needed.
        return new File(inputFilePath); // Temporary placeholder
    }

    private void sendAudioToApi(File wavFile) {
        RequestBody fileBody = RequestBody.create(wavFile, MediaType.parse("audio/wav"));
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav", fileBody)
                .build();

        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    resultTextView.setText("Error: Failed to send audio");
                });
                Log.e(TAG, "API Request failed: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String predictedEmotion = jsonResponse.getString("predicted_emotion");
                        double confidence = jsonResponse.getDouble("confidence");

                        String emoji = emotionEmojiMap.getOrDefault(predictedEmotion, "");
                        int color = emotionColorMap.getOrDefault(predictedEmotion, Color.BLACK);

                        runOnUiThread(() -> {
                            resultTextView.setText(String.format("Your Emotion: %s %s\nAccuracy: %.2f%%", emoji, predictedEmotion, confidence));
                            resultTextView.startAnimation(fadeInAnimation);
                            resultTextView.setTextColor(color);
                            getWindow().getDecorView().setBackgroundColor(color);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            resultTextView.setText("Error: Failed to parse response");
                        });
                        Log.e(TAG, "Error parsing API response: " + e.getMessage());
                    }
                } else {
                    runOnUiThread(() -> {
                        resultTextView.setText("Error: API response error");
                    });
                }
            }
        });
    }

    private void startListeningAnimation() {
        soundWaveView.setVisibility(View.VISIBLE);
        soundWaveView.startAnimation();
        logoImageView.startAnimation(pulseAnimation);
        resultTextView.setText("Listening...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }
}
