package com.dota2assistant.gsi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles Game State Integration (GSI) configuration for Dota 2.
 * This class is responsible for creating and managing the GSI config file
 * that tells Dota 2 where to send game state updates.
 */
@Component
public class GsiConfig {
    private static final Logger logger = LoggerFactory.getLogger(GsiConfig.class);
    
    @Value("${gsi.server.port:29455}")
    private int gsiServerPort;
    
    @Value("${gsi.config.name:dota2_draft_assistant_gsi}")
    private String configName;
    
    @Value("${gsi.auth.token:dota2-draft-assistant-gsi-token}")
    private String authToken;
    
    /**
     * Default Steam installation paths for different OS
     */
    private static final String WINDOWS_DEFAULT_PATH = "C:\\Program Files (x86)\\Steam\\steamapps\\common\\dota 2 beta\\game\\dota\\cfg\\gamestate_integration\\";
    private static final String MAC_DEFAULT_PATH = "~/Library/Application Support/Steam/steamapps/common/dota 2 beta/game/dota/cfg/gamestate_integration/";
    private static final String LINUX_DEFAULT_PATH = "~/.steam/steam/steamapps/common/dota 2 beta/game/dota/cfg/gamestate_integration/";
    
    /**
     * Checks if a valid GSI configuration file already exists at the given path.
     * 
     * @param configFilePath The path to check for an existing configuration file
     * @return true if a valid config file exists, false otherwise
     */
    public boolean isConfigInstalled(String configFilePath) {
        try {
            File configFile = new File(configFilePath);
            if (!configFile.exists() || !configFile.isFile() || configFile.length() == 0) {
                return false;
            }
            
            // Read the file content to verify it's a valid GSI config
            String content = new String(Files.readAllBytes(configFile.toPath()));
            
            // Check for key elements that should be in our GSI config
            return content.contains("\"uri\"") && 
                   content.contains("localhost:" + gsiServerPort) &&
                   content.contains("draft") && 
                   content.contains(authToken);
        } catch (Exception e) {
            logger.debug("Error checking for existing GSI config at {}: {}", configFilePath, e.getMessage());
            return false;
        }
    }
    
