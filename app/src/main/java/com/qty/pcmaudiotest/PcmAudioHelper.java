package com.qty.pcmaudiotest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Timer;
import java.util.TimerTask;

public class PcmAudioHelper {

    private static final String TAG = PcmAudioHelper.class.getSimpleName();
    private static final int IN_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int OUT_CHANNEL = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, IN_CHANNEL, AUDIO_FORMAT) * 2;

    private AudioRecord mRecorder;
    private AudioTrack mPlayer;
    private RecordThread mRecordThread;
    private PlayThread mPlayThread;

    private boolean isRecording;
    private boolean isPlaying;

    @SuppressLint("MissingPermission")
    public PcmAudioHelper() {
        mRecorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                IN_CHANNEL,
                AUDIO_FORMAT,
                BUFFER_SIZE
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setChannelMask(OUT_CHANNEL)
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AUDIO_FORMAT)
                    .build();
            mPlayer = new AudioTrack.Builder()
                    .setAudioFormat(audioFormat)
                    .build();
        } else {
            mPlayer = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    OUT_CHANNEL,
                    AUDIO_FORMAT,
                    BUFFER_SIZE,
                    AudioTrack.MODE_STREAM
            );
        }
    }

    public void startRecording(String filePath, RecordingCallback cb) {
        if (filePath != null) {
            try {
                File file = new File(filePath);
                if (file.exists() && file.isFile()) {
                    file.delete();
                }
                boolean success = file.createNewFile();
                if (success) {
                    if (mRecorder.getState() == AudioRecord.STATE_INITIALIZED) {
                        mRecordThread = new RecordThread(filePath, cb);
                        mRecordThread.start();
                    } else {
                        if (cb != null) {
                            cb.onRecordingError("Audio recorder is not properly initialized, state: " + mRecorder.getState());
                        }
                    }
                } else {
                    Log.e(TAG, "startRecording=>Create file fail.");
                    if (cb != null) {
                        cb.onRecordingError("Create file fail.");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "startRecording=>error: ", e);
                if (cb != null) {
                    cb.onRecordingError("Recording error: " + e.getLocalizedMessage());
                }
            }
        } else {
            if (cb != null) {
                cb.onRecordingError("File path is null.");
            }
        }
    }

    public void stopRecording() {
        if (isRecording && mRecordThread != null) {
            mRecordThread.stopRecording();
        }
    }

    public void startPlaying(String filePath, PlayingCallback cb) {
        stopPlaying();
        File audioFile = new File(filePath);
        if (filePath == null || !audioFile.exists() || !audioFile.isFile() || !audioFile.getAbsolutePath().endsWith(".pcm")) {
            if (cb != null) {
                cb.onPlayingError("File: " + filePath + ", is null or not a pcm file.");
            }
        } else {
            mPlayThread = new PlayThread(filePath, cb);
            mPlayThread.start();
        }
    }

    public void stopPlaying() {
        if (isPlaying && mPlayThread != null) {
            mPlayThread.stopPlaying();
            mPlayThread = null;
        }
    }

    private class RecordThread extends Thread {

        private String mFilePath;
        private RecordingCallback mCallback;
        private Timer mTimer;
        private TimerTask mTimeTask;
        private long mStartTime;
        private boolean isStopRecording;

        public RecordThread(String filePath, RecordingCallback cb) {
            mFilePath = filePath;
            mCallback = cb;
        }

        @Override
        public void run() {
            Log.d(TAG, "run=>start recording...");
            try (FileOutputStream fos = new FileOutputStream(mFilePath, true)){
                mRecorder.startRecording();
                isRecording = true;
                byte[] data = new byte[BUFFER_SIZE];
                mStartTime = SystemClock.elapsedRealtime();
                int length = mRecorder.read(data, 0, BUFFER_SIZE);
                mTimer = new Timer();
                mTimeTask = new TimerTask() {
                    @Override
                    public void run() {
                        if (mCallback != null) {
                            mCallback.onRecordingTimeChanged(SystemClock.elapsedRealtime() - mStartTime);
                        }
                    }
                };
                mTimer.schedule(mTimeTask, 0, 50);
                while (!isStopRecording && length > 0) {
                    fos.write(data, 0, length);
                    length = mRecorder.read(data, 0, BUFFER_SIZE);
                }
                isRecording = false;
                mTimer.cancel();
                mRecorder.stop();
                if (mCallback != null) {
                    mCallback.onRecordingCompletion();
                }
            } catch (Exception e) {
                Log.e(TAG, "run=>error: ", e);
                if (mCallback != null) {
                    mCallback.onRecordingError("Record error: " + e.getLocalizedMessage());
                }
            }
            Log.d(TAG, "run=>Recording end...");
        }

        public void stopRecording() {
            isStopRecording = true;
            try {
                interrupted();
            } catch (Exception ignore) {}
        }
    }

    private class PlayThread extends Thread {
        private String mFilePath;
        private PlayingCallback mCallback;
        private Timer mTimer;
        private TimerTask mTimeTask;
        private long mStartTime;
        private boolean isStopPlaying;

        public PlayThread(String filePath, PlayingCallback cb) {
            mFilePath = filePath;
            mCallback = cb;
        }

        @Override
        public void run() {
            Log.d(TAG, "run=>Start playing....");
            try (FileInputStream fis = new FileInputStream(new File(mFilePath))){
                mPlayer.play();
                isPlaying = true;
                byte[] data = new byte[BUFFER_SIZE];
                mStartTime = SystemClock.elapsedRealtime();
                int length = fis.read(data);
                mTimer = new Timer();
                mTimeTask = new TimerTask() {
                    @Override
                    public void run() {
                        if (mCallback != null) {
                            mCallback.onPlayingTimeChanged(SystemClock.elapsedRealtime() - mStartTime);
                        }
                    }
                };
                mTimer.schedule(mTimeTask, 0, 50);
                while (!isStopPlaying && length != -1) {
                    mPlayer.write(data, 0, length);
                    length = fis.read(data);
                }
                isPlaying = false;
                mTimer.cancel();
                mPlayer.stop();
                if (mCallback != null) {
                    mCallback.onPlayingCompletion();
                }
            } catch (Exception e) {
                Log.e(TAG, "run=>error: ", e);
                if (mCallback != null) {
                    mCallback.onPlayingError("Play error: " + e.getLocalizedMessage());
                }
            }
            Log.d(TAG, "run=>Playing end....");
        }

        public void stopPlaying() {
            isStopPlaying = true;
            try {
                interrupted();
            } catch (Exception ignore) {}
        }
    }

    public interface RecordingCallback {
        void onRecordingCompletion();
        void onRecordingError(String msg);
        void onRecordingTimeChanged(long time);
    }

    public interface PlayingCallback {
        void onPlayingCompletion();
        void onPlayingError(String msg);
        void onPlayingTimeChanged(long time);
    }
}
