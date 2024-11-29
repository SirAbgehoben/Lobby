//MySQLManager.java
package org.abgehoben.lobby;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.function.Function;

public class MySQLManager {

    private final JavaPlugin plugin;
    private final String database;
    private final String host;
    private final int port;
    private final String user;
    private final String password;


    private Connection connection;

    public MySQLManager(JavaPlugin plugin, String database, String host, int port, String user, String password) {
        this.plugin = plugin;
        this.database = database;
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;

    }

    public void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return; // Connection is already open, nothing to do
        }

        synchronized (this) {  // Synchronization is important
            if (connection != null && !connection.isClosed()) {  // Double-checked locking
                return;
            }

            try {
                connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true&failOverReadOnly=false&maxReconnects=5", user, password);  // Add autoReconnect!
                plugin.getLogger().info("Successfully established MySQL connection."); // Log success
            } catch (SQLException e) {
                plugin.getLogger().severe("Error establishing a database connection: " + e.getMessage());
                throw e; // Re-throw to be handled by the calling method
            }
        }
    }


    public void disconnect() { // Important for proper shutdown
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("MySQL connection closed."); // Log closure
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing the database connection: " + e.getMessage());
        }
    }


    public <T> T query(String sql, Function<ResultSet, T> handler, Object... parameters) throws SQLException {
        try {
            connect(); // Ensure connection before every query!
            try (PreparedStatement statement = connection.prepareStatement(sql)) {  // Try-with-resources
                // Fix the parameter setting loop:
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]); // Start from 1
                }

                try (ResultSet resultSet = statement.executeQuery()) {
                    return handler.apply(resultSet);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL Query Error: " + e.getMessage() + " | SQL: " + sql);

            if (e.getMessage().contains("No operations allowed after connection closed.") || e.getMessage().contains("Communications link failure")) { // Check for common closed connection errors
                try {
                    connect(); // Attempt reconnect

                    try (PreparedStatement retryStatement = connection.prepareStatement(sql)) { // New Statement for retry
                        for (int i = 0; i < parameters.length; i++) {
                            retryStatement.setObject(i + 1, parameters[i]);
                        }
                        try (ResultSet resultSet = retryStatement.executeQuery()) {
                            return handler.apply(resultSet);
                        }
                    }
                } catch (SQLException ex) {
                    plugin.getLogger().severe("Failed to reconnect to database: " + ex.getMessage() + " | SQL: " + sql);
                    throw ex; // Re-throw the reconnect failure
                }
            }
            throw e; // Re-throw the original exception if not a connection error
        }
    }


    public void update(String sql, Object... parameters) throws SQLException {
        try {
            connect(); // Ensure connection before every update!

            try (PreparedStatement statement = connection.prepareStatement(sql)) { // Try-with-resources
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]); // Start from index 1, not 0
                }
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL Update Error: " + e.getMessage() + " | SQL: " + sql);


            if (e.getMessage().contains("No operations allowed after connection closed.") || e.getMessage().contains("Communications link failure")) { // Reconnect logic
                try {
                    connect(); // Attempt a reconnect if connection is closed.

                    try (PreparedStatement retryStatement = connection.prepareStatement(sql)) {  // **New Statement!!**
                        for (int i = 0; i < parameters.length; i++) {
                            retryStatement.setObject(i + 1, parameters[i]);
                        }

                        retryStatement.executeUpdate();

                    }
                } catch (SQLException ex) {
                    plugin.getLogger().severe("Failed to reconnect to database: " + e.getMessage() + " | SQL: " + sql);
                    throw ex; // Re-throw the original exception if reconnect fails
                }
            }
            throw e; // Re-throw if not a closed connection error

        }
    }

    public void execute(String sql) throws SQLException {  // New method for DDL statements
        try {
            connect();
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql); // Use execute() for DDL
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL Execution Error: " + e.getMessage() + " | SQL: " + sql);
            // Reconnection logic (similar to query and update methods) if needed
            throw e;
        }
    }
}