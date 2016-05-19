package ru.altarix.tasksutils;

import java.util.concurrent.ThreadFactory;

public class NamedThreadFactory implements ThreadFactory {

    private String threadsName = "";

    public NamedThreadFactory(String threadsName) {
        super();
        this.threadsName = threadsName;
    }

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, threadsName);
    }

}