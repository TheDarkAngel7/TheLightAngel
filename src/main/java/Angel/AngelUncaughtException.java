package Angel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AngelUncaughtException implements Thread.UncaughtExceptionHandler {

    private final Logger log = LogManager.getLogger(AngelUncaughtException.class);

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        log.error("Uncaught Exception in " + t.getName() + ": " + e.getMessage());

        String trace = "Stack Trace:\n";
        int index = 0;
        StackTraceElement[] elements = e.getStackTrace();

        do {
            trace = trace.concat(elements[index++].toString() + "\n");
        } while (index < elements.length);

        log.error(trace);
    }
}
