package com.dota2assistant.data.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Interface for database operations.
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
    <T> List<T> query(String sql, SqliteDatabaseManager.ResultSetHandler<T> handler, Object... params) throws SQLException;
    
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
    <T> T queryForObject(String sql, SqliteDatabaseManager.ResultSetHandler<T> handler, Object... params) throws SQLException;
    
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
     * Interface for mapping ResultSet rows to objects.
     */
    interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }
}