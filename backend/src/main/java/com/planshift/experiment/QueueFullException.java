package com.planshift.experiment;

public class QueueFullException extends RuntimeException {

    public QueueFullException() {
        super("Too many experiments are already waiting. Try again when one has finished.");
    }
}
