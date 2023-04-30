package Angel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AngelExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger log = LogManager.getLogger(AngelExceptionHandler.class);

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        log.error("Uncaught Exception in " + t.getName() + ": " + e.getMessage());

        logException(e);
    }

    public void logCaughtException(Thread t, Throwable e) {
        log.warn("Caught Exception in " + t.getName() + ": " + e.getMessage());

        logException(e);
    }

    private void logException(Throwable e) {
        String trace = "Stack Trace:\n";
        int index = 0;
        StackTraceElement[] elements = e.getStackTrace();

        do {
            trace = trace.concat(elements[index++].toString() + "\n");
        } while (index < elements.length);

        log.error(trace);
    }
}
