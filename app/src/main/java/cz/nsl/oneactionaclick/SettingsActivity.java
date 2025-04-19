package cz.nsl.oneactionaclick;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cz.nsl.oneactionaclick.api.HomeAssistantApiClient;
import cz.nsl.oneactionaclick.api.HomeAssistantDiscoveryManager;
import cz.nsl.oneactionaclick.api.HomeAssistantEntity;
import cz.nsl.oneactionaclick.api.OAuth2Manager;

public class SettingsActivity extends Activity {

    private EditText editHomeAssistantUrl;
    private EditText editHomeAssistantToken;
    private EditText editOAuthClientId;
    private EditText editOAuthClientSecret;
    private RadioGroup radioGroupAuthMethod;
    private RadioButton radioToken;
    private RadioButton radioOAuth2;
    private LinearLayout layoutTokenAuth;
    private LinearLayout layoutOAuthAuth;
    private Button buttonStartOAuth;
    private Button buttonSaveSettings;
    private Button buttonDiscover;
    private TextView textDiscoveryStatus;

    // Discovery-related fields
    private HomeAssistantDiscoveryManager discoveryManager;
    private List<HomeAssistantDiscoveryManager.HomeAssistantInstance> discoveredInstances;
    private ArrayAdapter<HomeAssistantDiscoveryManager.HomeAssistantInstance> discoveryAdapter;
    private ListView listDiscoveredInstances;
    private ProgressBar progressDiscovery;
    
    // Test connection related fields
    private Button buttonTestConnection;
    private TextView textConnectionStatus;
    private TextView textConnectionDetails;
    private ScrollView scrollConnectionDetails;
    private HomeAssistantApiClient apiClient;
    private ProgressBar progressConnection;

    // Shared preferences file name
    private static final String PREFS_NAME = "cz.nsl.oneactionaclick.AppSettings";

    // Keys for storing app settings in SharedPreferences
    private static final String PREF_HOME_ASSISTANT_URL = "home_assistant_url";
    private static final String PREF_HOME_ASSISTANT_TOKEN = "home_assistant_token";
    private static final String PREF_AUTH_METHOD = "auth_method";
    private static final String PREF_OAUTH_CLIENT_ID = "oauth_client_id";
    private static final String PREF_OAUTH_CLIENT_SECRET = "oauth_client_secret";

    // Auth method constants
    private static final String AUTH_METHOD_TOKEN = "token";
    private static final String AUTH_METHOD_OAUTH = "oauth";

    // Default values
    private static final String DEFAULT_URL = "http://192.168.1.100:8123";
    private static final String DEFAULT_TOKEN = "";
    private static final String DEFAULT_AUTH_METHOD = AUTH_METHOD_TOKEN;

    // OAuth2 manager
    private OAuth2Manager oauth2Manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize discovery manager
        discoveryManager = new HomeAssistantDiscoveryManager(this);
        discoveredInstances = new ArrayList<>();

        // Initialize OAuth2 manager
        oauth2Manager = new OAuth2Manager(this);

        // Find views
        editHomeAssistantUrl = findViewById(R.id.edit_home_assistant_url);
        editHomeAssistantToken = findViewById(R.id.edit_home_assistant_token);
        editOAuthClientId = findViewById(R.id.edit_oauth_client_id);
        editOAuthClientSecret = findViewById(R.id.edit_oauth_client_secret);

        radioGroupAuthMethod = findViewById(R.id.radio_group_auth_method);
        radioToken = findViewById(R.id.radio_token);
        radioOAuth2 = findViewById(R.id.radio_oauth2);

        layoutTokenAuth = findViewById(R.id.layout_token_auth);
        layoutOAuthAuth = findViewById(R.id.layout_oauth_auth);

        buttonStartOAuth = findViewById(R.id.button_start_oauth);
        buttonSaveSettings = findViewById(R.id.button_save_settings);
        buttonDiscover = findViewById(R.id.button_discover);

