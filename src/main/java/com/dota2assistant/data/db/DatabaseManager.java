package com.dota2assistant.data.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Interface for database operations.
 * Provides methods for database connectivity, querying, and transaction management.
 */
public interface DatabaseManager {
    
    /**
     * Gets a database connection.
     * 
     * @return a Connection object
     * @throws SQLException if a database access error occurs
     */
    Connection getConnection() throws SQLException;
    
    /**
     * Initializes the database schema if it doesn't exist.
     * 
     * @throws SQLException if a database access error occurs
     */
    void initDatabase() throws SQLException;
    
    /**
     * Closes the database connection.
     */
    void closeConnection();
    
    /**
     * Executes a SQL update statement.
     * 
     * @param sql the SQL statement to execute
     * @throws SQLException if a database access error occurs
     */
    void executeUpdate(String sql) throws SQLException;
    
    /**
     * Executes a SQL query with parameters and returns a list of objects.
     * 
     * @param <T> the type of objects to return
     * @param sql the SQL query to execute
     * @param handler the handler to convert ResultSet rows to objects
     * @param params the parameters for the query
     * @return a list of objects
     * @throws SQLException if a database access error occurs
     */
    <T> List<T> query(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException;
    
    /**
     * Executes a SQL query with parameters and returns a single object.
     * 
     * @param <T> the type of object to return
     * @param sql the SQL query to execute
     * @param handler the handler to convert the ResultSet row to an object
     * @param params the parameters for the query
     * @return the object, or null if no result
     * @throws SQLException if a database access error occurs
     */
    <T> T queryForObject(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException;
    
    /**
     * Executes a SQL update statement with parameters.
     * 
     * @param sql the SQL statement to execute
     * @param params the parameters for the statement
     * @return the number of rows affected
     * @throws SQLException if a database access error occurs
     */
    int executeUpdate(String sql, Object... params) throws SQLException;
    
    /**
     * Executes a SQL insert statement with parameters and returns the generated ID.
     * 
     * @param sql the SQL insert statement to execute
     * @param params the parameters for the statement
     * @return the generated ID
     * @throws SQLException if a database access error occurs
     */
    long executeInsert(String sql, Object... params) throws SQLException;
    
    /**
     * Executes a batch of SQL statements with parameters.
     * 
     * @param sql the SQL statement to execute
     * @param batchParams the list of parameter arrays for each batch
     * @return an array of row counts for each statement
     * @throws SQLException if a database access error occurs
     */
    int[] executeBatch(String sql, List<Object[]> batchParams) throws SQLException;
    
    /**
     * Begins a database transaction.
     * 
     * @throws SQLException if a database access error occurs
     */
    void beginTransaction() throws SQLException;
    
    /**
     * Commits a database transaction.
     * 
     * @throws SQLException if a database access error occurs
     */
    void commitTransaction() throws SQLException;
    
    /**
     * Rolls back a database transaction.
     * 
     * @throws SQLException if a database access error occurs
     */
    void rollbackTransaction() throws SQLException;
    
    /**
     * Executes a SQL script containing multiple statements.
     * 
     * @param script the SQL script to execute
     * @throws SQLException if a database access error occurs
     */
    void executeScript(String script) throws SQLException;
    
    /**
     * Check the health status of the database connection
     * 
     * @return true if the database is available and responding, false otherwise
     */
    default boolean healthCheck() {
        try (Connection conn = getConnection()) {
            if (conn != null && !conn.isClosed()) {
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("SELECT 1");
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Get detailed health status information about the database connection
     * 
     * @return A Map containing health status information
     */
    default Map<String, Object> getHealthStatus() {
        HashMap<String, Object> status = new HashMap<>();
        try {
            boolean isHealthy = healthCheck();
            status.put("healthy", isHealthy);
            status.put("timestamp", System.currentTimeMillis());
            return status;
        } catch (Exception e) {
            status.put("healthy", false);
            status.put("error", e.getMessage());
            return status;
        }
    }
    
    /**
     * Interface for mapping ResultSet rows to objects.
     */
    interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }
}