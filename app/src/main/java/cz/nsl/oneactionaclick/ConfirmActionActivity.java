package cz.nsl.oneactionaclick;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ConfirmActionActivity extends Activity {
    
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private OkHttpClient client;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private Handler mainHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Get the widget ID
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, 
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        
        // If widget ID is invalid, close the activity
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        
        // Initialize HTTP client and main thread handler
        client = new OkHttpClient();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Check if confirmation is required
        boolean requireConfirmation = WidgetConfigActivity.requiresConfirmation(this, appWidgetId);
        
        if (requireConfirmation) {
            // Show confirmation dialog
            showConfirmationDialog();
        } else {
            // Skip confirmation and directly call the service
            String serviceDomain = WidgetConfigActivity.getServiceDomain(this, appWidgetId);
            String serviceName = WidgetConfigActivity.getServiceName(this, appWidgetId);
            String entityId = WidgetConfigActivity.getEntityId(this, appWidgetId);
            callHomeAssistantService(serviceDomain, serviceName, entityId);
        }
    }
    
    private void showConfirmationDialog() {
        // Get widget configuration
        String serviceDomain = WidgetConfigActivity.getServiceDomain(this, appWidgetId);
        String serviceName = WidgetConfigActivity.getServiceName(this, appWidgetId);
        String entityId = WidgetConfigActivity.getEntityId(this, appWidgetId);
        String widgetTitle = WidgetConfigActivity.getWidgetTitle(this, appWidgetId);
        
        // Get custom confirmation message if available, otherwise use default
        String customMessage = WidgetConfigActivity.getConfirmationMessage(this, appWidgetId);
        String message = (customMessage != null && !customMessage.isEmpty()) 
                ? customMessage 
                : getString(R.string.confirm_message);
        
        // Create and show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callHomeAssistantService(serviceDomain, serviceName, entityId);
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish(); // Close the activity without making the API call
                    }
                })
                .setCancelable(true)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish(); // Close when user cancels
                    }
                })
                .show();
    }
    
    private void callHomeAssistantService(String domain, String service, String entityId) {
        String baseUrl = SettingsActivity.getHomeAssistantUrl(this);
        String token = SettingsActivity.getHomeAssistantToken(this);
        String url = baseUrl + "/api/services/" + domain + "/" + service;
        
        // Create service call payload
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("entity_id", entityId);
        } catch (JSONException e) {
            e.printStackTrace();
            showErrorAndFinish(e.getMessage());
            return;
        }
        
        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        
        // Build and execute the request
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showErrorAndFinish(e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final boolean successful = response.isSuccessful();
                final String responseBody = response.body() != null ? response.body().string() : "";
                
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (successful) {
                            Toast.makeText(ConfirmActionActivity.this, 
                                    R.string.action_success, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ConfirmActionActivity.this, 
                                    getString(R.string.action_failed) + " " + responseBody, 
                                    Toast.LENGTH_LONG).show();
                        }
                        finish();
                    }
                });
            }
        });
    }
    
    private void showErrorAndFinish(String errorMessage) {
        Toast.makeText(this, 
                getString(R.string.action_failed) + " " + errorMessage, 
                Toast.LENGTH_LONG).show();
        finish();
    }
}