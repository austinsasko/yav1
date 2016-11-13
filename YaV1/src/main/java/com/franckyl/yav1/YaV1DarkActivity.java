package com.franckyl.yav1;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

/**
 * Created by franck on 2/11/14.
 */
public class YaV1DarkActivity extends Activity
{
    // activity for muting / dark screen the app
    private  GestureDetector gestureDetector = null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.yav1_dark_activity);
        // we make it full screen,
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener()
        {
            public void onLongPress(MotionEvent e)
            {
                restartAlert();
                return;
            }
        });

    }

    @Override
    public void onResume()
    {
        YaV1.superResume();
        super.onResume();
    }

    @Override
    public void onPause()
    {
        YaV1.superPause();;
        super.onPause();
    }

    public void onBackPressed()
    {
        restartAlert();
    }

    // handle the long press on back button

    public boolean onKeyLongPress(int keyCode, KeyEvent event)
    {
        if(keyCode == KeyEvent.KEYCODE_BACK)
        {
            restartAlert();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    public boolean onTouchEvent(MotionEvent event)
    {
        return gestureDetector.onTouchEvent(event);
    };

    private void restartAlert()
    {
        Intent intent = new Intent(YaV1.sContext, YaV1Activity.class);
        intent.putExtra("fromdark", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        YaV1.sContext.startActivity(intent);
        finish();
    }
}
