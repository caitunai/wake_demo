package ai.kitt.snowboy.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import ai.kitt.snowboy.Constants;
import ai.kitt.snowboy.MsgEnum;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import ai.kitt.snowboy.SnowboyDetect;

public class RecordingThread {
    static {
        System.loadLibrary("snowboy-detect-android");
    }

    private static final String TAG = RecordingThread.class.getSimpleName();

    private boolean shouldContinue;
    private final AudioDataReceivedListener listener;
    private final Handler handler;
    private Thread thread;

    private SnowboyDetect detector;
    private final MediaPlayer player = new MediaPlayer();
    private final Context ctx;
    private final String localFileDir;

    public RecordingThread(Context ctx, String kwdRes, String model, Handler handler, AudioDataReceivedListener listener) {
        this.ctx = ctx;
        this.handler = handler;
        this.listener = listener;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Path path = Paths.get(ctx.getFilesDir().getAbsolutePath(), Constants.ASSETS_RES_DIR);
            localFileDir = path.toAbsolutePath().toString();
        } else {
            localFileDir = ctx.getFilesDir().getAbsolutePath() + "/" + Constants.ASSETS_RES_DIR;
        }
        this.updateModel(kwdRes, model, "0.8,0.8", 1, true);
        try {
            player.setDataSource(localFileDir + "/" + "ding.wav");
            player.prepare();
        } catch (IOException e) {
            Log.e(TAG, "Playing ding sound error", e);
        }
    }

    public void updateModel(
            String activeRes,
            String activeModel,
            String sensitive,
            float audioGain,
            boolean applyFrontend
    ) {
        if (this.detector != null) {
            this.detector.delete();
            this.detector = null;
        }
        this.detector = new SnowboyDetect(
                localFileDir + "/" + activeRes,
                localFileDir + "/" + activeModel
        );
        this.detector.SetSensitivity(sensitive);
        this.detector.SetAudioGain(audioGain);
        this.detector.ApplyFrontend(applyFrontend);
    }

    private void sendMessage(MsgEnum what, Object obj) {
        if (null != handler) {
            Message msg = handler.obtainMessage(what.ordinal(), obj);
            handler.sendMessage(msg);
        }
    }

    public void startRecording() {
        if (thread != null)
            return;

        shouldContinue = true;
        thread = new Thread(() -> XXPermissions.with(RecordingThread.this.ctx)
                // 申请单个权限
                .permission(Permission.RECORD_AUDIO)
                .request(new OnPermissionCallback() {
                    @Override
                    public void onGranted(@NonNull List<String> list, boolean b) {
                        record();
                    }

                    @Override
                    public void onDenied(@NonNull List<String> permissions, boolean doNotAskAgain) {
                        OnPermissionCallback.super.onDenied(permissions, doNotAskAgain);
                    }
                }));
        thread.start();
    }

    public void stopRecording() {
        if (thread == null)
            return;

        shouldContinue = false;
        thread = null;
    }

    private void record() {
        Log.v(TAG, "Start");
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        // Buffer size in bytes: for 0.1 second of audio
        int bufferSize = (int) (Constants.SAMPLE_RATE * 0.1 * 2);
        byte[] audioBuffer = new byte[bufferSize];
        if (ActivityCompat.checkSelfPermission(this.ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "record: record permission failed");
            return;
        }
        AudioRecord record = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                Constants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Audio Record can't initialize!");
            return;
        }
        record.startRecording();
        if (null != listener) {
            listener.start();
        }
        Log.v(TAG, "Start recording");

        long shortsRead = 0;
        detector.Reset();
        while (shouldContinue) {
            record.read(audioBuffer, 0, audioBuffer.length);

            if (null != listener) {
                listener.onAudioDataReceived(audioBuffer, audioBuffer.length);
            }

            // Converts to short array.
            short[] audioData = new short[audioBuffer.length / 2];
            ByteBuffer.wrap(audioBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(audioData);

            shortsRead += audioData.length;

            // Snowboy hotword detection.
            int result = detector.RunDetection(audioData, audioData.length);

            if (result == -1) {
                sendMessage(MsgEnum.MSG_ERROR, "Unknown Detection Error");
            } else if (result > 0) {
                sendMessage(MsgEnum.MSG_ACTIVE, null);
                Log.i("Snowboy: ", "Hotword " + result + " detected!");
                player.start();
            }
            // VAD speech found or not, post a higher CPU usage:
            // if (result == -2) {
            //     sendMessage(MsgEnum.MSG_VAD_NOSPEECH, null);
            // } else if (result == 0) {
            //     sendMessage(MsgEnum.MSG_VAD_SPEECH, null);
            // }
        }

        record.stop();
        record.release();

        if (null != listener) {
            listener.stop();
        }
        Log.v(TAG, String.format("Recording stopped. Samples read: %d", shortsRead));
    }
}
