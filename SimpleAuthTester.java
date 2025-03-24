import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Simple standalone authentication tester for Steam OpenID.
 * This uses only standard Java libraries and Swing for the UI.
 * 
 * To compile:
 * javac SimpleAuthTester.java
 * 
 * To run:
 * java SimpleAuthTester
 */
public class SimpleAuthTester extends JFrame {
    
    private JLabel statusLabel;
    private JTextArea logArea;
    private JButton loginButton;
    private JButton logoutButton;
    private JLabel userInfoLabel;
    private HttpServer server;
    private CompletableFuture<String> callbackFuture;
    private boolean isAuthInProgress = false;
    
    public SimpleAuthTester() {
        setupUI();
    }
    
    private void setupUI() {
        // Setup frame
        setTitle("Steam Auth Simple Test");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Create components
        statusLabel = new JLabel("Not logged in");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        userInfoLabel = new JLabel("No user information");
        
        loginButton = new JButton("Login with Steam");
        logoutButton = new JButton("Logout");
        JButton checkSessionButton = new JButton("Check Saved Session");
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(loginButton);
        buttonPanel.add(logoutButton);
        buttonPanel.add(checkSessionButton);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(userInfoLabel, BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Add components to frame
        add(statusLabel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Add action listeners
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startLogin();
            }
        });
        
        logoutButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                log("Logout clicked");
                statusLabel.setText("Logged out");
                userInfoLabel.setText("No user information");
            }
        });
        
        checkSessionButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                log("Check session clicked");
            }
        });
        
        // Center on screen
        setLocationRelativeTo(null);
    }
    
    private void startLogin() {
        if (isAuthInProgress) {
            log("Authentication already in progress");
            return;
        }
        
        isAuthInProgress = true;
        log("Starting authentication...");
        statusLabel.setText("Authentication in progress...");
        
        // Start the server to handle callbacks
        try {
            startCallbackServer();
            
            // Build OpenID authentication URL
            String loginUrl = "https://steamcommunity.com/openid/login" +
                    "?openid.ns=http://specs.openid.net/auth/2.0" +
                    "&openid.mode=checkid_setup" +
                    "&openid.return_to=http://localhost:8080/login/oauth2/code/steam" +
                    "&openid.realm=http://localhost:8080" +
                    "&openid.identity=http://specs.openid.net/auth/2.0/identifier_select" +
                    "&openid.claimed_id=http://specs.openid.net/auth/2.0/identifier_select";
            
            log("Login URL: " + loginUrl);
            
            // Open browser
            Desktop.getDesktop().browse(new URI(loginUrl));
            
            // Handle the callback asynchronously
            callbackFuture.thenAcceptAsync(callbackUrl -> {
                log("Received callback URL: " + callbackUrl);
                
                // Extract Steam ID from the URL (simplified)
                String steamId = parseSteamId(callbackUrl);
                if (steamId != null) {
                    log("Authentication successful! Steam ID: " + steamId);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Logged in");
                        userInfoLabel.setText("User Steam ID: " + steamId);
                    });
                } else {
                    log("Could not extract Steam ID from callback");
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Authentication failed");
                    });
                }
                
                // Stop the server
                stopCallbackServer();
                isAuthInProgress = false;
            });
        } catch (Exception e) {
            log("Error during authentication: " + e.getMessage());
            stopCallbackServer();
            isAuthInProgress = false;
        }
    }
    
    private String parseSteamId(String url) {
        // Look for the openid.claimed_id parameter which contains the Steam ID
        if (url.contains("openid.claimed_id=")) {
            try {
                // Extract the claimed_id part
                int start = url.indexOf("openid.claimed_id=") + "openid.claimed_id=".length();
                int end = url.indexOf("&", start);
                if (end == -1) end = url.length();
                
                String claimedId = url.substring(start, end);
                
                // URL decode the value
                claimedId = java.net.URLDecoder.decode(claimedId, "UTF-8");
                
                // Format: https://steamcommunity.com/openid/id/76561198065100703
                if (claimedId.contains("/id/")) {
                    return claimedId.substring(claimedId.lastIndexOf("/") + 1);
                }
                
                log("Claimed ID: " + claimedId);
            } catch (Exception e) {
                log("Error parsing Steam ID: " + e.getMessage());
            }
        }
        
        log("Could not find openid.claimed_id in URL");
        return null;
    }
    
    private void startCallbackServer() throws IOException {
        callbackFuture = new CompletableFuture<>();
        server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/login/oauth2/code/steam", new CallbackHandler());
        server.setExecutor(null); // Use the default executor
        server.start();
        log("Callback server started on port 8080");
    }
    
    private void stopCallbackServer() {
        if (server != null) {
            server.stop(0);
            server = null;
            log("Callback server stopped");
        }
    }
    
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
        });
        System.out.println(message);
    }
    
    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Get the full URL including query parameters
                String callbackUrl = "http://localhost:8080" + exchange.getRequestURI().toString();
                
                log("Received callback: " + callbackUrl);
                
                // Complete the future with the callback URL
                callbackFuture.complete(callbackUrl);
                
                // Send a simple HTML response
                String response = 
                        "<html><body><h1>Authentication Successful</h1>" +
                        "<p>You have successfully authenticated with Steam.</p>" +
                        "<p>You can close this window and return to the application.</p>" +
                        "<script>setTimeout(function() { window.close(); }, 2000);</script>" +
                        "</body></html>";
                
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                
                // Delay server shutdown to ensure the response is fully sent
                Thread.sleep(500);
            } catch (Exception e) {
                log("Error in callback handler: " + e.getMessage());
                String errorResponse = "<html><body><h1>Error</h1><p>" + e.getMessage() + "</p></body></html>";
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(500, errorResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes());
                }
            }
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimpleAuthTester tester = new SimpleAuthTester();
            tester.setVisible(true);
        });
    }
}