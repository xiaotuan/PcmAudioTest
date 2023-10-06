package com.qty.pcmaudiotest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static String TAG = "MainActivity";

    private TextView mStateTv;
    private TextView mTimeTv;
    private Button mRecordBtn;
    private Button mStopRecordBtn;
    private Button mPlayBtn;
    private Button mStopPlayBtn;

    private PcmAudioHelper mPcmAudioHelper;
    private String mAudioFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPcmAudioHelper = new PcmAudioHelper();
        mAudioFilePath = getExternalFilesDir(Environment.DIRECTORY_MUSIC).getAbsolutePath() + File.separator + "audio.pcm";

        mStateTv = findViewById(R.id.state);
        mTimeTv = findViewById(R.id.time);
        mRecordBtn = findViewById(R.id.recording);
        mStopRecordBtn = findViewById(R.id.stop_recording);
        mPlayBtn = findViewById(R.id.playing);
        mStopPlayBtn = findViewById(R.id.stop_playing);

        mRecordBtn.setOnClickListener(this);
        mStopRecordBtn.setOnClickListener(this);
        mPlayBtn.setOnClickListener(this);
        mStopPlayBtn.setOnClickListener(this);

        mStateTv.setText("未知");
        mTimeTv.setText("00:00:00");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.recording:
                if (checkRecordPermission()) {
                    mStateTv.setText("正在录音");
                    mPcmAudioHelper.startRecording(mAudioFilePath, new PcmAudioHelper.RecordingCallback() {
                        @Override
                        public void onRecordingCompletion() {
                            Log.d(TAG, "onRecordingCompletion()...");
                        }

                        @Override
                        public void onRecordingError(String msg) {
                            Log.d(TAG, "onRecordingError=>msg: " + msg);
                            runOnUiThread(() -> {
                                mStateTv.setText("录音错误");
                            });
                        }

                        @Override
                        public void onRecordingTimeChanged(long time) {
                            Log.d(TAG, "onRecordingTimeChanged=>time: " + time);
                            runOnUiThread(() -> {
                                mTimeTv.setText(formatTime(time));
                                if (time >= 60 * 1000 + 500) {
                                    mPcmAudioHelper.stopRecording();
                                }
                            });
                        }
                    });
                } else {
                    Log.d(TAG, "Record permission denied.");
                }
                break;

            case R.id.stop_recording:
                mPcmAudioHelper.stopRecording();
                mStateTv.setText("录音完成");
                break;

            case R.id.playing:
                if (checkPlayPermission()) {
                    mStateTv.setText("正在播放");
                    mPcmAudioHelper.startPlaying(mAudioFilePath, new PcmAudioHelper.PlayingCallback() {
                        @Override
                        public void onPlayingCompletion() {
                            Log.d(TAG, "onPlayingCompletion()...");
                        }

                        @Override
                        public void onPlayingError(String msg) {
                            Log.d(TAG, "onPlayingError=>msg: " + msg);
                            runOnUiThread(() -> {
                                mStateTv.setText("播放错误");
                            });
                        }

                        @Override
                        public void onPlayingTimeChanged(long time) {
                            Log.d(TAG, "onPlayingTimeChanged=>time: " + time);
                            runOnUiThread(() -> {
                                mTimeTv.setText(formatTime(time));
                            });
                        }
                    });
                } else {
                    Log.d(TAG, "Play permission denied.");
                }
                break;

            case R.id.stop_playing:
                mPcmAudioHelper.stopPlaying();
                mStateTv.setText("播放停止");
                break;
        }
    }

    private boolean checkRecordPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record audio permission denied.");
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.RECORD_AUDIO }, 888);
            return false;
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                AppOpsManager appOps = getSystemService(AppOpsManager.class);
                int mode = appOps.unsafeCheckOpNoThrow(
                        "android:manage_external_storage",
                        getApplicationInfo().uid,
                        getPackageName()
                );
                if (mode != AppOpsManager.MODE_ALLOWED) {
                    Log.d(TAG, "Manager all files access permission denied.");
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, 777);
                    return false;
                }
            } else {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Write external storage permission denied.");
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 888);
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkPlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Read media audio permission denied.");
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.READ_MEDIA_AUDIO }, 888);
                return false;
            } else {
                AppOpsManager appOps = getSystemService(AppOpsManager.class);
                int mode = appOps.unsafeCheckOpNoThrow(
                        "android:manage_external_storage",
                        getApplicationInfo().uid,
                        getPackageName()
                );
                if (mode != AppOpsManager.MODE_ALLOWED) {
                    Log.d(TAG, "Manager all files access permission denied.");
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, 777);
                    return false;
                }
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 888);
                Log.d(TAG, "Read external storage permission denied.");
                return false;
            }
        }
        return true;
    }

    private String formatTime(long time) {
        time = time / 1000;
        int perMinute = 60;
        int perHour = 60 * 60;
        int hours = (int) (time / perHour);
        int minutes = (int)((time - hours * perHour) / perMinute);
        int seconds = (int)(time % perMinute);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}