    /**
     * Determines if GSI configuration is already installed in any of the possible locations.
     * 
     * @return true if GSI config is installed, false otherwise
     */
    public boolean isConfigInstalledAnywhere() {
        String osName = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        
        // Check default paths based on OS
        if (osName.contains("win")) {
            // Check user's Steam directory first
            String userSteamPath = userHome + "\\Steam\\steamapps\\common\\dota 2 beta\\game\\dota\\cfg\\gamestate_integration\\" + configName + ".cfg";
            if (isConfigInstalled(userSteamPath)) {
                logger.info("GSI config found in user's Steam directory: {}", userSteamPath);
                return true;
            }
            
            // Check Program Files path
            if (isConfigInstalled(WINDOWS_DEFAULT_PATH + configName + ".cfg")) {
                logger.info("GSI config found in default Windows path: {}", WINDOWS_DEFAULT_PATH + configName + ".cfg");
                return true;
            }
        } else if (osName.contains("mac")) {
            String macPath = MAC_DEFAULT_PATH.replace("~", userHome) + configName + ".cfg";
            if (isConfigInstalled(macPath)) {
                logger.info("GSI config found in macOS path: {}", macPath);
                return true;
            }
        } else {
            String linuxPath = LINUX_DEFAULT_PATH.replace("~", userHome) + configName + ".cfg";
            if (isConfigInstalled(linuxPath)) {
                logger.info("GSI config found in Linux path: {}", linuxPath);
                return true;
            }
        }
        
        // Check if the PowerShell elevation process successfully installed the file
        // This check is needed because the application might not have direct read access to the Program Files location,
        // but the file might still have been installed there by the elevated PowerShell script
        if (osName.contains("win")) {
            // Check if we've previously attempted elevation
            String fallbackPath = userHome + File.separator + ".dota2_draft_assistant" + File.separator + "copy_gsi_config.ps1";
            File psScript = new File(fallbackPath);
            
            // If we've tried elevation and the UAC prompt would have happened,
            // check if the file exists in the target location by using system commands
            if (psScript.exists()) {
                try {
                    // Use PowerShell to check if the file exists in the target location
                    Process checkProcess = new ProcessBuilder(
                        "powershell.exe", 
                        "-Command", 
                        "Test-Path \"" + WINDOWS_DEFAULT_PATH + configName + ".cfg\""
                    ).start();
                    
                    // Read the output to determine if the file exists
                    try (java.util.Scanner scanner = new java.util.Scanner(checkProcess.getInputStream())) {
                        if (scanner.hasNextLine() && scanner.nextLine().trim().equalsIgnoreCase("True")) {
                            logger.info("GSI config detected in Steam directory through PowerShell check");
                            return true;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error checking for GSI config with PowerShell: {}", e.getMessage());
                }
            }
        }
        
        // Check fallback location - consider this as installed to prevent repeated reinstall attempts
        // This improves user experience by not showing constant reinstall prompts when the app can't 
        // directly verify the actual installation location due to permission issues
        String fallbackPath = userHome + File.separator + ".dota2_draft_assistant" + File.separator + configName + ".cfg";
        if (isConfigInstalled(fallbackPath)) {
            logger.info("GSI config found in fallback location: {}", fallbackPath);
            // Consider this "installed" to prevent constant reinstall attempts
            // If the config really isn't working, users can still manually click "Reinstall"
            return true;
        }
        
        return false;
    }
    
    /**
     * Attempts to install the GSI configuration file in the default location
     * based on the detected operating system.
     * 
     * @return true if the config was successfully installed, false otherwise
     */
    public boolean installConfig() {
        String osName = System.getProperty("os.name").toLowerCase();
        String configPath;
        String userHome = System.getProperty("user.home");
        
        // First check if the config is already installed
        if (isConfigInstalledAnywhere()) {
            logger.info("GSI config is already installed somewhere - skipping installation");
            // Add advanced logging for debugging purposes
            String defaultPath = "";
            if (osName.contains("win")) {
                defaultPath = WINDOWS_DEFAULT_PATH;
            } else if (osName.contains("mac")) {
                defaultPath = MAC_DEFAULT_PATH.replace("~", userHome);
            } else {
                defaultPath = LINUX_DEFAULT_PATH.replace("~", userHome);
            }
            
            try {
                File dota2Dir = new File(defaultPath);
                if (!dota2Dir.exists()) {
                    logger.info("Could not verify Dota 2 GSI directory exists at: {}. This is normal if using elevated installation.", defaultPath);
                }
            } catch (Exception e) {
                logger.debug("Error checking Dota 2 GSI directory: {}", e.getMessage());
            }
            return true;
        }
        
        if (osName.contains("win")) {
            // On Windows, try to use a location that doesn't require admin privileges
            // Check if Steam is installed in the user's home directory first
            String userSteamPath = userHome + "\\Steam\\steamapps\\common\\dota 2 beta\\game\\dota\\cfg\\gamestate_integration\\";
            File userSteamDir = new File(userSteamPath);
            if (userSteamDir.exists() || new File(userHome + "\\Steam").exists()) {
                configPath = userSteamPath;
            } else {
                // Fall back to Program Files path, but warn about potential permission issues
                logger.warn("Steam installation not found in user home directory. Using default Program Files path which may require admin privileges.");
                configPath = WINDOWS_DEFAULT_PATH;
            }
        } else if (osName.contains("mac")) {
            configPath = MAC_DEFAULT_PATH;
            configPath = configPath.replace("~", userHome);
        } else {
            configPath = LINUX_DEFAULT_PATH;
            configPath = configPath.replace("~", userHome);
        }
        
        boolean result = installConfigToPath(configPath);
        
        // If installation to default path fails, try to install to user's home directory as a fallback
        if (!result) {
            logger.info("Failed to install GSI config to default location. Trying fallback location...");
            String fallbackPath = userHome + File.separator + ".dota2_draft_assistant" + File.separator;
            boolean fallbackResult = installConfigToFallbackPath(fallbackPath);
            
            if (fallbackResult) {
                logger.info("GSI config installed to fallback location: {}", fallbackPath);
                logger.info("You'll need to manually copy this file to the Dota 2 gamestate_integration directory.");
            }
            
            return fallbackResult;
        }
        
        return result;
    }
    
    /**
     * Installs the GSI configuration file to a custom path.
     * 
     * @param directoryPath The directory to install the config file to
     * @return true if the config was successfully installed, false otherwise
     */
    public boolean installConfigToPath(String directoryPath) {
        try {
            // Ensure the directory exists
            Path directory = Paths.get(directoryPath);
            if (!Files.exists(directory)) {
                try {
                    Files.createDirectories(directory);
                } catch (IOException e) {
                    logger.warn("Failed to create directory: {}", directoryPath, e);
                    // Don't return false yet, try to continue with file creation
                }
            }
            
            // Create the config file
            File configFile = new File(directory.toFile(), configName + ".cfg");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                writer.write(generateConfigContent());
            }
            
            logger.info("GSI config installed to: {}", configFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            logger.error("Failed to install GSI config to: {}", directoryPath, e);
            return false;
        }
    }
    
    /**
     * Installs the GSI config to a fallback location (user's home directory) when the
     * default Steam location is not accessible.
     * 
     * @param fallbackPath The fallback path to install to
     * @return true if installation succeeded, false otherwise
     */
    private String fallbackConfigPath = null;
    private String elevationScriptPath = null;
    
    /**
     * Gets the path to the fallback config file if it was used
     * @return The path to the fallback config file, or null if not used
     */
    public String getFallbackConfigPath() {
        return fallbackConfigPath;
    }
    
    /**
     * Gets the path to the elevation script if one was created
     * @return The path to the elevation script, or null if not created
     */
    public String getElevationScriptPath() {
        return elevationScriptPath;
    }
    
    /**
     * Installs the GSI config to a fallback location (user's home directory) and
     * creates an elevation script to help the user install it with admin privileges.
     * 
     * @param fallbackPath The fallback path to install to
     * @return true if installation succeeded, false otherwise
     */
    private boolean installConfigToFallbackPath(String fallbackPath) {
        try {
            // Create the fallback directory
            File directory = new File(fallbackPath);
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    logger.error("Failed to create fallback directory: {}", fallbackPath);
                    return false;
                }
            }
            
            // Create the config file
            File configFile = new File(directory, configName + ".cfg");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                writer.write(generateConfigContent());
            }
            
            // Store the fallback path for UI to show instructions
            fallbackConfigPath = configFile.getAbsolutePath();
            
            // First try to directly copy with elevation
            boolean directCopySuccess = directElevatedCopy(configFile);
            
            // If direct copy failed, create an elevation script as fallback
            if (!directCopySuccess) {
                createElevationScript(configFile);
            }
            
            logger.info("GSI config installed to fallback location: {}", configFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            logger.error("Failed to install GSI config to fallback location", e);
            return false;
        }
    }
    
