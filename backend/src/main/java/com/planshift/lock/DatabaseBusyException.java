package com.planshift.lock;

public class DatabaseBusyException extends RuntimeException {

    public DatabaseBusyException() {
        super("An experiment or another database change is in progress. Try again when it has finished.");
    }
}
