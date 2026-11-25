package com.planshift.experiment;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Who this running instance is, and whether it has experiments waiting or running. */
@Component
public class ExperimentOwner {

    private final String id = UUID.randomUUID().toString();
    private final AtomicInteger pending = new AtomicInteger();

    public String id() {
        return id;
    }

    void jobAccepted() {
        pending.incrementAndGet();
    }

    void jobFinished() {
        pending.decrementAndGet();
    }

    boolean hasWork() {
        return pending.get() > 0;
    }
}