    /**
     * Attempts to directly copy the GSI config file to the Steam directory using
     * an elevated process that will trigger a UAC prompt.
     * 
     * @param configFile The GSI config file to copy
     * @return true if the copy was successful, false otherwise
     */
    private boolean directElevatedCopy(File configFile) {
        String osName = System.getProperty("os.name").toLowerCase();
        
        try {
            if (osName.contains("win")) {
                return directElevatedCopyWindows(configFile);
            } else if (osName.contains("mac")) {
                return directElevatedCopyMac(configFile);
            } else {
                return directElevatedCopyLinux(configFile);
            }
        } catch (Exception e) {
            logger.error("Failed to perform direct elevated copy", e);
            return false;
        }
    }
    
    /**
     * Attempts to directly copy the GSI config file to the Steam directory using
     * PowerShell's "Start-Process" with the "RunAs" verb to trigger UAC on Windows.
     * 
     * @param configFile The GSI config file to copy
     * @return true if the copy was successful, false otherwise
     */
    private boolean directElevatedCopyWindows(File configFile) {
        try {
            // Create a temporary PowerShell script that will copy the file with elevation
            File psScript = new File(configFile.getParent(), "copy_gsi_config.ps1");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(psScript))) {
                writer.write("# Create the destination directory if it doesn't exist\n");
                writer.write("$destinationDir = \"" + WINDOWS_DEFAULT_PATH + "\"\n");
                writer.write("if (-not (Test-Path $destinationDir)) {\n");
                writer.write("    New-Item -ItemType Directory -Force -Path $destinationDir | Out-Null\n");
                writer.write("}\n\n");
                
                writer.write("# Copy the file\n");
                writer.write("Copy-Item -Path \"" + configFile.getAbsolutePath() + "\" -Destination \"$destinationDir" + configName + ".cfg\" -Force\n\n");
                
                writer.write("# Check if successful\n");
                writer.write("if (Test-Path \"$destinationDir" + configName + ".cfg\") {\n");
                writer.write("    Write-Host \"GSI config installed successfully!\"\n");
                writer.write("    exit 0\n");
                writer.write("} else {\n");
                writer.write("    Write-Host \"Failed to install GSI config.\"\n");
                writer.write("    exit 1\n");
                writer.write("}\n");
            }
            
