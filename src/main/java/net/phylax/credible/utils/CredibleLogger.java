package net.phylax.credible.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredibleLogger {
    public static final String LOGGER_PREFIX = "CredibleLayer";
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(LOGGER_PREFIX.concat(clazz.getSimpleName()));
    }
}
