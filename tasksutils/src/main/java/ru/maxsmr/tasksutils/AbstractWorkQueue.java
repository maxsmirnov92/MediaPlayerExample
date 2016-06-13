package ru.maxsmr.tasksutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

import ru.maxsmr.commonutils.data.FileHelper;
import ru.maxsmr.tasksutils.taskrunnable.RunnableInfo;
import ru.maxsmr.tasksutils.taskrunnable.TaskRunnable;

public abstract class AbstractWorkQueue {

    private static final Logger logger = LoggerFactory.getLogger(AbstractWorkQueue.class);

    private final PoolWorker[] poolWorkers;

    private final int maxRunnableQueueSize;
    private final LinkedList<TaskRunnable> runnableQueue;

    protected final boolean syncQueue;
    protected final String queueDirPath;

    protected final static String FILE_EXT_DAT = "dat";

    protected abstract boolean restoreTaskRunnablesFromFiles();

    public AbstractWorkQueue(int nPoolWorkers, int maxRunnableQueueSize, String workQueueName, boolean syncQueue, String queueDirPath) {
        logger.debug("WorkQueue(), nPoolWorkers=" + nPoolWorkers + ", maxRunnableQueueSize=" + maxRunnableQueueSize + ", workQueueName="
                + workQueueName + ", syncQueue=" + syncQueue + ", queueDirPath=" + queueDirPath);

        if (nPoolWorkers <= 0)
            nPoolWorkers = 1;

        poolWorkers = new PoolWorker[nPoolWorkers];

        for (int i = 0; i < nPoolWorkers; i++) {
            poolWorkers[i] = new PoolWorker();
            if (workQueueName != null && !workQueueName.isEmpty()) {
                poolWorkers[i].setName(workQueueName + "_" + i);
            }
            poolWorkers[i].start();
        }

        this.maxRunnableQueueSize = maxRunnableQueueSize >= 0 ? maxRunnableQueueSize : 0;
        this.runnableQueue = new LinkedList<TaskRunnable>();

        this.syncQueue = syncQueue;
        this.queueDirPath = queueDirPath;

        if (syncQueue) {
            final Thread restoreThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    restoreTaskRunnablesFromFiles();
                }
            });

            restoreThread.setName(AbstractWorkQueue.class.getSimpleName() + ":RestoreThread");
            restoreThread.start();
        }
    }

    public void release() {
        logger.debug("release");

        if (poolWorkers != null && poolWorkers.length > 0) {
            for (int i = 0; i < poolWorkers.length; i++) {
                poolWorkers[i].interrupt();
            }
        }

        if (runnableQueue != null) {
            runnableQueue.clear();
        }
    }

    public int getRunnableQueueSize() {
        return runnableQueue.size();
    }

    public boolean execute(TaskRunnable r) {
        logger.debug("execute(), r=" + r);

        if (r == null) {
            logger.error("runnable is null");
            return false;
        }

        if (runnableQueue == null) {
            return false;
        }

        synchronized (runnableQueue) {
            if (runnableQueue.size() < maxRunnableQueueSize || maxRunnableQueueSize == 0) {

                if (runnableQueue.contains(r)) {
                    logger.warn("runnableQueue already contains this runnable");
                    return true;
                }

                runnableQueue.addLast(r);
                runnableQueue.notify();

                if (syncQueue) {
                    if (!writeRunnableInfoToFile(r.rInfo, queueDirPath)) {
                        logger.error("can't write task runnable with info " + r.rInfo + " to file");
                    }
                }

                return true;

            } else {
                logger.error("no capacity remains in runnable queue (" + runnableQueue.size() + "/" + maxRunnableQueueSize + ")");
                return false;
            }
        }
    }

    private class PoolWorker extends Thread {

        @Override
        public void run() {
            logger.debug("PoolWorker :: run()");

            TaskRunnable r;

            while (!isInterrupted()) {

                if (runnableQueue == null) {
                    // logger.error("runnableQueue is null");
                    continue;
                }

                synchronized (runnableQueue) {
                    while (runnableQueue.isEmpty()) {
                        try {
                            // logger.debug("waiting queue...");
                            runnableQueue.wait();
                        } catch (InterruptedException e) {
                            logger.error("an InterruptedException occurred during wait(): " + e.getMessage());
                            Thread.currentThread().interrupt();
                        }
                    }

                    // logger.debug("getting runnable...");
                    r = runnableQueue.removeFirst();
                }

                try {
                    // logger.debug("running runnable: " + r + "...");
                    r.run();
                } catch (RuntimeException e) {
                    logger.error("a RuntimeException occurred during run()", e);
                }

                if (syncQueue) {
                    if (deleteFileByRunnableInfo(r.rInfo, queueDirPath)) {
                        logger.error("can't delete file by task runnable with info " + r.rInfo);
                    }
                }
            }

        }
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