        textDiscoveryStatus = findViewById(R.id.text_discovery_status);
        listDiscoveredInstances = findViewById(R.id.list_discovered_instances);
        progressDiscovery = findViewById(R.id.progress_discovery);
        
        // Test connection UI elements
        buttonTestConnection = findViewById(R.id.button_test_connection);
        textConnectionStatus = findViewById(R.id.text_connection_status);
        textConnectionDetails = findViewById(R.id.text_connection_details);
        scrollConnectionDetails = findViewById(R.id.scroll_connection_details);
        
        // Initialize API client
        apiClient = new HomeAssistantApiClient(this);

        // Setup discovery list adapter
        discoveryAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_list_item_1, discoveredInstances);
        listDiscoveredInstances.setAdapter(discoveryAdapter);

        // Add click listener for discovery list
        listDiscoveredInstances.setOnItemClickListener((parent, view, position, id) -> {
            HomeAssistantDiscoveryManager.HomeAssistantInstance instance = discoveredInstances.get(position);
            editHomeAssistantUrl.setText(instance.getUrl());
            hideDiscoveryUi();
        });

        // Load current settings
        String currentUrl = getHomeAssistantUrl(this);
        String currentToken = getHomeAssistantToken(this);
        String currentAuthMethod = getAuthMethod(this);
        String currentClientId = getOAuthClientId(this);
        String currentClientSecret = getOAuthClientSecret(this);

        // Set current values
        editHomeAssistantUrl.setText(currentUrl);
        editHomeAssistantToken.setText(currentToken);
        editOAuthClientId.setText(currentClientId);
        editOAuthClientSecret.setText(currentClientSecret);

        // Set auth method
        if (AUTH_METHOD_OAUTH.equals(currentAuthMethod)) {
            radioOAuth2.setChecked(true);
            layoutTokenAuth.setVisibility(View.GONE);
            layoutOAuthAuth.setVisibility(View.VISIBLE);
        } else {
            radioToken.setChecked(true);
            layoutTokenAuth.setVisibility(View.VISIBLE);
            layoutOAuthAuth.setVisibility(View.GONE);
        }

        // Radio group change listener
        radioGroupAuthMethod.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radio_token) {
                    layoutTokenAuth.setVisibility(View.VISIBLE);
                    layoutOAuthAuth.setVisibility(View.GONE);
                } else if (checkedId == R.id.radio_oauth2) {
                    layoutTokenAuth.setVisibility(View.GONE);
                    layoutOAuthAuth.setVisibility(View.VISIBLE);
                }
            }
        });

        // OAuth2 button click handler
        buttonStartOAuth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startOAuth2Flow();
            }
        });

        // Save button click handler
        buttonSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });

        // Discovery button click handler
        buttonDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDiscovery();
            }
        });

        // Test connection button click handler
        buttonTestConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testHomeAssistantConnection();
            }
        });
        
        // QR code scan button click handler
        Button buttonScanToken = findViewById(R.id.button_scan_token);
        buttonScanToken.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startQRCodeScan();
            }
        });
    }
    
    /**
     * Start the Home Assistant discovery process
     */
    private void startDiscovery() {
        // Show progress indicator and status
        showDiscoveryProgress();
        textDiscoveryStatus.setText(R.string.discovery_scanning);
        textDiscoveryStatus.setVisibility(View.VISIBLE);
        
        // Clear previous results
        discoveredInstances.clear();
        discoveryAdapter.notifyDataSetChanged();
        
        // Start the discovery process
        discoveryManager.startDiscovery(new HomeAssistantDiscoveryManager.HomeAssistantDiscoveryListener() {
            @Override
            public void onDiscoveryStarted() {
                runOnUiThread(() -> {
                    textDiscoveryStatus.setText(R.string.discovery_scanning);
                });
            }

            @Override
            public void onInstanceFound(HomeAssistantDiscoveryManager.HomeAssistantInstance instance) {
                runOnUiThread(() -> {
                    discoveredInstances.add(instance);
                    discoveryAdapter.notifyDataSetChanged();
                    listDiscoveredInstances.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onDiscoveryFinished(List<HomeAssistantDiscoveryManager.HomeAssistantInstance> instances) {
                runOnUiThread(() -> {
                    hideDiscoveryProgress();
                    
                    if (instances.isEmpty()) {
                        textDiscoveryStatus.setText(R.string.discovery_none_found);
                    } else {
                        textDiscoveryStatus.setText(getString(R.string.discovery_found, instances.size()));
                    }
                });
            }

            @Override
            public void onDiscoveryFailed(String errorMessage) {
                runOnUiThread(() -> {
                    hideDiscoveryProgress();
                    textDiscoveryStatus.setText(getString(R.string.discovery_error, errorMessage));
                });
            }
        });
    }
    
    /**
     * Show the discovery progress indicator
     */
    private void showDiscoveryProgress() {
        progressDiscovery.setVisibility(View.VISIBLE);
        buttonDiscover.setEnabled(false);
    }
    
    /**
     * Hide the discovery progress indicator
     */
    private void hideDiscoveryProgress() {
        progressDiscovery.setVisibility(View.GONE);
        buttonDiscover.setEnabled(true);
    }
    
    /**
     * Hide all discovery-related UI elements
     */
    private void hideDiscoveryUi() {
        progressDiscovery.setVisibility(View.GONE);
        listDiscoveredInstances.setVisibility(View.GONE);
        textDiscoveryStatus.setVisibility(View.GONE);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discoveryManager != null) {
            discoveryManager.stopDiscovery();
        }
    }

    private void startOAuth2Flow() {
        String url = editHomeAssistantUrl.getText().toString().trim();
        String clientId = editOAuthClientId.getText().toString().trim();
        String clientSecret = editOAuthClientSecret.getText().toString().trim();

        // Validate URL and client ID
        if (url.isEmpty()) {
            editHomeAssistantUrl.setError("URL is required");
            return;
        }

        if (clientId.isEmpty()) {
            editOAuthClientId.setError("Client ID is required");
            return;
        }

        // Remove trailing slash if present
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // Save the client ID and secret for later use
        SharedPreferences.Editor prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        prefs.putString(PREF_HOME_ASSISTANT_URL, url);
        prefs.putString(PREF_OAUTH_CLIENT_ID, clientId);
        prefs.putString(PREF_OAUTH_CLIENT_SECRET, clientSecret);
        prefs.apply();

        // Generate the OAuth2 authorization URL and open it
        String authUrl = oauth2Manager.getAuthorizationUrl(url, clientId, OAuth2Manager.REDIRECT_URI);
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
        startActivity(browserIntent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Handle the OAuth2 callback
        Uri uri = intent.getData();
        if (uri != null && uri.toString().startsWith(OAuth2Manager.REDIRECT_URI)) {
            handleOAuth2Callback(uri);
        }
    }

    private void handleOAuth2Callback(Uri uri) {
        // Extract the authorization code from the URI
        String code = uri.getQueryParameter("code");
        if (code != null) {
            // Get the URL, client ID, and client secret from SharedPreferences
            String url = getHomeAssistantUrl(this);
            String clientId = getOAuthClientId(this);
            String clientSecret = getOAuthClientSecret(this);

            // Exchange the code for tokens
            boolean success = oauth2Manager.exchangeCodeForTokens(url, clientId, clientSecret, code);

            if (success) {
                Toast.makeText(this, "OAuth2 authentication successful", Toast.LENGTH_SHORT).show();

                // Save OAuth2 as the current auth method
                SharedPreferences.Editor prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                prefs.putString(PREF_AUTH_METHOD, AUTH_METHOD_OAUTH);
                prefs.apply();

                // Close the activity
                finish();
            } else {
                Toast.makeText(this, "OAuth2 authentication failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveSettings() {
        // Get the entered values
        String url = editHomeAssistantUrl.getText().toString().trim();
        String authMethod = radioToken.isChecked() ? AUTH_METHOD_TOKEN : AUTH_METHOD_OAUTH;

        // Validate URL
        if (url.isEmpty()) {
            editHomeAssistantUrl.setError("URL is required");
            return;
        }

        // Remove trailing slash if present
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // Check if URL contains api/services
        if (url.contains("/api/services")) {
            url = url.substring(0, url.indexOf("/api/services"));
        }

        // Save common settings
        SharedPreferences.Editor prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        prefs.putString(PREF_HOME_ASSISTANT_URL, url);
        prefs.putString(PREF_AUTH_METHOD, authMethod);

        // Save token or OAuth2 settings based on the selected auth method
        if (AUTH_METHOD_TOKEN.equals(authMethod)) {
            String token = editHomeAssistantToken.getText().toString().trim();

            // Validate token
            if (token.isEmpty()) {
                editHomeAssistantToken.setError("Token is required");
                return;
            }

            prefs.putString(PREF_HOME_ASSISTANT_TOKEN, token);
        } else {
            String clientId = editOAuthClientId.getText().toString().trim();
            String clientSecret = editOAuthClientSecret.getText().toString().trim();

            // Validate client ID
            if (clientId.isEmpty()) {
                editOAuthClientId.setError("Client ID is required");
                return;
            }

            prefs.putString(PREF_OAUTH_CLIENT_ID, clientId);
            prefs.putString(PREF_OAUTH_CLIENT_SECRET, clientSecret);
        }

        prefs.apply();

        // Show success message
        Toast.makeText(SettingsActivity.this, R.string.settings_saved, Toast.LENGTH_SHORT).show();

        // Close activity
        finish();
    }

    /**
     * Test the connection to Home Assistant and show detailed information
     */
    private void testHomeAssistantConnection() {
        // Get the URL from the input field (might not be saved yet)
        String url = editHomeAssistantUrl.getText().toString().trim();
        if (url.isEmpty()) {
            editHomeAssistantUrl.setError("URL is required");
            return;
        }

        // Remove trailing slash if present
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // Show status message
        textConnectionStatus.setVisibility(View.VISIBLE);
        textConnectionStatus.setText("Testing connection...");
        textConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        scrollConnectionDetails.setVisibility(View.GONE);
        
        // Create a new API client with current fields (not saved ones)
        final String finalUrl = url;
        final String testUrl = url + "/api/states";
        final StringBuilder requestDetails = new StringBuilder();
        
        requestDetails.append("Request URL: ").append(testUrl).append("\n\n");
        requestDetails.append("Headers:\n");

        // Store the current auth mode and token for use in the test
        final boolean isOAuth = radioOAuth2.isChecked();
        final String token = editHomeAssistantToken.getText().toString().trim();
        
        if (isOAuth) {
            requestDetails.append("Authorization: Bearer <OAuth Token>\n");
            requestDetails.append("Content-Type: application/json\n\n");
        } else {
            if (token.isEmpty()) {
                editHomeAssistantToken.setError("Token is required");
                textConnectionStatus.setText("Error: Token is required");
                textConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                return;
            }
            requestDetails.append("Authorization: Bearer ").append(token.substring(0, 3)).append("...").append("\n");
            requestDetails.append("Content-Type: application/json\n\n");
        }
        
        // Create a temporary SharedPreferences to test with the current field values
        SharedPreferences.Editor tempPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        tempPrefs.putString(PREF_HOME_ASSISTANT_URL, finalUrl);
        tempPrefs.putString(PREF_AUTH_METHOD, isOAuth ? AUTH_METHOD_OAUTH : AUTH_METHOD_TOKEN);
        if (!isOAuth) {
            tempPrefs.putString(PREF_HOME_ASSISTANT_TOKEN, token);
        }
        tempPrefs.apply();
        
        // Create a test API client
        HomeAssistantApiClient testClient = new HomeAssistantApiClient(this);
        
        // Test fetching entities
        testClient.fetchEntities(new HomeAssistantApiClient.EntityCallback() {
            @Override
            public void onEntitiesLoaded(List<HomeAssistantEntity> entities) {
                runOnUiThread(() -> {
                    textConnectionStatus.setText("Connection Successful: Found " + entities.size() + " entities");
                    textConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    
                    // Show the detailed information
                    requestDetails.append("Response: Success\n");
                    requestDetails.append("Found ").append(entities.size()).append(" entities");
                    textConnectionDetails.setText(requestDetails.toString());
                    scrollConnectionDetails.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onEntitiesByDomainLoaded(Map<String, List<HomeAssistantEntity>> entitiesByDomain) {
                // Not needed for this test
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    textConnectionStatus.setText("Connection Failed");
                    textConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    
                    // Show detailed error information
                    requestDetails.append("Error: ").append(error).append("\n\n");
                    requestDetails.append("Troubleshooting Tips:\n");
                    requestDetails.append("1. Check the URL is correct\n");
                    requestDetails.append("2. Verify the token is valid\n");
                    requestDetails.append("3. Make sure Home Assistant is running\n");
                    requestDetails.append("4. Check network connectivity\n");
                    requestDetails.append("5. Verify API access is enabled in Home Assistant");
                    
                    textConnectionDetails.setText(requestDetails.toString());
                    scrollConnectionDetails.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    /**
     * Start the QR code scanning process
     */
    private void startQRCodeScan() {
        // Initialize the IntentIntegrator
        IntentIntegrator integrator = new IntentIntegrator(this);
        
        // Configure the scanner
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scan a QR code containing your Home Assistant token");
        integrator.setCameraId(0);  // Use default camera
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.setOrientationLocked(false);
        
        // Start the scanning activity
        integrator.initiateScan();
    }
    
    /**
     * Handle the result from the QR code scanner
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Get the scan result from the QR code scanner
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        
        if (result != null) {
            if (result.getContents() != null) {
                // Get the scanned content
                String scannedText = result.getContents();
                
                // Process the scanned token
                processScannedToken(scannedText);
            } else {
                // User cancelled the scan
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
            }
        } else {
            // This is not from our QR scanner, pass to parent
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
    
    /**
     * Process the token obtained from QR code scanning
     * @param scannedText The text content from the QR code
     */
    private void processScannedToken(String scannedText) {
        // Check if the scanned text is not empty
        if (scannedText == null || scannedText.isEmpty()) {
            Toast.makeText(this, "Empty QR code detected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Try to clean up the token
        String token = scannedText.trim();
        
        // If it's a URL or contains other data, try to extract just the token
        if (token.startsWith("http") || token.contains("token=")) {
            // Try to extract token from URL query parameters
            try {
                Uri uri = Uri.parse(token);
                String extractedToken = uri.getQueryParameter("token");
                if (extractedToken != null && !extractedToken.isEmpty()) {
                    token = extractedToken;
                }
            } catch (Exception e) {
                // If parsing fails, use the original text
            }
        }
        
        // Remove any surrounding quotes if present
        if ((token.startsWith("\"") && token.endsWith("\"")) || 
            (token.startsWith("'") && token.endsWith("'"))) {
            token = token.substring(1, token.length() - 1);
        }
        
        // Set the token in the input field
        editHomeAssistantToken.setText(token);
        
        // Show success message
        Toast.makeText(this, "Token scanned successfully", Toast.LENGTH_SHORT).show();
        
        // Switch to token authentication if not already selected
        if (!radioToken.isChecked()) {
            radioToken.setChecked(true);
        }
    }

    // Static methods to retrieve app settings
    public static String getHomeAssistantUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_HOME_ASSISTANT_URL, DEFAULT_URL);
    }

    public static String getHomeAssistantToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_HOME_ASSISTANT_TOKEN, DEFAULT_TOKEN);
    }

    public static String getAuthMethod(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_AUTH_METHOD, DEFAULT_AUTH_METHOD);
    }

    public static String getOAuthClientId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_OAUTH_CLIENT_ID, "");
    }

    public static String getOAuthClientSecret(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_OAUTH_CLIENT_SECRET, "");
    }

    public static boolean isUsingOAuth(Context context) {
        return AUTH_METHOD_OAUTH.equals(getAuthMethod(context));
    }
}