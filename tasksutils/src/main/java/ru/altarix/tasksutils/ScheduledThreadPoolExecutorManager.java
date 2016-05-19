package ru.altarix.tasksutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ScheduledThreadPoolExecutorManager {

    private final static Logger logger = LoggerFactory.getLogger(ScheduledThreadPoolExecutorManager.class);

    private final List<Runnable> runnableList = new ArrayList<Runnable>();

    public void addRunnableTask(Runnable runnable) throws NullPointerException {
        logger.debug("addRunnableTask(), runnable=" + runnable);

        if (runnable == null) {
            throw new NullPointerException();
        }

        synchronized (runnableList) {
            if (runnableList.contains(runnable)) {
                return;
            }
            runnableList.add(runnable);
        }

        if (isRunning()) {
            start(intervalMs);
        }
    }

    public void removeRunnableTask(Runnable runnable) {
        logger.debug("removeRunnableTask(), runnable=" + runnable);

        synchronized (runnableList) {
            if (runnableList.contains(runnable)) {
                runnableList.remove(runnable);
            }
        }

        if (isRunning()) {
            start(intervalMs);
        }
    }

    public void removeAllRunnableTasks() {

        if (isRunning()) {
            stop(false, 0);
        }

        synchronized (runnableList) {
            runnableList.clear();
        }
    }

    private final String poolName;

    public ScheduledThreadPoolExecutorManager(String poolName) {
        logger.debug("ScheduledThreadPoolExecutorManager(), poolName=" + poolName);
        this.poolName = poolName;
    }

    private ScheduledThreadPoolExecutor executor;

    public boolean isRunning() {

        if (executor == null) {
            return false;
        }

        return (!executor.isShutdown() || !executor.isTerminated());
    }

    private long intervalMs = 0;

    public long getIntervalMs() {
        return intervalMs;
    }

    public synchronized void start(long intervalMs) {
        logger.debug("start(), intervalMs=" + intervalMs);

        if (intervalMs <= 0)
            throw new IllegalArgumentException("can't start executor: incorrect intervalMs: " + intervalMs);

        if (runnableList.isEmpty())
            throw new RuntimeException("no runnables to schedule");

        stop(false, 0);

        executor = new ScheduledThreadPoolExecutor(runnableList.size(), new NamedThreadFactory(poolName));

        for (Runnable runnable : runnableList) {
            logger.debug("scheduling runnable " + runnable + " with interval " + intervalMs + " ms...");
            executor.scheduleAtFixedRate(runnable, 0, this.intervalMs = intervalMs, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stop(boolean await, long timeoutMs) {
        logger.debug("stop(), await=" + await + ", timeoutMs=" + timeoutMs);

        if (!isRunning()) {
            logger.debug("executor already not running");
            return;
        }

        // executor.remove(runnable);
        // executor.purge();

        executor.shutdown();

        if (await) {
            try {
                executor.awaitTermination(timeoutMs >= 0 ? timeoutMs : 0, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                logger.error("an InterruptedException occurred during awaitTermination(): " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        executor = null;
        intervalMs = 0;
    }

}
