package com.planshift.experiment;

public class KeepIndexDisabledException extends RuntimeException {

    public KeepIndexDisabledException() {
        super("Keeping indexes is turned off on this server.");
    }
}
