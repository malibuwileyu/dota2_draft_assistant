package com.dota2assistant.infrastructure.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.*;

/**
 * File-based credential store with basic encryption.
 * Fallback for systems without native secure storage.
 * 
 * Uses AES-GCM encryption with a key derived from machine-specific data.
 * NOT as secure as native credential stores - use only as fallback.
 */
public class FileCredentialStore implements CredentialStore {
    
    private static final Logger log = LoggerFactory.getLogger(FileCredentialStore.class);
    private static final String CREDENTIALS_FILE = "credentials.enc";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 100000;
    
    private final Path credentialsPath;
    private final SecretKey encryptionKey;
    
    public FileCredentialStore(Path appDataDir) {
        this.credentialsPath = appDataDir.resolve(CREDENTIALS_FILE);
        this.encryptionKey = deriveKey();
    }
    
    @Override
    public boolean store(String key, String value) {
        try {
            Map<String, String> credentials = loadCredentials();
            credentials.put(key, value);
            saveCredentials(credentials);
            log.debug("Stored credential '{}' in file store", key);
            return true;
        } catch (Exception e) {
            log.error("Error storing credential in file: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public Optional<String> retrieve(String key) {
        try {
            Map<String, String> credentials = loadCredentials();
            String value = credentials.get(key);
            if (value != null) {
                log.debug("Retrieved credential '{}' from file store", key);
                return Optional.of(value);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error retrieving credential from file: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public boolean delete(String key) {
        try {
            Map<String, String> credentials = loadCredentials();
            credentials.remove(key);
            saveCredentials(credentials);
            log.debug("Deleted credential '{}' from file store", key);
            return true;
        } catch (Exception e) {
            log.error("Error deleting credential from file: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isAvailable() {
        try {
            Path parent = credentialsPath.getParent();
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            return Files.isWritable(parent);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getStoreName() {
        return "Encrypted File Store";
    }
    
    private Map<String, String> loadCredentials() {
        if (!Files.exists(credentialsPath)) {
            return new HashMap<>();
        }
        
        try {
            byte[] encrypted = Files.readAllBytes(credentialsPath);
            if (encrypted.length < GCM_IV_LENGTH + SALT_LENGTH) {
                return new HashMap<>();
            }
            
            // Extract IV and encrypted data
            byte[] iv = Arrays.copyOfRange(encrypted, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(encrypted, GCM_IV_LENGTH, encrypted.length);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, spec);
            
            byte[] decrypted = cipher.doFinal(ciphertext);
            String json = new String(decrypted, StandardCharsets.UTF_8);
            
            // Simple JSON parsing (avoid Jackson dependency in this class)
            return parseSimpleJson(json);
            
        } catch (Exception e) {
            log.warn("Failed to load credentials file, starting fresh: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    private void saveCredentials(Map<String, String> credentials) throws Exception {
        String json = toSimpleJson(credentials);
        byte[] plaintext = json.getBytes(StandardCharsets.UTF_8);
        
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, spec);
        
        byte[] ciphertext = cipher.doFinal(plaintext);
        
        // Prepend IV to ciphertext
        byte[] output = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, output, 0, iv.length);
        System.arraycopy(ciphertext, 0, output, iv.length, ciphertext.length);
        
        Files.write(credentialsPath, output);
        
        // Set restrictive permissions on Unix systems
        try {
            Set<PosixFilePermission> perms = Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(credentialsPath, perms);
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions
        }
    }
    
    private SecretKey deriveKey() {
        try {
            // Derive key from machine-specific data
            String machineId = getMachineId();
            byte[] salt = getSalt();
            
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(machineId.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive encryption key", e);
        }
    }
    
    private String getMachineId() {
        // Combine various system properties for a semi-unique machine identifier
        StringBuilder sb = new StringBuilder();
        sb.append(System.getProperty("user.name", ""));
        sb.append(System.getProperty("os.name", ""));
        sb.append(System.getProperty("os.arch", ""));
        sb.append(System.getProperty("user.home", ""));
        
        // Add MAC address if available
        try {
            java.net.NetworkInterface ni = java.net.NetworkInterface.getNetworkInterfaces().nextElement();
            byte[] mac = ni.getHardwareAddress();
            if (mac != null) {
                sb.append(Base64.getEncoder().encodeToString(mac));
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return sb.toString();
    }
    
    private byte[] getSalt() {
        // Use a fixed salt derived from app name (stored encrypted data is machine-specific anyway)
        return "dota2assistant.salt".getBytes(StandardCharsets.UTF_8);
    }
    
    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) return map;
        
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return map;
        
        // Simple key-value parser (assumes no nested objects)
        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String k = kv[0].trim().replaceAll("^\"|\"$", "");
                String v = kv[1].trim().replaceAll("^\"|\"$", "");
                map.put(k, v);
            }
        }
        return map;
    }
    
    private String toSimpleJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append("\"").append(escapeJson(entry.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }
    
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

