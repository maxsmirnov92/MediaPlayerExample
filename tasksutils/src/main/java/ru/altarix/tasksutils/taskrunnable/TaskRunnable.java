package ru.altarix.tasksutils.taskrunnable;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

public abstract class TaskRunnable<I extends RunnableInfo> implements Runnable {

    @NonNull
    public final I rInfo;

    public TaskRunnable(@NonNull I rInfo) {
        this.rInfo = rInfo;
    }

    protected abstract boolean checkArgs();

    @Override
    @CallSuper
    public void run() {
        checkArgs();
    }

    @Override
    public String toString() {
        return "TaskRunnable [rInfo=" + rInfo + "]";
    }

}
