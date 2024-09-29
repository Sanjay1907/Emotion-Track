package com.example.emotiontrack;

import android.Manifest;
import android.content.pm.PackageManager;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private ProgressBar progressBar;
    private ImageView logoImageView;
    private boolean isRecording = false;
    private String audioFilePath;
    private Animation pulseAnimation;
    private OkHttpClient httpClient;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        micButton = findViewById(R.id.micButton);
        resultTextView = findViewById(R.id.resultTextView);
        progressBar = findViewById(R.id.progressBar);
        logoImageView = findViewById(R.id.logoImageView);

        pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse);
        httpClient = new OkHttpClient();
        executorService = Executors.newSingleThreadExecutor();

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
                    progressBar.setVisibility(View.GONE);
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

                        runOnUiThread(() -> {
                            resultTextView.setText("Your Emotion: " + predictedEmotion);
                            progressBar.setVisibility(View.GONE);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            resultTextView.setText("Error: Failed to parse response");
                            progressBar.setVisibility(View.GONE);
                        });
                        Log.e(TAG, "Error parsing API response: " + e.getMessage());
                    }
                } else {
                    runOnUiThread(() -> {
                        resultTextView.setText("Error: API response error");
                        progressBar.setVisibility(View.GONE);
                    });
                }
            }
        });
    }

    private void startListeningAnimation() {
        progressBar.setVisibility(View.VISIBLE);
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
        if (requestCode == REQUEST_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "Permission denied! Cannot record audio without permission.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
