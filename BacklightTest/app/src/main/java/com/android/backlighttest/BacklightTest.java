package com.android.backlighttest;

import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class BacklightTest extends Activity implements View.OnClickListener {

    private static final String TAG = "BacklightTest";

    private static final String KEY_TESTED_TIMES = "test_times";
    private static final String KEY_NOT_TESTED_TIMES = "not_test_times";
    private static final String KEY_CURRENT_SCREEN_ON_TIME = "current_screen_on_time";
    private static final String KEY_LAST_SCREEN_ON_TIME = "last_screen_on_time";
    private static final String KEY_LAST_TEST_RESULT = "last_test_result";

    private static final int MSG_SCREEN_OFF = 0;
    private static final int MSG_SCREEN_ON = 1;

    private EditText mTestTimesEt;
    private EditText mScreenOffTimeEt;
    private EditText mScreenOnTimeEt;
    private TextView mTestedTimesTv;
    private TextView mUntestedTimesTv;
    private TextView mCurrentScreenOnTimeTv;
    private TextView mLastScreenOnTimeTv;
    private TextView mTestResultTv;
    private Button mStartOrStopBt;

    private PowerManager mPowerManager;
    private SharedPreferences mSharedPreferences;
    private SimpleDateFormat mSimpleDateFormat;

    private long mTimes;
    private long mTestedTimes;
    private long mUntestTimes;
    private long mScreenOnTime;
    private long mScreenOffTime;
    private String mCurrentScreenOnTime;
    private String mLastScreenOnTime;
    private boolean mIsStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backlight_test);

        initValues();
        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mIsStart) {
            updateViews();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTest();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start_or_stop:
                if (!mIsStart) {
                    startTest();
                } else {
                    stopTest();
                }
                break;
        }
    }

    private void initValues() {
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        mSimpleDateFormat.setTimeZone(TimeZone.getDefault());
        mIsStart = false;
    }

    private void initViews() {
        mTestTimesEt = (EditText) findViewById(R.id.test_times);
        mScreenOffTimeEt = (EditText) findViewById(R.id.screen_off_time);
        mScreenOnTimeEt = (EditText) findViewById(R.id.screen_on_time);
        mTestedTimesTv = (TextView) findViewById(R.id.tested_times);
        mUntestedTimesTv = (TextView) findViewById(R.id.not_tested_time);
        mCurrentScreenOnTimeTv = (TextView) findViewById(R.id.current_screen_on_time);
        mLastScreenOnTimeTv = (TextView) findViewById(R.id.last_screen_on_time);
        mTestResultTv = (TextView) findViewById(R.id.test_result);
        mStartOrStopBt = (Button) findViewById(R.id.start_or_stop);

        mStartOrStopBt.setOnClickListener(this);
    }

    private void updateViews() {
        mTestedTimes = mSharedPreferences.getLong(KEY_TESTED_TIMES, -1);
        mUntestTimes = mSharedPreferences.getLong(KEY_NOT_TESTED_TIMES, -1);
        mCurrentScreenOnTime = mSharedPreferences.getString(KEY_CURRENT_SCREEN_ON_TIME, "");
        mLastScreenOnTime = mSharedPreferences.getString(KEY_LAST_SCREEN_ON_TIME, "");
        int lastTestResult = mSharedPreferences.getInt(KEY_LAST_TEST_RESULT, -1);

        if (mTestedTimes >= 0) {
            mTestedTimesTv.setText(mTestedTimes + "");
        }
        if (mUntestTimes >= 0) {
            mUntestedTimesTv.setText(mUntestTimes + "");
        }

        mCurrentScreenOnTimeTv.setText(mCurrentScreenOnTime);
        mLastScreenOnTimeTv.setText(mLastScreenOnTime);
        if (lastTestResult == 1) {
            mTestResultTv.setTextColor(0xff00ff00);
            mTestResultTv.setText(R.string.backlight_test_pass);
        } else if (lastTestResult == 0) {
            mTestResultTv.setTextColor(0xffff0000);
            mTestResultTv.setText(R.string.backlight_test_fail);
        }
    }

    private void wakeUp() {
        mPowerManager.wakeUp(SystemClock.uptimeMillis(), "backlight_test");
    }

    private void goToSleep() {
        mPowerManager.goToSleep(SystemClock.uptimeMillis());
    }

    private void startTest() {
        if (!mIsStart) {
            boolean enabledTest = checkTestParameter();
            Log.d(TAG, "startTest=>enabled: " + enabledTest);
            if (enabledTest) {
                mIsStart = true;
                mStartOrStopBt.setText(R.string.backlight_test_stop);
                mTestTimesEt.setEnabled(false);
                mScreenOffTimeEt.setEnabled(false);
                mScreenOnTimeEt.setEnabled(false);
                SharedPreferences.Editor e = mSharedPreferences.edit();
                e.putLong(KEY_TESTED_TIMES, 0);
                e.putLong(KEY_NOT_TESTED_TIMES, mTimes);
                e.putString(KEY_CURRENT_SCREEN_ON_TIME, mSimpleDateFormat.format(Calendar.getInstance().getTime()));
                e.remove(KEY_LAST_SCREEN_ON_TIME);
                e.commit();
                mHandler.sendEmptyMessage(MSG_SCREEN_OFF);
            }
            updateViews();
        }
    }

    private void stopTest() {
        if (mIsStart) {
            mTestTimesEt.setText("");
            mScreenOnTimeEt.setText("");
            mScreenOffTimeEt.setText("");
            mIsStart = false;
            mHandler.removeMessages(MSG_SCREEN_OFF);
            mHandler.removeMessages(MSG_SCREEN_ON);
            mStartOrStopBt.setText(R.string.backlight_test_start);
            mTestTimesEt.setEnabled(true);
            mScreenOffTimeEt.setEnabled(true);
            mScreenOnTimeEt.setEnabled(true);
            long untestedTime = mSharedPreferences.getLong(KEY_NOT_TESTED_TIMES, 0);
            if (untestedTime == 0) {
                mTestResultTv.setTextColor(0xff00ff00);
                mTestResultTv.setText(R.string.backlight_test_pass);
                mSharedPreferences.edit().putInt(KEY_LAST_TEST_RESULT, 1).commit();
            } else {
                mTestResultTv.setTextColor(0xffff0000);
                mTestResultTv.setText(R.string.backlight_test_fail);
                mSharedPreferences.edit().putInt(KEY_LAST_TEST_RESULT, 0).commit();
            }
        }
    }

    private boolean checkTestParameter() {
        boolean enabled = true;
        String timesStr = mTestTimesEt.getText().toString().trim();
        String screenOnTimeStr = mScreenOnTimeEt.getText().toString().trim();
        String screenOffTimeStr = mScreenOffTimeEt.getText().toString().trim();
        try {
            mTimes = Long.parseLong(timesStr);
            if (mTimes <= 0) {
                Toast.makeText(this, getString(R.string.backlight_test_times_limit, Long.MAX_VALUE), Toast.LENGTH_SHORT).show();
                mTestTimesEt.setText("");
                mTimes = 0;
                enabled = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "checkTestParameter=>error: ", e);
            Toast.makeText(this, getString(R.string.backlight_test_times_limit, Long.MAX_VALUE), Toast.LENGTH_SHORT).show();
            mTestTimesEt.setText("");
            mTimes = 0;
            enabled = false;
        }

        try {
            mScreenOnTime = Long.parseLong(screenOnTimeStr);
            if (mScreenOnTime <= 0) {
                Toast.makeText(this, getString(R.string.backlight_test_screen_on_time_limit, Long.MAX_VALUE), Toast.LENGTH_SHORT).show();
                mScreenOnTimeEt.setText("");
                mScreenOnTime = 0;
                enabled = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "checkTestParameter=>error: ", e);
            Toast.makeText(this, getString(R.string.backlight_test_screen_on_time_limit, Long.MAX_VALUE), Toast.LENGTH_SHORT).show();
            mScreenOnTimeEt.setText("");
            mScreenOnTime = 0;
            enabled = false;
        }

        try {
            mScreenOffTime = Long.parseLong(screenOffTimeStr);
            if (mScreenOffTime  <= 0) {
                Toast.makeText(this, getString(R.string.backlight_test_screen_off_time_limit, Long.MAX_VALUE), Toast.LENGTH_SHORT).show();
                mScreenOffTimeEt.setText("");
                mScreenOffTime = 0;
                enabled = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "checkTestParameter=>error: ", e);
            Toast.makeText(this, getString(R.string.backlight_test_screen_off_time_limit, Long.MAX_VALUE), Toast.LENGTH_SHORT).show();
            mScreenOffTimeEt.setText("");
            mScreenOffTime = 0;
            enabled = false;
        }

        return enabled;
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SCREEN_OFF:
                    goToSleep();
                    mHandler.sendEmptyMessageDelayed(MSG_SCREEN_ON, mScreenOffTime * 1000);
                    break;

                case MSG_SCREEN_ON:
                    wakeUp();
                    long untestedTimes = mSharedPreferences.getLong(KEY_NOT_TESTED_TIMES, 0);
                    SharedPreferences.Editor e = mSharedPreferences.edit();
                    e.putString(KEY_LAST_SCREEN_ON_TIME, mSharedPreferences.getString(KEY_CURRENT_SCREEN_ON_TIME, ""));
                    e.putString(KEY_CURRENT_SCREEN_ON_TIME, mSimpleDateFormat.format(Calendar.getInstance().getTime()));
                    e.putLong(KEY_TESTED_TIMES, mSharedPreferences.getLong(KEY_TESTED_TIMES, 0) + 1);
                    e.putLong(KEY_NOT_TESTED_TIMES, untestedTimes - 1);
                    e.commit();
                    Log.d(TAG, "handleMessage=>untestedTime: " + untestedTimes);
                    if (untestedTimes - 1 > 0) {
                        mHandler.sendEmptyMessageDelayed(MSG_SCREEN_OFF, mScreenOnTime * 1000);
                    } else {
                        stopTest();
                    }
                    break;
            }
            updateViews();
        }
    };


}
