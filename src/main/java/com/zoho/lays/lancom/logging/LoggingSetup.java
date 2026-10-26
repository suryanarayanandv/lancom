package com.zoho.lays.lancom.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Configures java.util.logging with a compact single-line console format on stderr. */
public final class LoggingSetup {

    private LoggingSetup() {
    }

    public static void configure(Level level) {
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new ConsoleFormatter());
        handler.setLevel(level);
        root.addHandler(handler);
        root.setLevel(level);
    }

    static final class ConsoleFormatter extends Formatter {
        private static final DateTimeFormatter TIME =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

        @Override
        public String format(LogRecord record) {
            StringBuilder line = new StringBuilder(160)
                    .append(TIME.format(record.getInstant()))
                    .append(' ')
                    .append(String.format("%-7s", record.getLevel().getName()))
                    .append(" [")
                    .append(simpleName(record.getLoggerName()))
                    .append("] ")
                    .append(formatMessage(record))
                    .append(System.lineSeparator());
            if (record.getThrown() != null) {
                StringWriter trace = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(trace));
                line.append(trace);
            }
            return line.toString();
        }

        private static String simpleName(String loggerName) {
            if (loggerName == null) {
                return "root";
            }
            int dot = loggerName.lastIndexOf('.');
            return dot >= 0 ? loggerName.substring(dot + 1) : loggerName;
        }
    }
}
