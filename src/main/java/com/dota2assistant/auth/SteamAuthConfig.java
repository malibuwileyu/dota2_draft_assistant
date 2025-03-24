package com.dota2assistant.auth;

import com.dota2assistant.util.PropertyLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Configuration for Steam OpenID authentication.
 * Steam uses OpenID 2.0 which is different from OAuth2, but we can adapt it.
 */
@Configuration
public class SteamAuthConfig {

    private static final Logger logger = LoggerFactory.getLogger(SteamAuthConfig.class);
    private final PropertyLoader propertyLoader;

    public SteamAuthConfig(PropertyLoader propertyLoader) {
        this.propertyLoader = propertyLoader;
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(steamClientRegistration());
    }
    
    /**
     * Generate the Steam OpenID authentication URL
     * 
     * @param returnUrl The URL to return to after authentication
     * @return The authentication URL to redirect the user to
     */
    public static String generateSteamOpenIdUrl(String returnUrl) {
        String openIdUrl = "https://steamcommunity.com/openid/login";
        
        StringBuilder builder = new StringBuilder(openIdUrl);
        builder.append("?openid.ns=http://specs.openid.net/auth/2.0");
        builder.append("&openid.mode=checkid_setup");
        builder.append("&openid.return_to=").append(returnUrl);
        builder.append("&openid.realm=").append(returnUrl);
        builder.append("&openid.identity=http://specs.openid.net/auth/2.0/identifier_select");
        builder.append("&openid.claimed_id=http://specs.openid.net/auth/2.0/identifier_select");
        
        return builder.toString();
    }

    private ClientRegistration steamClientRegistration() {
        String apiKey = propertyLoader.getProperty("steam.api.key", "");
        String returnUrl = propertyLoader.getProperty("steam.auth.return_url", "http://localhost:8080/login/oauth2/code/steam");
        
        if (apiKey.isEmpty()) {
            logger.warn("Steam API key is not set. Authentication will not work properly.");
        }
        
        return ClientRegistration.withRegistrationId("steam")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId(apiKey)
                .redirectUri(returnUrl)
                .authorizationUri("https://steamcommunity.com/openid/login")
                .tokenUri("https://api.steampowered.com/ISteamUserAuth/GetTokenForApp/v1/")
                .userInfoUri("https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/")
                .userNameAttributeName("steamid")
                .clientName("Steam")
                .build();
    }
}