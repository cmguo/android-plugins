package dx.android.util.system;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.util.Log;

public class FileLock {

    private static final String TAG = "FileLock";

    public static FileLock lock(File lockFile) {
        FileLock fileLock = new FileLock(lockFile, false);
        return fileLock.hasLock() ? fileLock : null;
    }
    
    public static FileLock tryLock(File lockFile) {
        FileLock fileLock = new FileLock(lockFile, true);
        return fileLock.hasLock() ? fileLock : null;
    }

    private FileOutputStream mOut;

    private FileLock(File file, boolean tryLock) {
        java.nio.channels.FileLock lock = null;
        try {
            mOut = new FileOutputStream(file, true);
            if (tryLock)
                lock = mOut.getChannel().tryLock();
            else
                lock = mOut.getChannel().lock();
        } catch (IOException e) {
            Log.w(TAG, "<init>", e);
        }
        if (lock == null)
            release();
    }
    
    private boolean hasLock() {
        return mOut != null;
    }
    
    public void release() {
        try {
            if (mOut != null)
                mOut.close();
        } catch (IOException e) {
            Log.w(TAG, "release", e);
        }
        mOut = null;
    }

}
