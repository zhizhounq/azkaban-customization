package azkaban.jobtype;

import java.io.File;
import java.io.IOException;
import org.apache.log4j.Logger;
import azkaban.jobExecutor.AbstractProcessJob;
import azkaban.utils.Props;

public class LockedJob extends AbstractProcessJob {

    public static final String COMMAND = "command";

    private static final String DEFAULT_LOCK_PATH = "/tmp/lock";
    private static final String DEFAULT_LOCK_NAME = "lockname";
    private static final int DEFAULT_LOCK_CHECK_MS = 30 * 1000;

    private String lockName;
    private String lockDir;
    private int lockWaitTime;
    private boolean lockFlag = false;

    public LockedJob(final String jobId, final Props sysProps, final Props jobProps,
            final Logger log) {
        super(jobId, sysProps, jobProps, log);
        if (jobProps.containsKey("lock")) {
            lockName = jobProps.getString("lock", DEFAULT_LOCK_NAME);
            lockFlag = true;
        } else {
            lockName = jobProps.getString("unlock", DEFAULT_LOCK_NAME);
            lockFlag = false;
        }
        lockDir = sysProps.getString("azkaban.lock.path", DEFAULT_LOCK_PATH);
        lockWaitTime = jobProps.getInt("lock.check.time", DEFAULT_LOCK_CHECK_MS);
    }

    @Override
    public void run() throws Exception {
        if (lockFlag) {
            while (true) {
                if (!fileExists())
                    break;
                info("blocking and waiting");
                Thread.sleep(lockWaitTime);
            }
            info("getting lock " + lockName);
            synchronized (this) {
                createFile();
            }
        } else {
            synchronized (this) {
                deleteFile();
            }
        }
    }

    private boolean fileExists() {
        File dir = new File(lockDir);
        if (!dir.exists()) {
            info("The lock path" + lockDir + " doesn't exist");
            dir.mkdirs();
        }
        if (dir.isDirectory()) {
            String[] fileList = dir.list();
            for (int i = 0; i < fileList.length; ++i)
                if (fileList[i].startsWith(lockName))
                    return true;
        }
        return false;
    }

    private void createFile() {
        String fullFileName = getLockFullName();
        File lockFile = new File(lockDir, fullFileName);
        try {
            lockFile.createNewFile();
        } catch (IOException e) {
            error("failed to get lock " + lockName);
            throw new RuntimeException(e);
        }
    }

    private boolean deleteFile() {
        String fullFileName = getLockFullName();
        File lockFile = new File(lockDir, fullFileName);

        return lockFile.delete();
    }

    private String getLockFullName() {
        return lockName + "_" + getExecutionId();
    }
}
