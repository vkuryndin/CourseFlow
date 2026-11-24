package org.courseflow.common.exception;

public class InvalidSessionException extends RuntimeException {
    public InvalidSessionException() { super("Authentication session is invalid or expired"); }
}
