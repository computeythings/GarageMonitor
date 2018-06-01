package computeythings.piopener.ui;

import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;

import computeythings.piopener.R;

class BackgroundAnimator {
    private final String TAG = "BackgroundAnimator";
    private ImageView background;
    private Handler animationHandler;
    private boolean running;

    public static final int CLOSING = 0;
    public static final int OPENING = 1;

    BackgroundAnimator() {
        this.animationHandler = new Handler();
        this.running = false;
    }

    void start(int animationValue) {
        running = true;
        switch (animationValue) {
            case CLOSING:
                new ClosingAnimation().run();
                break;
            case OPENING:
                new OpeningAnimation().run();
                break;
            default:
                running = false; // don't run if a valid animation was not given.
        }
    }

    void setBackground(ImageView background) {
        this.background = background;
    }

    void stop() {
        this.running = false;
        animationHandler.removeCallbacksAndMessages(null); // stop all animations
    }

    private class OpeningAnimation implements Runnable {
        private int index = 0;
        private final int[] res = {R.drawable.garage_7_8_opening, R.drawable.garage_5_8_opening,
                R.drawable.garage_3_8_opening, R.drawable.garage_1_8_opening,
                R.drawable.garage_opening};
        private final int[] duration = {400, 400, 400, 400, 700};

        @Override
        public void run() {
            if (running) {
                try {
                    background.setImageResource(res[index % res.length]);
                    animationHandler.postDelayed(this, duration[index % duration.length]);
                    index++;
                } catch (NullPointerException e) {
                    Log.e(TAG, "Background no longer exists. Stopping animation.");
                    e.printStackTrace();
                    running = false;
                }
            } else {
                animationHandler.removeCallbacks(this);
            }
        }
    }

    private class ClosingAnimation implements Runnable {
        private int index = 0;
        private final int[] res = {R.drawable.garage_1_8_closing, R.drawable.garage_3_8_closing,
                R.drawable.garage_5_8_closing, R.drawable.garage_7_8_closing,
                R.drawable.garage_closing};
        private final int[] duration = {400, 400, 400, 400, 700};

        @Override
        public void run() {
            if (running) {
                try {
                    background.setImageResource(res[index % res.length]);
                    animationHandler.postDelayed(this, duration[index % duration.length]);
                    index++;
                } catch (NullPointerException e) {
                    Log.e(TAG, "Background no longer exists. Stopping animation.");
                    e.printStackTrace();
                    running = false;
                }
            } else {
                animationHandler.removeCallbacks(this);
            }
        }
    }
}
