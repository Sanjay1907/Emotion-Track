package com.example.emotiontrack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class SoundWaveView extends View {

    private Paint paint;
    private float amplitude = 1.0f;
    private boolean isAnimating = false;

    public SoundWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setStrokeWidth(8);
        paint.setStyle(Paint.Style.STROKE);
    }

    public void startAnimation() {
        isAnimating = true;
        postInvalidate();
    }

    public void stopAnimation() {
        isAnimating = false;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isAnimating) {
            int width = getWidth();
            int height = getHeight();
            float centerX = width / 2f;
            float centerY = height / 2f;

            // Create a gradient effect for the waves
            int[] colors = {Color.parseColor("#FF6347"), Color.parseColor("#FFCA28"), Color.parseColor("#00BCD4")};
            Shader shader = new LinearGradient(0, 0, 0, height, colors, null, Shader.TileMode.CLAMP);
            paint.setShader(shader);

            // Draw multiple vertical wave lines for a richer effect
            for (int i = 0; i < 8; i++) {
                float lineLength = amplitude * (i + 1) * 15; // Adjust for varied line length
                float offset = i * 20; // Offset each line for better spacing

                // Draw vertical wave lines with varying lengths
                canvas.drawLine(centerX - offset, centerY - lineLength / 2, centerX - offset, centerY + lineLength / 2, paint);
                canvas.drawLine(centerX + offset, centerY - lineLength / 2, centerX + offset, centerY + lineLength / 2, paint);
            }

            // Update amplitude for the next frame
            amplitude += 0.05f; // Smoother speed adjustment
            if (amplitude > 5.0f) amplitude = 1.0f; // Reset amplitude for looping
            postInvalidateDelayed(50); // Redraw every 50ms for smoother animation
        }
    }
}
