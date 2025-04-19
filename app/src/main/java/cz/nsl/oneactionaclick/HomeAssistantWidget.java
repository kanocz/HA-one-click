package cz.nsl.oneactionaclick;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Implementation of App Widget functionality.
 * This widget shows a simple button that triggers a Home Assistant API call after confirmation.
 */
public class HomeAssistantWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
    
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it
        for (int appWidgetId : appWidgetIds) {
            WidgetConfigActivity.deleteWidgetConfiguration(context, appWidgetId);
        }
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        // Handle theme changes by refreshing all widgets
        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            refreshAllWidgets(context);
        }
    }
    
    /**
     * Refreshes all instances of the widget to update with current theme colors
     */
    public static void refreshAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, HomeAssistantWidget.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Get the widget configuration
        String widgetTitle = WidgetConfigActivity.getWidgetTitle(context, appWidgetId);
        int iconIndex = WidgetConfigActivity.getIconIndex(context, appWidgetId);
        int iconResource = WidgetConfigActivity.getDrawableResource(iconIndex);
        int transparency = WidgetConfigActivity.getTransparency(context, appWidgetId);
        int alphaValue = WidgetConfigActivity.getAlphaValue(transparency);
        
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        
        // Update the widget title and icon
        views.setTextViewText(R.id.widget_text, widgetTitle);
        views.setImageViewResource(R.id.widget_icon, iconResource);
        
        // Apply transparency to the widget background
        int backgroundColor = context.getResources().getColor(R.color.widgetBackground);
        int transparentColor = Color.argb(
                alphaValue,
                Color.red(backgroundColor),
                Color.green(backgroundColor),
                Color.blue(backgroundColor)
        );
        views.setInt(R.id.widget_container, "setBackgroundColor", transparentColor);
        
        // Create an Intent to launch the confirmation activity when widget is clicked
        Intent actionIntent = new Intent(context, ConfirmActionActivity.class);
        actionIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        
        // Use appropriate flag based on the Android version
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent actionPendingIntent = PendingIntent.getActivity(context, appWidgetId, actionIntent, flags);
        
        // Set click listener on the container to make the entire widget clickable
        views.setOnClickPendingIntent(R.id.widget_container, actionPendingIntent);
        
        // For older Android versions (pre-Android 12), add a gear icon for configuration
        // For Android 12+ (S), rely on the system's built-in widget configuration menu
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Only show gear icon on older Android versions
            views.setViewVisibility(R.id.widget_settings, View.VISIBLE);
            
            // Create an Intent to launch the configuration activity for editing the widget
            Intent configIntent = new Intent(context, WidgetConfigActivity.class);
            configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            configIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            
            PendingIntent configPendingIntent = PendingIntent.getActivity(
                    context, 
                    appWidgetId + 10000, // Use different request code to avoid conflicts
                    configIntent, 
                    flags
            );
            
            views.setOnClickPendingIntent(R.id.widget_settings, configPendingIntent);
        } else {
            // Hide gear icon on Android 12+ as we'll use the system's configuration option
            views.setViewVisibility(R.id.widget_settings, View.GONE);
        }
        
        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}