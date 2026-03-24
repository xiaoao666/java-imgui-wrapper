    package com.xa.JavaImgui;

    import static android.graphics.PixelFormat.TRANSLUCENT;
    import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
    import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

    import android.app.Activity;
    import android.app.ActivityManager;
    import android.content.Context;
    import android.content.Intent;
    import android.content.pm.ConfigurationInfo;
    import android.os.Bundle;
    import android.provider.Settings;
    import android.util.Log;
    import android.view.Gravity;
    import android.view.WindowManager;
    import android.view.inputmethod.InputMethodManager;
    import android.widget.Toast;

    import androidx.appcompat.app.AppCompatActivity;

    public class MainActivity extends AppCompatActivity {
        private static final String TAG = "MainActivity";

        static {
            System.loadLibrary("JavaImgui");
        }


        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Logger.d(TAG, "onCreate");


            setContentView(R.layout.activity_main);
            MyGLSurfaceView.startMenu(this);
        }


    }