package ai.kitt.snowboy.audio;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class AudioDataSaver implements AudioDataReceivedListener {

    private static final String TAG = AudioDataSaver.class.getSimpleName();

    // keeps track of recording file size
    private int recordingFileSizeCounterInBytes = 0;

    private final File saveFile;
    private DataOutputStream dataOutputStreamInstance = null;

    public AudioDataSaver(String path) {
        saveFile = new File(path);
        Log.i(TAG, "save audio to path :" + path);
    }

    @Override
    public void start() {
        if (null != saveFile) {
            if (saveFile.exists()) {
                boolean deleted = saveFile.delete();
                if (!deleted) {
                    Log.e(TAG, "start: delete save file failed, path: " + saveFile.getAbsoluteFile());
                }
            }
            try {
                boolean created = saveFile.createNewFile();
                if (!created) {
                    Log.e(TAG, "start: file is exists: " + saveFile.getAbsoluteFile());
                }
            } catch (IOException e) {
                Log.e(TAG, "IO Exception on creating audio file " + saveFile, e);
            }

            try {
                BufferedOutputStream bufferedStreamInstance = new BufferedOutputStream(
                        new FileOutputStream(this.saveFile));
                dataOutputStreamInstance = new DataOutputStream(bufferedStreamInstance);
            } catch (FileNotFoundException e) {
                throw new IllegalStateException("Cannot Open File", e);
            }
        }
    }

    @Override
    public void onAudioDataReceived(byte[] data, int length) {
        try {
            if (null != dataOutputStreamInstance) {
                // converted max file size
                // file size of when to delete and create a new recording file
                float MAX_RECORDING_FILE_SIZE_IN_MB = 50f;
                // initial file size of recording file
                float INITIAL_FILE_SIZE_IN_MB = 1.3f;
                float MAX_RECORDING_FILE_SIZE_IN_BYTES = (MAX_RECORDING_FILE_SIZE_IN_MB - INITIAL_FILE_SIZE_IN_MB) * 1024 * 1024;
                if (recordingFileSizeCounterInBytes >= MAX_RECORDING_FILE_SIZE_IN_BYTES) {
                    stop();
                    start();
                    recordingFileSizeCounterInBytes = 0;
                }
                dataOutputStreamInstance.write(data, 0, length);
                recordingFileSizeCounterInBytes += length;
            }
        } catch (IOException e) {
            Log.e(TAG, "IO Exception on saving audio file " + saveFile.toString(), e);
        }
    }

    @Override
    public void stop() {
        if (null != dataOutputStreamInstance) {
            try {
                dataOutputStreamInstance.close();
            } catch (IOException e) {
                Log.e(TAG, "IO Exception on finishing saving audio file " + saveFile.toString(), e);
            }
            Log.e(TAG, "Recording saved to " + saveFile.toString());
        }
    }
}
