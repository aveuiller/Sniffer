package fr.inria.sniffer.tracker.analysis.persistence;

import java.sql.Connection;

public class PostgresqlPersistence extends JDBCPersistence {
    public static final String SCHEMA_RESOURCE_PATH = "/schema/tracker-postgresql.sql";

    public PostgresqlPersistence(String path, String username, String password) {
        super("postgresql", path, SCHEMA_RESOURCE_PATH, username, password);
    }

    public PostgresqlPersistence(Connection connection) {
        super(connection, SCHEMA_RESOURCE_PATH);
    }
}
