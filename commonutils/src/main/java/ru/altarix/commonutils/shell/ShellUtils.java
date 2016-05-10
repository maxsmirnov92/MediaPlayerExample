package ru.altarix.commonutils.shell;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ru.altarix.commonutils.data.FileHelper;

public class ShellUtils {

    private final static Logger logger = LoggerFactory.getLogger(ShellUtils.class);

    public static final int PROCESS_EXIT_CODE_SUCCESS = 0;

    public static final String SU_PROCESS_NAME = "su";

    public interface ShellCallback {

        enum StreamType {
            CMD("cmd"), OUT("out"), ERR("err");

            public final String name;

            StreamType(String name) {
                this.name = name;
            }
        }

        boolean needToLogCommands();

        void shellOut(@NonNull StreamType from, String shellLine);

        void processComplete(int exitValue);
    }

    public interface ThreadsCallback {
        void onThreadsStarted(List<Thread> threads);
    }

    @Nullable
    private static Process createAndStartProcess(@NonNull List<String> cmds, @Nullable String workingDir, @Nullable ShellCallback sc, @Nullable ThreadsCallback tc) {

        for (int i = 0; i < cmds.size(); i++) {
            cmds.set(i, String.format(Locale.US, "%s", cmds.get(i)));
        }

        ProcessBuilder pb = new ProcessBuilder(cmds);

        if (FileHelper.isDirExists(workingDir)) {
            pb.directory(new File(workingDir));
        } else {
            logger.warn("working directory " + workingDir + " not exists");
        }

        if (sc != null && sc.needToLogCommands()) {
            StringBuilder cmdlog = new StringBuilder();
            for (String cmd : cmds) {
                cmdlog.append(cmd);
                cmdlog.append(' ');
            }
            sc.shellOut(ShellCallback.StreamType.CMD, cmdlog.toString());
        }

        Process process;

        try {
            process = pb.start();
        } catch (IOException e) {
            logger.error("an IOException occurred during start()", e);
            e.printStackTrace();
            return null;
        }

        Thread outThread;
        (outThread = new StreamConsumeThread(process.getInputStream(), ShellCallback.StreamType.OUT, sc)).start();
        Thread errThread;
        (errThread = new StreamConsumeThread(process.getErrorStream(), ShellCallback.StreamType.ERR, sc)).start();

        if (tc != null) {
            tc.onThreadsStarted(new ArrayList<>(Arrays.asList(new Thread[]{outThread, errThread})));
        }

        return process;
    }

    /**
     * @return true if started successfully, false - otherwise
     */
    public static boolean execProcessAsync(@NonNull List<String> cmds, @Nullable String workingDir, @Nullable ShellCallback sc, @Nullable ThreadsCallback tc) {
        logger.debug("execProcessAsync(), cmds=" + cmds + ", workingDir=" + workingDir + ", sc=" + sc + ", tc=" + tc);
        Process process = createAndStartProcess(cmds, workingDir, sc, tc);
        if (process != null) {
            Thread waitThread;
            (waitThread = new ProcessWaitThread(process, sc)).start();
            if (tc != null) {
                tc.onThreadsStarted(Collections.singletonList(waitThread));
            }
        }
        return process != null;
    }

    /**
     * @return result code; -1 if start failed or interrupted
     */
    public static int execProcess(@NonNull List<String> cmds, @Nullable String workingDir, @Nullable ShellCallback sc, @Nullable final ThreadsCallback tc, final boolean waitForEndOutput) {
        logger.debug("execProcess(), cmds=" + cmds + ", workingDir=" + workingDir + ", sc=" + sc + ", tc=" + tc);
        Process process = createAndStartProcess(cmds, workingDir, sc, new ThreadsCallback() {
            @Override
            public void onThreadsStarted(List<Thread> threads) {
                if (waitForEndOutput) {
                    for (Thread t : threads) {
                        if (t != null && t.isAlive() && !t.isInterrupted()) {
                            try {
                                t.join();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                    if (tc != null) {
                        tc.onThreadsStarted(threads);
                    }
                }
            }
        });
        int exitCode = -1;
        try {
            return exitCode = process != null ?  process.waitFor() : -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
            return exitCode;
        } finally {
            if (sc != null) {
                sc.processComplete(exitCode);
            }
        }
    }

    private static class StreamConsumeThread extends Thread {

        @NonNull
        final InputStream is;

        @NonNull
        final ShellCallback.StreamType type;

        @Nullable
        final ShellCallback sc;

        StreamConsumeThread(@NonNull InputStream is, @NonNull ShellCallback.StreamType type, @Nullable ShellCallback sc) {
            this.is = is;
            this.type = type;
            this.sc = sc;
            this.setName(type.name);
        }

        public void run() {
            try {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                while (!isInterrupted() && (line = br.readLine()) != null)
                    if (sc != null) {
                        sc.shellOut(type, line);
                    }
            } catch (IOException e) {
                logger.error("an IOException occurred", e);
                e.printStackTrace();
            }
        }
    }

    private static class ProcessWaitThread extends Thread {

        @NonNull
        final Process process;

        @Nullable
        final ShellCallback sc;

        public ProcessWaitThread(@NonNull Process process, @Nullable ShellCallback sc) {
            super(ProcessWaitThread.class.getName());
            this.process = process;
            this.sc = sc;
        }

        @Override
        public void run() {

            int exitVal = -1;

            try {
                exitVal = process.waitFor();
            } catch (InterruptedException e) {
                logger.error("an InterruptedException occurred", e);
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }

            if (sc != null) {
                sc.processComplete(exitVal);
            }

        }
    }
}