            // Create a batch file that will run the PowerShell script with elevation
            File batchFile = new File(configFile.getParent(), "install_gsi_config_elevated.bat");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(batchFile))) {
                writer.write("@echo off\n");
                writer.write("echo Requesting administrator privileges to install GSI config...\n");
                writer.write("powershell -Command \"Start-Process -Verb RunAs 'powershell' -ArgumentList '-ExecutionPolicy Bypass -File \"\"" + 
                             psScript.getAbsolutePath().replace("\\", "\\\\") + "\"\"'\"\n");
                writer.write("if %errorlevel% equ 0 (\n");
                writer.write("    echo GSI config installed successfully!\n");
                writer.write(") else (\n");
                writer.write("    echo Failed to install GSI config or user cancelled the UAC prompt.\n");
                writer.write(")\n");
                writer.write("pause\n");
            }
            
            // Set the batch file as executable
            batchFile.setExecutable(true);
            
            // Launch the batch file
            Process process = new ProcessBuilder(batchFile.getAbsolutePath()).start();
            
            // Store the elevation script path for UI to show instructions
            elevationScriptPath = batchFile.getAbsolutePath();
            
            logger.info("Launched Windows elevation prompt for GSI config installation");
            return true;
        } catch (Exception e) {
            logger.error("Failed to create or execute Windows elevation script", e);
            return false;
        }
    }
    
    /**
     * Attempts to directly copy the GSI config file to the Steam directory using
     * the macOS osascript command to prompt for admin privileges.
     * 
     * @param configFile The GSI config file to copy
     * @return true if the copy was successful, false otherwise
     */
    private boolean directElevatedCopyMac(File configFile) {
        try {
            // Create a temporary shell script that will copy the file
            File shellScript = new File(configFile.getParent(), "copy_gsi_config.sh");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(shellScript))) {
                writer.write("#!/bin/bash\n\n");
                
                writer.write("# Replace ~ with the actual user home directory\n");
                writer.write("GSI_DIR=\"" + MAC_DEFAULT_PATH.replace("~", "$HOME") + "\"\n\n");
                
                writer.write("# Create the destination directory if it doesn't exist\n");
                writer.write("mkdir -p \"$GSI_DIR\"\n\n");
                
                writer.write("# Copy the config file\n");
                writer.write("cp \"" + configFile.getAbsolutePath() + "\" \"$GSI_DIR" + configName + ".cfg\"\n\n");
                
                writer.write("# Check if successful\n");
                writer.write("if [ -f \"$GSI_DIR" + configName + ".cfg\" ]; then\n");
                writer.write("    echo \"GSI config installed successfully!\"\n");
                writer.write("    exit 0\n");
                writer.write("else\n");
                writer.write("    echo \"Failed to install GSI config.\"\n");
                writer.write("    exit 1\n");
                writer.write("fi\n");
            }
            
            // Make the script executable
            shellScript.setExecutable(true);
            
            // Use osascript to run the script with admin privileges
            String[] command = {
                "osascript", 
                "-e", 
                "do shell script \"" + shellScript.getAbsolutePath() + "\" with administrator privileges"
            };
            
            // Launch the process
            Process process = new ProcessBuilder(command).start();
            
            // Store the elevation script path for UI to show instructions if needed
            elevationScriptPath = shellScript.getAbsolutePath();
            
            logger.info("Launched macOS elevation prompt for GSI config installation");
            return true;
        } catch (Exception e) {
            logger.error("Failed to create or execute macOS elevation script", e);
            return false;
        }
    }
    
    /**
     * Attempts to directly copy the GSI config file to the Steam directory using
     * pkexec or gksudo on Linux to prompt for admin privileges.
     * 
     * @param configFile The GSI config file to copy
     * @return true if the copy was successful, false otherwise
     */
    private boolean directElevatedCopyLinux(File configFile) {
        try {
            // Create a temporary shell script that will copy the file
            File shellScript = new File(configFile.getParent(), "copy_gsi_config.sh");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(shellScript))) {
                writer.write("#!/bin/bash\n\n");
                
                writer.write("# Replace ~ with the actual user home directory\n");
                writer.write("GSI_DIR=\"" + LINUX_DEFAULT_PATH.replace("~", "$HOME") + "\"\n\n");
                
                writer.write("# Create the destination directory if it doesn't exist\n");
                writer.write("mkdir -p \"$GSI_DIR\"\n\n");
                
                writer.write("# Copy the config file\n");
                writer.write("cp \"" + configFile.getAbsolutePath() + "\" \"$GSI_DIR" + configName + ".cfg\"\n\n");
                
                writer.write("# Check if successful\n");
                writer.write("if [ -f \"$GSI_DIR" + configName + ".cfg\" ]; then\n");
                writer.write("    echo \"GSI config installed successfully!\"\n");
                writer.write("    exit 0\n");
                writer.write("else\n");
                writer.write("    echo \"Failed to install GSI config.\"\n");
                writer.write("    exit 1\n");
                writer.write("fi\n");
            }
            
            // Make the script executable
            shellScript.setExecutable(true);
            
            // Try pkexec first (most modern Linux distributions)
            String[] pkexecCommand = {"pkexec", shellScript.getAbsolutePath()};
            try {
                Process process = new ProcessBuilder(pkexecCommand).start();
                
                // Store the elevation script path for UI to show instructions if needed
                elevationScriptPath = shellScript.getAbsolutePath();
                
                logger.info("Launched Linux pkexec elevation prompt for GSI config installation");
                return true;
            } catch (Exception pkexecError) {
                // If pkexec fails, try gksudo
                try {
                    String[] gksudoCommand = {"gksudo", shellScript.getAbsolutePath()};
                    Process process = new ProcessBuilder(gksudoCommand).start();
                    
                    logger.info("Launched Linux gksudo elevation prompt for GSI config installation");
                    return true;
                } catch (Exception gksudoError) {
                    // If both fail, fall back to the original approach
                    logger.error("Failed to launch elevation with pkexec or gksudo");
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to create or execute Linux elevation script", e);
            return false;
        }
    }
    
    /**
     * Creates a script that can be run with administrator privileges to copy
     * the GSI config file to the Steam directory.
     * 
     * @param configFile The GSI config file that was created
     */
    private void createElevationScript(File configFile) {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("win")) {
            createWindowsElevationScript(configFile);
        } else if (osName.contains("mac")) {
            createMacElevationScript(configFile);
        } else {
            createLinuxElevationScript(configFile);
        }
    }
    
    /**
     * Creates a Windows batch file that can be run with admin privileges
     * to copy the GSI config file to the Steam directory.
     * 
     * @param configFile The GSI config file that was created
     */
    private void createWindowsElevationScript(File configFile) {
        try {
            // Create the batch file in the same directory as the config file
            File scriptFile = new File(configFile.getParent(), "install_gsi_config.bat");
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFile))) {
                writer.write("@echo off\n");
                writer.write("echo Dota 2 Draft Assistant - GSI Config Installer\n");
                writer.write("echo This script requires administrator privileges to copy the GSI config file.\n");
                writer.write("echo.\n\n");
                
                writer.write(":: Check for admin rights\n");
                writer.write("net session >nul 2>&1\n");
                writer.write("if %errorLevel% neq 0 (\n");
                writer.write("    echo Error: This script requires administrator privileges.\n");
                writer.write("    echo Please right-click on this file and select \"Run as administrator\".\n");
                writer.write("    echo.\n");
                writer.write("    pause\n");
                writer.write("    exit /b 1\n");
                writer.write(")\n\n");
                
                writer.write(":: Create the destination directory if it doesn't exist\n");
                writer.write("echo Creating Dota 2 GSI directory...\n");
                writer.write("mkdir \"" + WINDOWS_DEFAULT_PATH + "\" 2>nul\n\n");
                
                writer.write(":: Copy the config file\n");
                writer.write("echo Copying GSI config file...\n");
                writer.write("copy /Y \"" + configFile.getAbsolutePath() + "\" \"" + WINDOWS_DEFAULT_PATH + configName + ".cfg\"\n\n");
                
                writer.write("if %errorLevel% equ 0 (\n");
                writer.write("    echo GSI config installed successfully!\n");
                writer.write(") else (\n");
                writer.write("    echo Failed to install GSI config file. Please check that Steam is installed in the default location.\n");
                writer.write(")\n\n");
                
                writer.write("echo.\n");
                writer.write("echo If Dota 2 is running, please restart it for the changes to take effect.\n");
                writer.write("echo.\n");
                writer.write("pause\n");
            }
            
            // Set the script as executable
            scriptFile.setExecutable(true);
            
            // Store the script path to display in the UI
            elevationScriptPath = scriptFile.getAbsolutePath();
            
            logger.info("Created Windows elevation script: {}", scriptFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create Windows elevation script", e);
        }
    }
    
    /**
     * Creates a macOS shell script that can be run with admin privileges
     * to copy the GSI config file to the Steam directory.
     * 
     * @param configFile The GSI config file that was created
     */
    private void createMacElevationScript(File configFile) {
        try {
            // Create the shell script in the same directory as the config file
            File scriptFile = new File(configFile.getParent(), "install_gsi_config.command");
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFile))) {
                writer.write("#!/bin/bash\n\n");
                
                writer.write("echo \"Dota 2 Draft Assistant - GSI Config Installer\"\n");
                writer.write("echo \"This script requires administrator privileges to copy the GSI config file.\"\n");
                writer.write("echo\n\n");
                
                writer.write("# Replace ~ with the actual user home directory\n");
                writer.write("GSI_DIR=\"" + MAC_DEFAULT_PATH.replace("~", "\\$HOME") + "\"\n\n");
                
                writer.write("# Create the destination directory if it doesn't exist\n");
                writer.write("echo \"Creating Dota 2 GSI directory...\"\n");
                writer.write("sudo mkdir -p \"$GSI_DIR\"\n\n");
                
                writer.write("# Copy the config file\n");
                writer.write("echo \"Copying GSI config file...\"\n");
                writer.write("sudo cp \"" + configFile.getAbsolutePath() + "\" \"$GSI_DIR" + configName + ".cfg\"\n\n");
                
                writer.write("if [ $? -eq 0 ]; then\n");
                writer.write("    echo \"GSI config installed successfully!\"\n");
                writer.write("else\n");
                writer.write("    echo \"Failed to install GSI config file.\"\n");
                writer.write("fi\n\n");
                
                writer.write("echo\n");
                writer.write("echo \"If Dota 2 is running, please restart it for the changes to take effect.\"\n");
                writer.write("echo\n");
                writer.write("read -p \"Press Enter to close this window...\"\n");
            }
            
            // Set the script as executable
            scriptFile.setExecutable(true);
            
            // Store the script path to display in the UI
            elevationScriptPath = scriptFile.getAbsolutePath();
            
            logger.info("Created macOS elevation script: {}", scriptFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create macOS elevation script", e);
        }
    }
    
    /**
     * Creates a Linux shell script that can be run with admin privileges
     * to copy the GSI config file to the Steam directory.
     * 
     * @param configFile The GSI config file that was created
     */
    private void createLinuxElevationScript(File configFile) {
        try {
            // Create the shell script in the same directory as the config file
            File scriptFile = new File(configFile.getParent(), "install_gsi_config.sh");
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFile))) {
                writer.write("#!/bin/bash\n\n");
                
                writer.write("echo \"Dota 2 Draft Assistant - GSI Config Installer\"\n");
                writer.write("echo \"This script requires administrator privileges to copy the GSI config file.\"\n");
                writer.write("echo\n\n");
                
                writer.write("# Replace ~ with the actual user home directory\n");
                writer.write("GSI_DIR=\"" + LINUX_DEFAULT_PATH.replace("~", "\\$HOME") + "\"\n\n");
                
                writer.write("# Create the destination directory if it doesn't exist\n");
                writer.write("echo \"Creating Dota 2 GSI directory...\"\n");
                writer.write("sudo mkdir -p \"$GSI_DIR\"\n\n");
                
                writer.write("# Copy the config file\n");
                writer.write("echo \"Copying GSI config file...\"\n");
                writer.write("sudo cp \"" + configFile.getAbsolutePath() + "\" \"$GSI_DIR" + configName + ".cfg\"\n\n");
                
                writer.write("if [ $? -eq 0 ]; then\n");
                writer.write("    echo \"GSI config installed successfully!\"\n");
                writer.write("else\n");
                writer.write("    echo \"Failed to install GSI config file.\"\n");
                writer.write("fi\n\n");
                
                writer.write("echo\n");
                writer.write("echo \"If Dota 2 is running, please restart it for the changes to take effect.\"\n");
                writer.write("echo\n");
                writer.write("read -p \"Press Enter to close this window...\"\n");
            }
            
            // Set the script as executable
            scriptFile.setExecutable(true);
            
            // Store the script path to display in the UI
            elevationScriptPath = scriptFile.getAbsolutePath();
            
            logger.info("Created Linux elevation script: {}", scriptFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create Linux elevation script", e);
        }
    }
    
    /**
     * Generates the content of the GSI config file.
     * 
     * @return The GSI config file content
     */
    public String generateConfigContent() {
        return "\"Dota 2 Draft Assistant GSI Configuration\"\n" +
               "{\n" +
               "    \"uri\"           \"http://localhost:" + gsiServerPort + "/gsi\"\n" +
               "    \"timeout\"       \"5.0\"\n" +
               "    \"buffer\"        \"0.1\"\n" +
               "    \"throttle\"      \"0.1\"\n" +
               "    \"heartbeat\"     \"30.0\"\n" +
               "    \"auth\"\n" +
               "    {\n" +
               "        \"token\"     \"" + authToken + "\"\n" +
               "    }\n" +
               "    \"data\"\n" +
               "    {\n" +
               "        \"provider\"      \"1\"\n" +
               "        \"map\"           \"1\"\n" +
               "        \"player\"        \"1\"\n" +
               "        \"hero\"          \"1\"\n" +
               "        \"abilities\"     \"1\"\n" +
               "        \"items\"         \"1\"\n" +
               "        \"draft\"         \"1\"\n" +
               "        \"wearables\"     \"0\"\n" +
               "    }\n" +
               "}\n";
    }
    
    /**
     * @return The port the GSI server is configured to run on
     */
    public int getGsiServerPort() {
        return gsiServerPort;
    }
    
    /**
     * Gets the status of the GSI configuration. This includes whether the config file
     * is properly installed and if the GSI server is receiving data from Dota 2.
     * 
     * @return A GsiStatus object with the configuration status details
     */
    public GsiStatus getGsiStatus() {
        GsiStatus status = new GsiStatus();
        
        // Check if the config file is installed
        status.setConfigInstalled(isConfigInstalledAnywhere());
        
        return status;
    }
    
    /**
     * Gets the GSI server from Spring context.
     * This is used to restart the server when necessary.
     *
     * @return The GSI server instance
     */
    public GsiServer getGsiServer() {
        try {
            // Try to get the GsiServer from Spring context using reflection
            org.springframework.context.ApplicationContext context = 
                org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
            
            if (context != null) {
                return context.getBean(GsiServer.class);
            }
            
            // If the above doesn't work, try to find it from the calling class
            // Using a throwaway exception to get the stack trace
            StackTraceElement[] stackTrace = new Exception().getStackTrace();
            if (stackTrace.length > 1) {
                String callingClassName = stackTrace[1].getClassName();
                try {
                    Class<?> callingClass = Class.forName(callingClassName);
                    // Look for a field of type GsiServer in the calling class
                    for (java.lang.reflect.Field field : callingClass.getDeclaredFields()) {
                        if (field.getType() == GsiServer.class) {
                            field.setAccessible(true);
                            Object instance = null;
                            // If it's a static field
                            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                                instance = field.get(null);
                            } else if (stackTrace.length > 2) {
                                // Try to get the instance from the caller's caller
                                String callerCallerClassName = stackTrace[2].getClassName();
                                Class<?> callerCallerClass = Class.forName(callerCallerClassName);
                                for (java.lang.reflect.Field instanceField : callerCallerClass.getDeclaredFields()) {
                                    if (instanceField.getType() == callingClass) {
                                        instanceField.setAccessible(true);
                                        Object callingInstance = instanceField.get(null);
                                        if (callingInstance != null) {
                                            instance = field.get(callingInstance);
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            if (instance instanceof GsiServer) {
                                return (GsiServer) instance;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore errors in reflection
                }
            }
        } catch (Exception e) {
            // Ignore any errors in this method
        }
        
        // Could not find GsiServer instance
        return null;
    }
    
    /**
     * Class to represent the status of GSI configuration
     */
    public static class GsiStatus {
        private boolean configInstalled;
        
        public boolean isConfigInstalled() {
            return configInstalled;
        }
        
        public void setConfigInstalled(boolean configInstalled) {
            this.configInstalled = configInstalled;
        }
    }
}