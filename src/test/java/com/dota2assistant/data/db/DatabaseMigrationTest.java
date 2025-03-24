package com.dota2assistant.data.db;

import com.dota2assistant.data.service.DatabaseMigrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig
class DatabaseMigrationTest {

    @Mock
    private DatabaseManager databaseManager;
    
    @Mock
    private Connection connection;
    
    @Mock
    private PreparedStatement statement;
    
    @Mock
    private ResultSet resultSet;
    
    @InjectMocks
    private DatabaseMigrationService migrationService;
    
    @Test
    void testInitialize() throws SQLException {
        // Set up mocks
        when(databaseManager.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        
        // Mock the version lookup
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getInt(1)).thenReturn(5); // Current version is 5
        
        // Call initialize
        migrationService.initialize();
        
        // Verify that executeUpdate was called to create the version table
        verify(databaseManager).executeUpdate(contains("CREATE TABLE IF NOT EXISTS db_version"));
        
        // Verify that we checked the current version
        verify(statement).executeQuery();
        
        // Should try to look for migration scripts
        // Note: We're not going to mock the resource loading here as it's quite complex with Spring resources
    }
}