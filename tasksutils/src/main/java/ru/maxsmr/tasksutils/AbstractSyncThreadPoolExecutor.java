package ru.maxsmr.tasksutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import ru.maxsmr.tasksutils.taskrunnable.RunnableInfo;
import ru.maxsmr.tasksutils.taskrunnable.TaskRunnable;
import ru.maxsmr.commonutils.data.FileHelper;


public abstract class AbstractSyncThreadPoolExecutor extends ThreadPoolExecutor {

    private final static Logger logger = LoggerFactory.getLogger(AbstractSyncThreadPoolExecutor.class);

    protected final boolean syncQueue;
    protected final String queueDirPath;

    protected final static String FILE_EXT_DAT = "dat";

    private Thread restoreThread;

    protected final boolean isRestoreThreadRunning() {
        return (restoreThread != null && restoreThread.isAlive());
    }

    protected final void startRestoreThread() {
        logger.debug("startRestoreThread()");

        if (!syncQueue) {
            logger.warn("sync queue is not enabled");
            return;
        }

        if (isRestoreThreadRunning()) {
            logger.debug("restoreThread is already running");
            return;
        }

        restoreThread = new Thread(new Runnable() {

            @Override
            public void run() {
                restoreTaskRunnablesFromFiles();
            }
        }, AbstractSyncThreadPoolExecutor.class.getSimpleName() + ":RestoreThread");

        restoreThread.start();
    }

    protected final void stopRestoreThread() {
        logger.debug("stopRestoreThread()");

        if (!isRestoreThreadRunning()) {
            logger.debug("restoreThread is not running");
            return;
        }

        restoreThread.interrupt();
        restoreThread = null;
    }

    protected abstract boolean restoreTaskRunnablesFromFiles();

    public final static int DEFAULT_KEEP_ALIVE_TIME = 60;

    public AbstractSyncThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, String poolName,
                                          boolean syncQueue, String queueDirPath) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory(poolName));
        logger.debug("AbstractSyncThreadPoolExecutor(), corePoolSize=" + corePoolSize + ", maximumPoolSize=" + maximumPoolSize
                + ", keepAliveTime=" + keepAliveTime + ", unit=" + unit + ", poolName=" + poolName + ", syncQueue=" + syncQueue
                + ", queueDirPath=" + queueDirPath);

        this.syncQueue = syncQueue;
        this.queueDirPath = queueDirPath;

        startRestoreThread();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        stopRestoreThread();
    }

    public boolean isRunning() {
        return (!isShutdown() || !isTerminated()) /* && getTaskCount() > 0 */;
    }

    @Override
    public void execute(Runnable command) {

        if (!isRunning()) {
            throw new IllegalStateException("can't run " + command + ": not running (isShutdown=" + isShutdown() + ", isTerminated=" + isTerminated() + ", taskCount=" + getTaskCount());
        }

        if (command == null) {
            throw new NullPointerException("command is null");
        }

        if (!(command instanceof TaskRunnable)) {
            throw new IllegalArgumentException("command " + command.getClass().getName() + " is not instance of " + TaskRunnable.class.getName());
        }

        if (syncQueue) {
            if (!writeRunnableInfoToFile(((TaskRunnable) command).rInfo, queueDirPath)) {
                logger.error("can't write task runnable with info " + ((TaskRunnable) command).rInfo + " to file");
            }
        }

        super.execute(command);
    }

    public boolean execute(TaskRunnable command) {
        execute((Runnable) command);
        return true;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        TaskRunnable tRunnable = null;

        try {
            tRunnable = (TaskRunnable) r;
        } catch (ClassCastException e) {
            logger.error("a ClassCastException occurred", e);
            return;
        }

        if (syncQueue) {
            if (!deleteFileByRunnableInfo(tRunnable.rInfo, queueDirPath)) {
                logger.error("can't delete file by task runnable with info " + tRunnable.rInfo);
            }
        }

        super.afterExecute(r, t);
    }

    private static boolean writeRunnableInfoToFile(RunnableInfo rInfo, String parentPath) {
        logger.debug("writeRunnableInfoToFile(), parentPath=" + parentPath); // rInfo=" + rInfo + "

        if (rInfo == null) {
            logger.error("runnable info is null");
            return false;
        }

        if (rInfo.name == null || rInfo.name.length() == 0) {
            logger.error("runnable info name is null or empty");
            return false;
        }

        if (parentPath == null || parentPath.length() == 0) {
            logger.error("parentPath is null or empty");
            return false;
        }

        final String infoFileName = rInfo.name + "." + FILE_EXT_DAT;
        return (FileHelper.writeBytesToFile(RunnableInfo.toByteArray(rInfo), infoFileName, parentPath, false) != null);
    }

    private static boolean deleteFileByRunnableInfo(RunnableInfo rInfo, String parentPath) {
        logger.debug("deleteFileByRunnableInfo(), parentPath=" + parentPath); // rInfo=" + rInfo + "

        if (rInfo == null) {
            logger.error("runnable info is null");
            return false;
        }

        if (rInfo.name == null || rInfo.name.length() == 0) {
            logger.error("runnable info name is null or empty");
            return false;
        }

        if (parentPath == null || parentPath.length() == 0) {
            logger.error("parentPath is null or empty");
            return false;
        }

        final String infoFileName = rInfo.name + "." + FILE_EXT_DAT;
        return FileHelper.deleteFile(infoFileName, parentPath);
    }

}
