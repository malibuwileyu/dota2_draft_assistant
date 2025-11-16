package com.dota2assistant.data.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Represents an authentication session for a user.
 */
public class Session implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private long id;
    private long accountId;
    private String sessionToken;
    private Date createdTime;
    private Date expireTime;
    private Date lastActive;
    private String ipAddress;
    private String userAgent;
    private String deviceInfo;
    private boolean isActive;
    
    // Default constructor
    public Session() {
    }
    
    // Getters and setters
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public long getAccountId() {
        return accountId;
    }
    
    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }
    
    public String getSessionToken() {
        return sessionToken;
    }
    
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }
    
    public Date getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }
    
    public Date getExpireTime() {
        return expireTime;
    }
    
    public void setExpireTime(Date expireTime) {
        this.expireTime = expireTime;
    }
    
    public Date getLastActive() {
        return lastActive;
    }
    
    public void setLastActive(Date lastActive) {
        this.lastActive = lastActive;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public String getDeviceInfo() {
        return deviceInfo;
    }
    
    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    /**
     * Check if this session has expired.
     *
     * @return true if the session has expired, false otherwise
     */
    public boolean isExpired() {
        return expireTime != null && expireTime.before(new Date());
    }
    
    /**
     * Check if this session is valid (active and not expired).
     *
     * @return true if the session is valid, false otherwise
     */
    public boolean isValid() {
        return isActive && !isExpired();
    }
}