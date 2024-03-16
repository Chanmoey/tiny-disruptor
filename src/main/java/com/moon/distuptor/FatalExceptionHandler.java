package com.moon.distuptor;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public class FatalExceptionHandler implements ExceptionHandler<Object> {

    private static final Logger LOGGER = Logger.getLogger(FatalExceptionHandler.class.getName());
    private final Logger logger;

    public FatalExceptionHandler() {
        this.logger = LOGGER;
    }

    public FatalExceptionHandler(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public void handleEventException(Throwable ex, long sequence, Object event) {
        logger.log(Level.SEVERE, "Exception processing: " + sequence + " " + event, ex);

        throw new RuntimeException(ex);
    }

    @Override
    public void handleOnStartException(Throwable ex) {
        logger.log(Level.SEVERE, "Exception during onStart()", ex);
    }

    @Override
    public void handleOnShutdownException(Throwable ex) {
        logger.log(Level.SEVERE, "Exception during onShutdown()", ex);
    }
}
