package com.transactionledger.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppLogger {

    private AppLogger() {
    }

    public static Logger get(Class<?> type) {
        return LoggerFactory.getLogger(type);
    }
}
