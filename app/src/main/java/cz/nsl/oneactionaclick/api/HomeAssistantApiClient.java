package cz.nsl.oneactionaclick.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import cz.nsl.oneactionaclick.SettingsActivity;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HomeAssistantApiClient {
    private static final String TAG = "HomeAssistantApiClient";
    
    private Context context;
    private OkHttpClient client;
    private Handler mainHandler;
    private OAuth2Manager oauth2Manager;
    
    // Request logging
    private static List<ApiRequestLog> requestLogs = new ArrayList<>();
    private static final int MAX_LOGS = 50;

    public interface ServiceCallback {
        void onServicesLoaded(Map<String, List<HomeAssistantService>> services);
        void onError(String error);
        default void onDetailedError(String error, String detailedInfo) {
            // Default implementation falls back to the simple error method
            onError(error);
        }
    }

    public interface EntityCallback {
        void onEntitiesLoaded(List<HomeAssistantEntity> entities);
        void onEntitiesByDomainLoaded(Map<String, List<HomeAssistantEntity>> entities);
        void onError(String error);
        default void onDetailedError(String error, String detailedInfo) {
            // Default implementation falls back to the simple error method
            onError(error);
        }
    }

    public enum AuthMethod {
        LONG_LIVED_TOKEN,
        OAUTH2
    }
    
    /**
     * Class to store API request logs
     */
    public static class ApiRequestLog {
        private String timestamp;
        private String method;
        private String url;
        private int statusCode;
        private String responseSize;
        private long durationMs;
        private String errorMessage;
        
        public ApiRequestLog(String method, String url) {
            this.timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            this.method = method;
            this.url = url;
            this.statusCode = 0;
            this.responseSize = "";
            this.durationMs = 0;
            this.errorMessage = "";
        }
        
        public void setResponse(int statusCode, String responseSize, long durationMs) {
            this.statusCode = statusCode;
            this.responseSize = responseSize;
            this.durationMs = durationMs;
        }
        
        public void setError(String errorMessage, long durationMs) {
            this.errorMessage = errorMessage;
            this.durationMs = durationMs;
        }
        
        public String getTimestamp() {
            return timestamp;
        }
        
        public String getMethod() {
            return method;
        }
        
        public String getUrl() {
            return url;
        }
        
        public int getStatusCode() {
            return statusCode;
        }
        
        public String getResponseSize() {
            return responseSize;
        }
        
        public long getDurationMs() {
            return durationMs;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
        
        public boolean isError() {
            return !errorMessage.isEmpty() || (statusCode > 0 && (statusCode < 200 || statusCode >= 300));
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(timestamp).append(" | ");
            sb.append(method).append(" ");
            sb.append(url);
            
            if (isError()) {
                if (statusCode > 0) {
                    sb.append(" | HTTP ").append(statusCode);
                }
                if (!errorMessage.isEmpty()) {
                    sb.append(" | Error: ").append(errorMessage);
                }
            } else if (statusCode > 0) {
                sb.append(" | HTTP ").append(statusCode);
                if (!responseSize.isEmpty()) {
                    sb.append(" | ").append(responseSize);
                }
            }
            
            sb.append(" | ").append(durationMs).append("ms");
            
            return sb.toString();
        }
    }
    
    /**
     * Get all recorded API request logs
     */
    public static List<ApiRequestLog> getRequestLogs() {
        return new ArrayList<>(requestLogs);
    }
    
    /**
     * Clear all recorded API request logs
     */
    public static void clearRequestLogs() {
        requestLogs.clear();
    }
    
    /**
     * Add a log entry for a new API request
     */
    private static ApiRequestLog logRequest(String method, String url) {
        ApiRequestLog log = new ApiRequestLog(method, url);
        
        // Add to the beginning of the list to show most recent first
        requestLogs.add(0, log);
        
        // Trim the log if it exceeds the maximum size
        if (requestLogs.size() > MAX_LOGS) {
            requestLogs.remove(requestLogs.size() - 1);
        }
        
        return log;
    }

    public HomeAssistantApiClient(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.oauth2Manager = new OAuth2Manager(context);
    }

    /**
     * Get the current authentication method
     */
    private AuthMethod getAuthMethod() {
        return SettingsActivity.isUsingOAuth(context) ? AuthMethod.OAUTH2 : AuthMethod.LONG_LIVED_TOKEN;
    }

    /**
     * Add authentication headers to a request based on the current auth method
     */
    private Request.Builder addAuthHeaders(Request.Builder builder) throws IOException {
        AuthMethod authMethod = getAuthMethod();
        
        if (authMethod == AuthMethod.OAUTH2) {
            // Ensure token is valid before making the request
            if (!oauth2Manager.refreshTokenIfNeeded()) {
                throw new IOException("Failed to refresh OAuth2 token");
            }
            
            String accessToken = oauth2Manager.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                throw new IOException("No valid OAuth2 token available");
            }
            
            return builder.addHeader("Authorization", "Bearer " + accessToken);
        } else {
            // Use long-lived token
            String token = SettingsActivity.getHomeAssistantToken(context);
            if (token == null || token.isEmpty()) {
                throw new IOException("No long-lived token available");
            }
            
            return builder.addHeader("Authorization", "Bearer " + token);
        }
    }

    /**
     * Fetch all available services from Home Assistant
     */
    public void fetchServices(final ServiceCallback callback) {
        String baseUrl = SettingsActivity.getHomeAssistantUrl(context);
        String url = baseUrl + "/api/services";
        
        ApiRequestLog requestLog = logRequest("GET", url);
        long startTime = System.currentTimeMillis();

        try {
            Request.Builder requestBuilder = new Request.Builder().url(url);
            requestBuilder = addAuthHeaders(requestBuilder);
            
            final Request request = requestBuilder.build();
            
            Log.d(TAG, "Fetching services from: " + url);
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    final String errorMessage = e.getMessage();
                    Log.e(TAG, "Error fetching services", e);
                    
                    long duration = System.currentTimeMillis() - startTime;
                    requestLog.setError(errorMessage, duration);
                    
                    // Create a detailed error report including the connection parameters
                    final StringBuilder detailedInfo = new StringBuilder();
                    detailedInfo.append("Request URL: ").append(url).append("\n\n");
                    detailedInfo.append("Error Type: ").append(e.getClass().getSimpleName()).append("\n\n");
                    detailedInfo.append("Error Message: ").append(errorMessage).append("\n\n");
                    
                    // Add connection diagnostics
                    detailedInfo.append("Connection Parameters:\n");
                    detailedInfo.append("- Base URL: ").append(baseUrl).append("\n");
                    detailedInfo.append("- Auth Method: ").append(getAuthMethod()).append("\n");
                    
                    if (e instanceof java.net.UnknownHostException) {
                        detailedInfo.append("\nDiagnostic Suggestion: Domain name could not be resolved. Check your Home Assistant URL and ensure your device has internet connectivity.\n");
                    } else if (e instanceof java.net.SocketTimeoutException) {
                        detailedInfo.append("\nDiagnostic Suggestion: Connection timed out. Home Assistant server might be down or unreachable.\n");
                    } else if (e instanceof javax.net.ssl.SSLHandshakeException) {
                        detailedInfo.append("\nDiagnostic Suggestion: SSL certificate error. You might be using a self-signed certificate or there's an SSL configuration issue.\n");
                    }
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onDetailedError(errorMessage, detailedInfo.toString());
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    long duration = System.currentTimeMillis() - startTime;
                    
                    if (!response.isSuccessful()) {
                        final String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        final String errorMessage = "API Error: HTTP " + response.code();
                        
                        Log.e(TAG, "API error: " + response.code() + " - " + errorBody);
                        
                        requestLog.setResponse(response.code(), errorBody.length() + " bytes", duration);
                        
                        // Build detailed error information including headers and response body
                        final StringBuilder detailedInfo = new StringBuilder();
                        detailedInfo.append("Request URL: ").append(url).append("\n\n");
                        detailedInfo.append("Status Code: ").append(response.code()).append("\n\n");
                        detailedInfo.append("Headers:\n");
                        for (String name : response.headers().names()) {
                            detailedInfo.append(name).append(": ").append(response.header(name)).append("\n");
                        }
                        
                        detailedInfo.append("\nResponse Body:\n").append(errorBody);
                        
                        // Add troubleshooting advice based on status code
                        detailedInfo.append("\n\nDiagnostic Suggestion: ");
                        if (response.code() == 401) {
                            detailedInfo.append("Authentication failed. Check your Home Assistant token or OAuth credentials.");
                        } else if (response.code() == 403) {
                            detailedInfo.append("Permission denied. Your account may not have sufficient privileges.");
                        } else if (response.code() == 404) {
                            detailedInfo.append("The services API endpoint was not found. Verify your Home Assistant URL.");
                        } else if (response.code() >= 500) {
                            detailedInfo.append("Server error. Check your Home Assistant server logs for problems.");
                        }
                        
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onDetailedError(errorMessage, detailedInfo.toString());
                            }
                        });
                        return;
                    }

                    try {
                        final String responseBody = response.body().string();
                        final Map<String, List<HomeAssistantService>> services =
                                HomeAssistantService.parseServices(responseBody);

                        requestLog.setResponse(response.code(), responseBody.length() + " bytes", duration);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onServicesLoaded(services);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing services response", e);
                        final String errorMessage = "Error parsing response: " + e.getMessage();
                        final StringBuilder detailedInfo = new StringBuilder();
                        detailedInfo.append("Could not parse Home Assistant services response.\n\n");
                        detailedInfo.append("Error: ").append(e.getMessage()).append("\n\n");
                        
                        requestLog.setError(errorMessage, duration);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onDetailedError(errorMessage, detailedInfo.toString());
                            }
                        });
                    }
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error preparing request", e);
            final String errorMessage = e.getMessage();
            
            long duration = System.currentTimeMillis() - startTime;
            requestLog.setError(errorMessage, duration);
            
            // Create detailed error report for authentication or request preparation errors
            final StringBuilder detailedInfo = new StringBuilder();
            detailedInfo.append("Error preparing API request\n\n");
            detailedInfo.append("URL: ").append(url).append("\n\n");
            detailedInfo.append("Error Type: ").append(e.getClass().getSimpleName()).append("\n\n");
            detailedInfo.append("Error Message: ").append(errorMessage).append("\n\n");
            detailedInfo.append("Likely causes:\n");
            detailedInfo.append("1. Authentication token is missing or invalid\n");
            detailedInfo.append("2. OAuth2 configuration is incorrect\n");
            detailedInfo.append("3. Home Assistant URL is malformed\n");
            
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onDetailedError(errorMessage, detailedInfo.toString());
                }
            });
        }
    }

    /**
     * Fetch all entities from Home Assistant
     */
    public void fetchEntities(final EntityCallback callback) {
        String baseUrl = SettingsActivity.getHomeAssistantUrl(context);
        String url = baseUrl + "/api/states";

        ApiRequestLog requestLog = logRequest("GET", url);
        long startTime = System.currentTimeMillis();
        
        try {
            Request.Builder requestBuilder = new Request.Builder().url(url);
            requestBuilder = addAuthHeaders(requestBuilder);
            
            final Request request = requestBuilder.build();
            
            Log.d(TAG, "Fetching entities from: " + url);
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    final String errorMessage = e.getMessage();
                    Log.e(TAG, "Error fetching entities", e);
                    
                    long duration = System.currentTimeMillis() - startTime;
                    requestLog.setError(errorMessage, duration);
                    
                    // Create a detailed error report including the connection parameters
                    final StringBuilder detailedInfo = new StringBuilder();
                    detailedInfo.append("Request URL: ").append(url).append("\n\n");
                    detailedInfo.append("Error Type: ").append(e.getClass().getSimpleName()).append("\n\n");
                    detailedInfo.append("Error Message: ").append(errorMessage).append("\n\n");
                    
                    // Add connection diagnostics
                    detailedInfo.append("Connection Parameters:\n");
                    detailedInfo.append("- Base URL: ").append(baseUrl).append("\n");
                    detailedInfo.append("- Auth Method: ").append(getAuthMethod()).append("\n");
                    
                    if (e instanceof java.net.UnknownHostException) {
                        detailedInfo.append("\nDiagnostic Suggestion: Domain name could not be resolved. Check your Home Assistant URL and ensure your device has internet connectivity.\n");
                    } else if (e instanceof java.net.SocketTimeoutException) {
                        detailedInfo.append("\nDiagnostic Suggestion: Connection timed out. Home Assistant server might be down or unreachable.\n");
                    } else if (e instanceof javax.net.ssl.SSLHandshakeException) {
                        detailedInfo.append("\nDiagnostic Suggestion: SSL certificate error. You might be using a self-signed certificate or there's an SSL configuration issue.\n");
                    }
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onDetailedError(errorMessage, detailedInfo.toString());
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    long duration = System.currentTimeMillis() - startTime;
                    
                    if (!response.isSuccessful()) {
                        final String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        final String errorMessage = "API Error: HTTP " + response.code();
                        
                        Log.e(TAG, "API error: " + response.code() + " - " + errorBody);
                        
                        requestLog.setResponse(response.code(), errorBody.length() + " bytes", duration);
                        
                        // Build detailed error information including headers and response body
                        final StringBuilder detailedInfo = new StringBuilder();
                        detailedInfo.append("Request URL: ").append(url).append("\n\n");
                        detailedInfo.append("Status Code: ").append(response.code()).append("\n\n");
                        detailedInfo.append("Headers:\n");
                        for (String name : response.headers().names()) {
                            detailedInfo.append(name).append(": ").append(response.header(name)).append("\n");
                        }
                        
                        detailedInfo.append("\nResponse Body:\n").append(errorBody);
                        
                        // Add troubleshooting advice based on status code
                        detailedInfo.append("\n\nDiagnostic Suggestion: ");
                        if (response.code() == 401) {
                            detailedInfo.append("Authentication failed. Check your Home Assistant token or OAuth credentials.");
                        } else if (response.code() == 403) {
                            detailedInfo.append("Permission denied. Your account may not have sufficient privileges.");
                        } else if (response.code() == 404) {
                            detailedInfo.append("The states API endpoint was not found. Verify your Home Assistant URL.");
                        } else if (response.code() >= 500) {
                            detailedInfo.append("Server error. Check your Home Assistant server logs for problems.");
                        }
                        
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onDetailedError(errorMessage, detailedInfo.toString());
                            }
                        });
                        return;
                    }

                    try {
                        final String responseBody = response.body().string();
                        final List<HomeAssistantEntity> entities =
                                HomeAssistantEntity.parseEntitiesList(responseBody);
                        final Map<String, List<HomeAssistantEntity>> entitiesByDomain =
                                HomeAssistantEntity.groupByDomain(entities);

                        requestLog.setResponse(response.code(), responseBody.length() + " bytes", duration);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onEntitiesLoaded(entities);
                                callback.onEntitiesByDomainLoaded(entitiesByDomain);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing entities response", e);
                        final String errorMessage = "Error parsing response: " + e.getMessage();
                        final StringBuilder detailedInfo = new StringBuilder();
                        detailedInfo.append("Could not parse Home Assistant entities response.\n\n");
                        detailedInfo.append("Error: ").append(e.getMessage()).append("\n\n");
                        
                        requestLog.setError(errorMessage, duration);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onDetailedError(errorMessage, detailedInfo.toString());
                            }
                        });
                    }
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error preparing request", e);
            final String errorMessage = e.getMessage();
            
            long duration = System.currentTimeMillis() - startTime;
            requestLog.setError(errorMessage, duration);
            
            // Create detailed error report for authentication or request preparation errors
            final StringBuilder detailedInfo = new StringBuilder();
            detailedInfo.append("Error preparing API request\n\n");
            detailedInfo.append("URL: ").append(url).append("\n\n");
            detailedInfo.append("Error Type: ").append(e.getClass().getSimpleName()).append("\n\n");
            detailedInfo.append("Error Message: ").append(errorMessage).append("\n\n");
            detailedInfo.append("Likely causes:\n");
            detailedInfo.append("1. Authentication token is missing or invalid\n");
            detailedInfo.append("2. OAuth2 configuration is incorrect\n");
            detailedInfo.append("3. Home Assistant URL is malformed\n");
            
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onDetailedError(errorMessage, detailedInfo.toString());
                }
            });
        }
    }
    
    /**
     * Call a service on an entity
     */
    public void callService(String domain, String service, String entityId, final ServiceCallback callback) {
        String baseUrl = SettingsActivity.getHomeAssistantUrl(context);
        String url = baseUrl + "/api/services/" + domain + "/" + service;
        
        // Build JSON request body
        String jsonBody = "{\"entity_id\":\"" + entityId + "\"}";
        
        ApiRequestLog requestLog = logRequest("POST", url);
        long startTime = System.currentTimeMillis();
        
        try {
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse("application/json"), jsonBody);
                    
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(body);
                    
            requestBuilder = addAuthHeaders(requestBuilder);
            
            final Request request = requestBuilder.build();
            
            Log.d(TAG, "Calling service: " + domain + "." + service + " on entity: " + entityId);
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    final String errorMessage = e.getMessage();
                    Log.e(TAG, "Error calling service", e);
                    
                    long duration = System.currentTimeMillis() - startTime;
                    requestLog.setError(errorMessage, duration);
                    
                    // Create a detailed error report including the connection parameters
                    final StringBuilder detailedInfo = new StringBuilder();
                    detailedInfo.append("Request URL: ").append(url).append("\n\n");
                    detailedInfo.append("Error Type: ").append(e.getClass().getSimpleName()).append("\n\n");
                    detailedInfo.append("Error Message: ").append(errorMessage).append("\n\n");
                    
                    // Add call details
                    detailedInfo.append("Service Call Details:\n");
                    detailedInfo.append("- Domain: ").append(domain).append("\n");
                    detailedInfo.append("- Service: ").append(service).append("\n");
                    detailedInfo.append("- Entity ID: ").append(entityId).append("\n\n");
                    
                    // Add connection diagnostics
                    detailedInfo.append("Connection Parameters:\n");
                    detailedInfo.append("- Base URL: ").append(baseUrl).append("\n");
                    detailedInfo.append("- Auth Method: ").append(getAuthMethod()).append("\n");
                    
                    if (e instanceof java.net.UnknownHostException) {
                        detailedInfo.append("\nDiagnostic Suggestion: Domain name could not be resolved. Check your Home Assistant URL and ensure your device has internet connectivity.\n");
                    } else if (e instanceof java.net.SocketTimeoutException) {
                        detailedInfo.append("\nDiagnostic Suggestion: Connection timed out. Home Assistant server might be down or unreachable.\n");
                    } else if (e instanceof javax.net.ssl.SSLHandshakeException) {
                        detailedInfo.append("\nDiagnostic Suggestion: SSL certificate error. You might be using a self-signed certificate or there's an SSL configuration issue.\n");
                    }
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onDetailedError(errorMessage, detailedInfo.toString());
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    long duration = System.currentTimeMillis() - startTime;
                    
                    if (!response.isSuccessful()) {
                        final String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        final String errorMessage = "API Error: HTTP " + response.code();
                        
                        Log.e(TAG, "API error calling service: " + response.code() + " - " + errorBody);
                        
                        requestLog.setResponse(response.code(), errorBody.length() + " bytes", duration);
                        
                        // Build detailed error information including headers and response body
                        final StringBuilder detailedInfo = new StringBuilder();
                        detailedInfo.append("Request URL: ").append(url).append("\n\n");
                        detailedInfo.append("Status Code: ").append(response.code()).append("\n\n");
                        detailedInfo.append("Headers:\n");
                        for (String name : response.headers().names()) {
                            detailedInfo.append(name).append(": ").append(response.header(name)).append("\n");
                        }
                        
                        detailedInfo.append("\nRequest Body:\n").append(jsonBody);
                        detailedInfo.append("\n\nResponse Body:\n").append(errorBody);
                        
                        // Add troubleshooting advice based on status code
                        detailedInfo.append("\n\nDiagnostic Suggestion: ");
                        if (response.code() == 401) {
                            detailedInfo.append("Authentication failed. Check your Home Assistant token or OAuth credentials.");
                        } else if (response.code() == 403) {
                            detailedInfo.append("Permission denied. Your account may not have sufficient privileges.");
                        } else if (response.code() == 404) {
                            detailedInfo.append("The service or entity was not found. Check if the domain, service, and entity ID are correct.");
                        } else if (response.code() >= 500) {
                            detailedInfo.append("Server error. Check your Home Assistant server logs for problems.");
                        }
                        
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onDetailedError(errorMessage, detailedInfo.toString());
                            }
                        });
                        return;
                    }

                    requestLog.setResponse(response.code(), "Success", duration);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onServicesLoaded(null); // Success with no data to return
                        }
                    });
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error preparing request", e);
            final String errorMessage = e.getMessage();
            
            long duration = System.currentTimeMillis() - startTime;
            requestLog.setError(errorMessage, duration);
            
            // Create detailed error report for authentication or request preparation errors
            final StringBuilder detailedInfo = new StringBuilder();
            detailedInfo.append("Error preparing API request\n\n");
            detailedInfo.append("URL: ").append(url).append("\n\n");
            detailedInfo.append("Error Type: ").append(e.getClass().getSimpleName()).append("\n\n");
            detailedInfo.append("Error Message: ").append(errorMessage).append("\n\n");
            detailedInfo.append("Service Call Details:\n");
            detailedInfo.append("- Domain: ").append(domain).append("\n");
            detailedInfo.append("- Service: ").append(service).append("\n");
            detailedInfo.append("- Entity ID: ").append(entityId).append("\n\n");
            detailedInfo.append("Likely causes:\n");
            detailedInfo.append("1. Authentication token is missing or invalid\n");
            detailedInfo.append("2. OAuth2 configuration is incorrect\n");
            detailedInfo.append("3. Home Assistant URL is malformed\n");
            
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onDetailedError(errorMessage, detailedInfo.toString());
                }
            });
        }
    }
}