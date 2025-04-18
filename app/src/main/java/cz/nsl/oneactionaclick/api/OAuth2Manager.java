package cz.nsl.oneactionaclick.api;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OAuth2Manager {
    private static final String TAG = "OAuth2Manager";
    
    // SharedPreferences keys
    private static final String PREFS_NAME = "cz.nsl.oneactionaclick.AppSettings";
    private static final String PREF_ACCESS_TOKEN = "oauth2_access_token";
    private static final String PREF_REFRESH_TOKEN = "oauth2_refresh_token";
    private static final String PREF_TOKEN_EXPIRY = "oauth2_token_expiry";
    private static final String PREF_CLIENT_ID = "oauth2_client_id";
    private static final String PREF_CLIENT_SECRET = "oauth2_client_secret";
    
    // OAuth2 Constants
    public static final String REDIRECT_URI = "homeassistant://auth-callback";
    
    private Context context;
    private OkHttpClient client;
    
    public OAuth2Manager(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Generate the authorization URL for Home Assistant OAuth2
     */
    public String getAuthorizationUrl(String baseUrl, String clientId, String redirectUri) {
        // Save client ID for later token requests
        saveClientId(clientId);
        
        // Build the authorization URL
        Uri.Builder builder = Uri.parse(baseUrl + "/auth/authorize").buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("response_type", "code");
        
        return builder.build().toString();
    }
    
    /**
     * Open the authorization URL in a browser
     */
    public void startAuthorizationRequest(String authorizationUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
    
    /**
     * Exchange authorization code for tokens
     */
    public boolean exchangeCodeForTokens(String baseUrl, String clientId, String clientSecret, String code) {
        // Save client credentials
        saveClientId(clientId);
        saveClientSecret(clientSecret);
        
        String redirectUri = REDIRECT_URI;
        
        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", clientId)
                .add("redirect_uri", redirectUri)
                .build();
        
        if (clientSecret != null && !clientSecret.isEmpty()) {
            FormBody.Builder newFormBodyBuilder = new FormBody.Builder();
            for (int i = 0; i < ((FormBody) formBody).size(); i++) {
                newFormBodyBuilder.addEncoded(((FormBody) formBody).name(i), ((FormBody) formBody).value(i));
            }
            newFormBodyBuilder.add("client_secret", clientSecret);
            formBody = newFormBodyBuilder.build();
        }
        
        Request request = new Request.Builder()
                .url(baseUrl + "/auth/token")
                .post(formBody)
                .build();
        
        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                parseAndSaveTokenResponse(responseBody);
                return true;
            } else {
                Log.e(TAG, "Token exchange failed: " + (response.body() != null ? response.body().string() : "Unknown error"));
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "Token exchange error", e);
            return false;
        }
    }
    
    /**
     * Refresh access token if expired
     */
    public boolean refreshTokenIfNeeded() {
        // Check if token is expired or will expire soon (within 5 minutes)
        long expiryTime = getTokenExpiryTime();
        long currentTime = System.currentTimeMillis();
        
        if (expiryTime - currentTime > 5 * 60 * 1000) {
            // Token is still valid
            return true;
        }
        
        String refreshToken = getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            return false;
        }
        
        String baseUrl = getBaseUrl();
        String clientId = getClientId();
        
        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .build();
        
        Request request = new Request.Builder()
                .url(baseUrl + "/auth/token")
                .post(formBody)
                .build();
        
        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                parseAndSaveTokenResponse(responseBody);
                return true;
            } else {
                Log.e(TAG, "Token refresh failed: " + (response.body() != null ? response.body().string() : "Unknown error"));
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "Token refresh error", e);
            return false;
        }
    }
    
    /**
     * Parse and save token response
     */
    private void parseAndSaveTokenResponse(String responseBody) throws IOException {
        try {
            JSONObject json = new JSONObject(responseBody);
            String accessToken = json.getString("access_token");
            String refreshToken = json.optString("refresh_token", null);
            long expiresIn = json.getLong("expires_in");
            
            SharedPreferences.Editor editor = getSharedPreferences().edit();
            editor.putString(PREF_ACCESS_TOKEN, accessToken);
            
            // Save refresh token only if it's included in the response
            // (token refresh responses might not include a new refresh token)
            if (refreshToken != null && !refreshToken.isEmpty()) {
                editor.putString(PREF_REFRESH_TOKEN, refreshToken);
            }
            
            // Calculate token expiry time
            long expiryTime = System.currentTimeMillis() + (expiresIn * 1000);
            editor.putLong(PREF_TOKEN_EXPIRY, expiryTime);
            
            editor.apply();
        } catch (JSONException e) {
            throw new IOException("Failed to parse token response", e);
        }
    }
    
    /**
     * Get current access token
     */
    public String getAccessToken() {
        return getSharedPreferences().getString(PREF_ACCESS_TOKEN, null);
    }
    
    /**
     * Get refresh token
     */
    private String getRefreshToken() {
        return getSharedPreferences().getString(PREF_REFRESH_TOKEN, null);
    }
    
    /**
     * Get token expiry time
     */
    private long getTokenExpiryTime() {
        return getSharedPreferences().getLong(PREF_TOKEN_EXPIRY, 0);
    }
    
    /**
     * Get client ID
     */
    private String getClientId() {
        return getSharedPreferences().getString(PREF_CLIENT_ID, null);
    }
    
    /**
     * Save client ID
     */
    private void saveClientId(String clientId) {
        getSharedPreferences().edit().putString(PREF_CLIENT_ID, clientId).apply();
    }
    
    /**
     * Get client secret
     */
    private String getClientSecret() {
        return getSharedPreferences().getString(PREF_CLIENT_SECRET, null);
    }
    
    /**
     * Save client secret
     */
    public void saveClientSecret(String clientSecret) {
        getSharedPreferences().edit().putString(PREF_CLIENT_SECRET, clientSecret).apply();
    }
    
    /**
     * Check if we have valid tokens
     */
    public boolean hasValidTokens() {
        String accessToken = getAccessToken();
        return accessToken != null && !accessToken.isEmpty() && !isTokenExpired();
    }
    
    /**
     * Check if token is expired
     */
    public boolean isTokenExpired() {
        long expiryTime = getTokenExpiryTime();
        return System.currentTimeMillis() >= expiryTime;
    }
    
    /**
     * Clear all auth tokens
     */
    public void clearTokens() {
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.remove(PREF_ACCESS_TOKEN);
        editor.remove(PREF_REFRESH_TOKEN);
        editor.remove(PREF_TOKEN_EXPIRY);
        editor.apply();
    }
    
    /**
     * Get base URL from settings
     */
    private String getBaseUrl() {
        return getSharedPreferences().getString("home_assistant_url", null);
    }
    
    /**
     * Get shared preferences
     */
    private SharedPreferences getSharedPreferences() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}