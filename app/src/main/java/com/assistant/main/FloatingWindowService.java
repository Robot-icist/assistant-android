package com.assistant.main;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.assistant.main.R;

public class FloatingWindowService extends Service {

    private Context mContext;
    private WindowManager mWindowManager;
    private View mView;

    WindowManager.LayoutParams mWindowsParams;

    private boolean wasInFocus = true;
    private EditText edt1;

    protected int previousEvent = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("FloatingWindowService", "started");
        mContext = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        allAboutLayout(intent);
        moveView();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {

        if (mView != null) {
            mWindowManager.removeView(mView);
        }
        super.onDestroy();
    }

    private void allAboutLayout(Intent intent) {

        LayoutInflater layoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mView = layoutInflater.inflate(R.layout.overlay_window, null);

        //edt1 = (EditText) mView.findViewById(R.id.edt1);
        final TextView tvValue = (TextView) mView.findViewById(R.id.tvValue);
        //Button btnClose = (Button) mView.findViewById(R.id.btnClose);
        ImageView iv = (ImageView) mView.findViewById(R.id.imageView);
        iv.getLayoutParams().height = 90;
        iv.getLayoutParams().width = 90;
        iv.setImageResource(R.drawable.ic_stat_mic);
        /*iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tasks.restartMainActivity(getApplicationContext());
                mView.setVisibility(View.GONE);
            }
        });*/
        /*edt1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mWindowsParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                mWindowsParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
                mWindowManager.updateViewLayout(mView, mWindowsParams);
                wasInFocus = true;
                showSoftKeyboard(v);
            }
        });

        edt1.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                tvValue.setText(edt1.getText());
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });*/

    }

    private void moveView() {
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        int width = (int) (metrics.widthPixels * 0.7f);
        int height = (int) (metrics.heightPixels * 0.45f);
        int size = 150;
        mWindowsParams = new WindowManager.LayoutParams(
                //width,//
                //WindowManager.LayoutParams.WRAP_CONTENT,
                size,
                //height,//
                //WindowManager.LayoutParams.WRAP_CONTENT,
                size,
                //WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,

                (Build.VERSION.SDK_INT <= 25) ? WindowManager.LayoutParams.TYPE_PHONE : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                ,
                //WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, // Not displaying keyboard on bg activity's EditText
                //WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, //Not work with EditText on keyboard
                PixelFormat.TRANSLUCENT);


        mWindowsParams.gravity = Gravity.TOP | Gravity.LEFT;
        //params.x = 0;
        mWindowsParams.y = metrics.heightPixels/2 - size/2;
        mWindowsParams.x = metrics.widthPixels/2 - size/2;
        GradientDrawable content = new GradientDrawable();
        content.setShape(GradientDrawable.OVAL);
        content.setColor(Color.BLACK);
        GradientDrawable shape =  new GradientDrawable();
        shape.setCornerRadius(size/2);
        shape.setColor(Color.BLACK);
        //mView.setBackground(shape);
        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(Color.WHITE), content, shape);
        mView.setBackground(ripple);
        mWindowManager.addView(mView, mWindowsParams);

        mView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            long startTime = System.currentTimeMillis();
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (System.currentTimeMillis() - startTime <= 500) {
                    return false;
                }
               if (isViewInBounds(mView, (int) (event.getRawX()), (int) (event.getRawY()))) {
                    editTextReceiveFocus();
                } else {
                    editTextDontReceiveFocus();
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        Log.i("MotionEvent: ","ACTION_DOWN");
                        initialX = mWindowsParams.x;
                        initialY = mWindowsParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        break;
                    case MotionEvent.ACTION_UP:
                        Log.i("MotionEvent: ","ACTION_UP");
                        if(previousEvent != MotionEvent.ACTION_MOVE){
                            Log.i("Motion: ", "true");
                            Tasks.restartMainActivity(getApplicationContext());
                            mView.setVisibility(View.GONE);
                            stopSelf();
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        Log.i("MotionEvent: ","ACTION_MOVE");

                        mWindowsParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        mWindowsParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(mView, mWindowsParams);
                        break;
                }
                previousEvent = event.getAction();
                return false;
            }
        });
    }

    private boolean isViewInBounds(View view, int x, int y) {
        Rect outRect = new Rect();
        int[] location = new int[2];
        view.getDrawingRect(outRect);
        view.getLocationOnScreen(location);
        outRect.offset(location[0], location[1]);
        return outRect.contains(x, y);
    }

    private void editTextReceiveFocus() {
        if (!wasInFocus) {
            mWindowsParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            mWindowManager.updateViewLayout(mView, mWindowsParams);
            wasInFocus = true;
        }
    }

    private void editTextDontReceiveFocus() {
        if (wasInFocus) {
            mWindowsParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            mWindowManager.updateViewLayout(mView, mWindowsParams);
            wasInFocus = false;
            hideKeyboard(mContext, edt1);
        }
    }

    private void hideKeyboard(Context context, View view) {
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public void showSoftKeyboard(View view) {
        if (view.requestFocus()) {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

}