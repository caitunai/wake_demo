package ai.kitt.snowboy;

import static ai.kitt.snowboy.Constants.ASSETS_RES_DIR;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppResCopy {
    private final static String TAG = AppResCopy.class.getSimpleName();

    private static void copyFilesFromAssets(Context context, String assetsSrcDir, String destFile, boolean override) {
        String localFileDir;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Path path = Paths.get(context.getFilesDir().getAbsolutePath(), ASSETS_RES_DIR);
            localFileDir = path.toAbsolutePath().toString();
        } else {
            localFileDir = context.getFilesDir().getAbsolutePath() + "/" + ASSETS_RES_DIR;
        }
        try {
            String[] fileNames = context.getAssets().list(assetsSrcDir);
            if (fileNames != null && fileNames.length > 0) {
                Log.i(TAG, assetsSrcDir +" directory has "+fileNames.length+" files.\n");
                File dir = new File(localFileDir);
                if (!dir.exists()) {
                    if (!dir.mkdirs()) {
                        Log.e(TAG, "mkdir failed: "+localFileDir);
                        return;
                    } else {
                        Log.d(TAG, "mkdir ok: "+localFileDir);
                    }
                } else {
                     Log.w(TAG, localFileDir+" already exists! ");
                }
                for (String fileName : fileNames) {
                    copyFilesFromAssets(context,assetsSrcDir + "/" + fileName, fileName, override);
                }
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    Path path = Paths.get(localFileDir, destFile);
                    destFile = path.toAbsolutePath().toString();
                } else {
                    destFile = localFileDir + "/" + destFile;
                }
                Log.d(TAG, assetsSrcDir +" is file, copy to " + destFile);
                File outFile = new File(destFile);
                if (outFile.exists()) {
                    if (override) {
                        boolean deleted = outFile.delete();
                        if (deleted) {
                            Log.d(TAG, "deleted file " + destFile + "\n");
                        } else {
                            Log.e(TAG, "delete file " + destFile + " failed\n");
                        }
                    } else {
                        Log.d(TAG, "file "+ destFile +" already exists. No override.\n");
                        return;
                    }
                }
                InputStream is = context.getAssets().open(assetsSrcDir);
                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buffer = new byte[1024];
                int byteCount;
                while ((byteCount=is.read(buffer)) != -1) {
                    fos.write(buffer, 0, byteCount);
                }
                fos.flush();
                is.close();
                fos.close();
                Log.d(TAG, "copy to "+destFile+" ok!");
            }
        } catch (Exception e) {
            Log.e(TAG, "copyFilesFromAssets: ", e);
        }
    }

    public static void copyResFromAssetsToSD(Context context) {
        copyFilesFromAssets(context, ASSETS_RES_DIR, "", true);
    }
}
