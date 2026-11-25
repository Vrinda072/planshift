package com.planshift.lock;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * One lock, held in Postgres, for everything that changes the database an experiment measures: experiments
 * themselves, CSV imports, data generation and kept indexes. It works across application instances.
 *
 * It is a transaction-level advisory lock on a connection of its own, which stays correct behind a pooler in
 * transaction mode (session-level advisory locks do not). That connection runs nothing else, so the work being
 * protected uses other connections. The lock is not re-entrant.
 */
@Component
public class DatabaseLock {

    private static final long KEY = 0x504C414E53484654L;

    private final DataSource dataSource;

    public DatabaseLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Waits for as long as it takes. */
    public Held acquire() {
        return lock(true).orElseThrow();
    }

    /** Empty if someone else holds the lock right now. */
    public Optional<Held> tryAcquire() {
        return lock(false);
    }

    private Optional<Held> lock(boolean wait) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            Held held = new Held(connection);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                // The transaction sits idle while the protected work runs; some hosts end idle transactions.
                statement.execute("SET LOCAL idle_in_transaction_session_timeout = 0");
                statement.execute("SET LOCAL lock_timeout = 0");
                statement.execute("SET LOCAL statement_timeout = 0");
                if (wait) {
                    statement.execute("SELECT pg_advisory_xact_lock(" + KEY + ")");
                } else {
                    try (ResultSet rs = statement.executeQuery("SELECT pg_try_advisory_xact_lock(" + KEY + ")")) {
                        rs.next();
                        if (!rs.getBoolean(1)) {
                            held.close();
                            return Optional.empty();
                        }
                    }
                }
            }
            return Optional.of(held);
        } catch (SQLException e) {
            if (connection != null) {
                new Held(connection).close();
            }
            throw new IllegalStateException("Could not take the database lock", e);
        }
    }

    /** Releases the lock when closed. */
    public static final class Held implements AutoCloseable {

        private final Connection connection;

        private Held(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void close() {
            try (connection) {
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                // A broken connection has already lost its transaction, and the lock with it.
                org.slf4j.LoggerFactory.getLogger(DatabaseLock.class).warn("Could not release the lock cleanly", e);
            }
        }
    }
}
