package cz.nsl.oneactionaclick;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.appcompat.widget.SwitchCompat;

import cz.nsl.oneactionaclick.api.HomeAssistantApiClient;
import cz.nsl.oneactionaclick.api.HomeAssistantEntity;
import cz.nsl.oneactionaclick.api.HomeAssistantService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class WidgetConfigActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private EditText editWidgetTitle, editConfirmationMessage;
    private TextView textHaInfo, textValidationMessage, textError, textErrorDetails, textRequestLogs;
    private Spinner spinnerDomain, spinnerService, spinnerEntity, spinnerIcon;
    private ProgressBar progressDomains, progressServices, progressEntities;
    private ScrollView scrollErrorDetails, scrollRequestLogs;
    private SeekBar transparencySeekBar;
    private Button saveButton, toggleLogsButton, clearLogsButton;
    private SwitchCompat switchRequireConfirmation;
    private LinearLayout confirmationMessageContainer;

    private boolean logsVisible = false;
    private Handler logUpdateHandler = new Handler();
    private Runnable logUpdateRunnable;

    private HomeAssistantApiClient apiClient;

    // Data for spinners
    private List<String> domains = new ArrayList<>();
    private Map<String, List<HomeAssistantService>> servicesByDomain;
    private Map<String, List<HomeAssistantEntity>> entitiesByDomain;
    private List<HomeAssistantService> servicesForSelectedDomain;

    // Selected values
    private String selectedDomain;
    private HomeAssistantService selectedService;
    private HomeAssistantEntity selectedEntity;

    // Shared preferences file name
    private static final String PREFS_NAME = "cz.nsl.oneactionaclick.WidgetPrefs";

    // Keys for storing widget configuration in SharedPreferences
    private static final String PREF_PREFIX_KEY = "appwidget_";
    private static final String PREF_TITLE_KEY = "_title";
    private static final String PREF_DOMAIN_KEY = "_domain";
    private static final String PREF_SERVICE_KEY = "_service";
    private static final String PREF_ENTITY_KEY = "_entity";
    private static final String PREF_ICON_KEY = "_icon";
    private static final String PREF_TRANSPARENCY_KEY = "_transparency";
    private static final String PREF_REQUIRE_CONFIRMATION_KEY = "_require_confirmation";
    private static final String PREF_CONFIRMATION_MESSAGE_KEY = "_confirmation_message";

    // Default transparency value (50%)
    private static final int DEFAULT_TRANSPARENCY = 50;

    // Log update interval in milliseconds
    private static final int LOG_UPDATE_INTERVAL = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_widget_config);

        // Create API client
        apiClient = new HomeAssistantApiClient(this);

        // Find views
        editWidgetTitle = findViewById(R.id.edit_widget_title);
        editConfirmationMessage = findViewById(R.id.edit_confirmation_message);
        textHaInfo = findViewById(R.id.text_ha_info);
        spinnerDomain = findViewById(R.id.spinner_domain);
        spinnerService = findViewById(R.id.spinner_service);
        spinnerEntity = findViewById(R.id.spinner_entity);
        spinnerIcon = findViewById(R.id.spinner_icon);
        progressDomains = findViewById(R.id.progress_domains);
        progressServices = findViewById(R.id.progress_services);
        progressEntities = findViewById(R.id.progress_entities);
        textError = findViewById(R.id.text_error);
        scrollErrorDetails = findViewById(R.id.scroll_error_details);
        textErrorDetails = findViewById(R.id.text_error_details);
        textValidationMessage = findViewById(R.id.text_validation_message);
        transparencySeekBar = findViewById(R.id.seekbar_transparency);
        saveButton = findViewById(R.id.button_save);
        switchRequireConfirmation = findViewById(R.id.switch_require_confirmation);
        confirmationMessageContainer = findViewById(R.id.confirmation_message_container);

        // Set up confirmation toggle listener
        switchRequireConfirmation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            confirmationMessageContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Initialize confirmation message container visibility
        confirmationMessageContainer.setVisibility(switchRequireConfirmation.isChecked() ? View.VISIBLE : View.GONE);

        // Request log views
        toggleLogsButton = findViewById(R.id.button_toggle_logs);
        clearLogsButton = findViewById(R.id.button_clear_logs);
        scrollRequestLogs = findViewById(R.id.scroll_request_logs);
        textRequestLogs = findViewById(R.id.text_request_logs);

        // Display Home Assistant info
        updateHomeAssistantInfo();

        // Setup icon spinner with theme-aware adapter
        setupIconSpinner();

        // Get the widget ID from the intent extras
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If they gave us an intent without the widget ID, just bail.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        // Set default values and existing values if editing
        setupInitialValues();

        // Set up spinner listeners
        setupSpinnerListeners();

        // Load domains and services from Home Assistant
        loadHomeAssistantData();

        // Save button click handler
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveWidgetConfiguration();
            }
        });

        // Setup request log toggle and clear buttons
        setupRequestLogButtons();

        // Start periodic log updates
        startLogUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop log updates when activity is destroyed
        stopLogUpdates();
    }

    /**
     * Setup request log toggle and clear buttons
     */
    private void setupRequestLogButtons() {
        toggleLogsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logsVisible = !logsVisible;
                toggleLogsButton.setText(logsVisible ? R.string.hide_logs : R.string.show_logs);
                scrollRequestLogs.setVisibility(logsVisible ? View.VISIBLE : View.GONE);
                clearLogsButton.setVisibility(logsVisible ? View.VISIBLE : View.GONE);

                if (logsVisible) {
                    updateRequestLogs();
                }
            }
        });

        clearLogsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HomeAssistantApiClient.clearRequestLogs();
                updateRequestLogs();
            }
        });

        // Initialize log area visibility
        scrollRequestLogs.setVisibility(View.GONE);
        clearLogsButton.setVisibility(View.GONE);
    }

    /**
     * Start periodic updates of the request log display
     */
    private void startLogUpdates() {
        logUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (logsVisible) {
                    updateRequestLogs();
                }
                logUpdateHandler.postDelayed(this, LOG_UPDATE_INTERVAL);
            }
        };

        // Start immediate log updates
        logUpdateHandler.post(logUpdateRunnable);
    }

    /**
     * Stop periodic updates of the request log display
     */
    private void stopLogUpdates() {
        if (logUpdateHandler != null && logUpdateRunnable != null) {
            logUpdateHandler.removeCallbacks(logUpdateRunnable);
        }
    }

    /**
     * Update the request log display with current logs
     */
    private void updateRequestLogs() {
        List<HomeAssistantApiClient.ApiRequestLog> logs = HomeAssistantApiClient.getRequestLogs();

        if (logs.isEmpty()) {
            textRequestLogs.setText("No API requests recorded yet.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (HomeAssistantApiClient.ApiRequestLog log : logs) {
            sb.append(log.toString()).append("\n\n");
        }

        textRequestLogs.setText(sb.toString());

        // Scroll to the top to show the most recent logs
        scrollRequestLogs.post(new Runnable() {
            @Override
            public void run() {
                scrollRequestLogs.scrollTo(0, 0);
            }
        });
    }

    /**
     * Updates the Home Assistant instance information display
     */
    private void updateHomeAssistantInfo() {
        String haUrl = SettingsActivity.getHomeAssistantUrl(this);
        boolean isUsingOAuth = SettingsActivity.isUsingOAuth(this);

        StringBuilder infoText = new StringBuilder();
        infoText.append("Connected to: ").append(haUrl);

        if (isUsingOAuth) {
            infoText.append(" (using OAuth)");
        } else {
            infoText.append(" (using Long-lived Token)");
        }

        textHaInfo.setText(infoText.toString());
    }

    /**
     * Sets up initial values for the form
     */
    private void setupInitialValues() {
        // Default or existing widget title
        String savedTitle = getWidgetTitle(this, appWidgetId);
        if (!savedTitle.equals(getString(R.string.widget_text))) {
            editWidgetTitle.setText(savedTitle);
        }

        // Load saved transparency value
        int savedTransparency = getTransparency(this, appWidgetId);
        transparencySeekBar.setProgress(savedTransparency);

        // Disable save button until we have loaded necessary data
        saveButton.setEnabled(false);
    }

    /**
     * Sets up listeners for the spinner selections
     */
    private void setupSpinnerListeners() {
        // Domain selection
        spinnerDomain.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && domains.size() > position - 1) {
                    // Apply -1 offset to account for the prompt item
                    selectedDomain = domains.get(position - 1);
                    updateServicesSpinner();
                    updateEntitiesSpinner();
                } else {
                    selectedDomain = null;
                    clearServicesSpinner();
                    clearEntitiesSpinner();
                }
                updateSaveButtonState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedDomain = null;
                clearServicesSpinner();
                clearEntitiesSpinner();
                updateSaveButtonState();
            }
        });

        // Service selection
        spinnerService.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && servicesForSelectedDomain != null &&
                        servicesForSelectedDomain.size() >= position) {
                    selectedService = servicesForSelectedDomain.get(position - 1); // Offset for prompt item
                } else {
                    selectedService = null;
                }
                updateSaveButtonState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedService = null;
                updateSaveButtonState();
            }
        });

        // Entity selection
        spinnerEntity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && entitiesByDomain != null &&
                        entitiesByDomain.containsKey(selectedDomain)) {
                    List<HomeAssistantEntity> entities = entitiesByDomain.get(selectedDomain);
                    if (entities != null && entities.size() >= position) {
                        selectedEntity = entities.get(position - 1); // Offset for prompt item
                    }
                } else {
                    selectedEntity = null;
                }
                updateSaveButtonState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedEntity = null;
                updateSaveButtonState();
            }
        });
    }

    /**
     * Updates the save button enabled state based on selections
     */
    private void updateSaveButtonState() {
        StringBuilder validationMessages = new StringBuilder();

        // Check each required field
        if (editWidgetTitle.getText().toString().trim().length() == 0) {
            validationMessages.append("• Widget title is required\n");
        }

        if (selectedDomain == null) {
            validationMessages.append("• Select a domain\n");
        }

        if (selectedService == null) {
            validationMessages.append("• Select a service\n");
        }

        if (selectedEntity == null) {
            validationMessages.append("• Select an entity\n");
        }

        boolean hasRequiredValues = validationMessages.length() == 0;

        // Update UI based on validation result
        if (hasRequiredValues) {
            // All required fields are filled
            saveButton.setEnabled(true);
            textValidationMessage.setVisibility(View.GONE);
        } else {
            // Some fields are missing
            saveButton.setEnabled(false);
            textValidationMessage.setText("Save disabled: Missing required fields\n" + validationMessages.toString());
            textValidationMessage.setVisibility(View.VISIBLE);
        }

        // Log validation state for debugging
        Log.d("WidgetConfigActivity", "Save button validation: " +
                "title=" + (editWidgetTitle.getText().toString().trim().length() > 0) +
                ", domain=" + (selectedDomain != null) +
                ", service=" + (selectedService != null) +
                ", entity=" + (selectedEntity != null) +
                ", enabled=" + hasRequiredValues);
    }

    /**
     * Sets up the icon spinner with theme-aware adapter
     */
    private void setupIconSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.icon_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerIcon.setAdapter(adapter);

        // Set default or saved icon selection
        int savedIconIndex = getIconIndex(this, appWidgetId);
        if (savedIconIndex >= 0 && savedIconIndex < adapter.getCount()) {
            spinnerIcon.setSelection(savedIconIndex);
        }

        // Check if we're in night mode to apply custom styling if needed
        int nightModeFlags = getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK;

        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            // For dark theme, we could apply custom text appearance if needed
        }
    }

    /**
     * Loads domains and services from Home Assistant
     */
    private void loadHomeAssistantData() {
        // Show progress indicators
        progressDomains.setVisibility(View.VISIBLE);
        progressServices.setVisibility(View.VISIBLE);
        progressEntities.setVisibility(View.VISIBLE);

        // Clear any previous error
        textError.setVisibility(View.GONE);

        Log.d("WidgetConfigActivity", "Starting to fetch services from Home Assistant");

        // Load services
        apiClient.fetchServices(new HomeAssistantApiClient.ServiceCallback() {
            @Override
            public void onServicesLoaded(Map<String, List<HomeAssistantService>> services) {
                if (services == null || services.isEmpty()) {
                    Log.e("WidgetConfigActivity", "Received empty services map");
                    progressServices.setVisibility(View.GONE);
                    displayError("No services available. Please check your Home Assistant connection.");
                    return;
                }

                Log.d("WidgetConfigActivity", "Services loaded successfully: " + services.size() + " domains");
                servicesByDomain = services;
                updateDomainSpinner();
                progressServices.setVisibility(View.GONE);

                // Try to restore saved settings
                restoreSavedSelections();
            }

            @Override
            public void onError(String error) {
                Log.e("WidgetConfigActivity", "Error loading services: " + error);
                progressDomains.setVisibility(View.GONE);
                progressServices.setVisibility(View.GONE);
                displayError("Error loading services: " + error);
            }

            @Override
            public void onDetailedError(String error, String detailedInfo) {
                Log.e("WidgetConfigActivity", "Detailed error loading services: " + error);
                progressDomains.setVisibility(View.GONE);
                progressServices.setVisibility(View.GONE);
                displayDetailedError("Error loading services: " + error, detailedInfo);
            }
        });

        Log.d("WidgetConfigActivity", "Starting to fetch entities from Home Assistant");

        // Load entities
        apiClient.fetchEntities(new HomeAssistantApiClient.EntityCallback() {
            @Override
            public void onEntitiesLoaded(List<HomeAssistantEntity> entities) {
                // This method is optional since we use grouped entities
                Log.d("WidgetConfigActivity", "Entities loaded: " + (entities != null ? entities.size() : 0));
                progressEntities.setVisibility(View.GONE);
            }

            @Override
            public void onEntitiesByDomainLoaded(Map<String, List<HomeAssistantEntity>> entities) {
                if (entities == null || entities.isEmpty()) {
                    Log.e("WidgetConfigActivity", "Received empty entities map");
                    progressEntities.setVisibility(View.GONE);
                    displayError("No entities available. Please check your Home Assistant connection.");
                    return;
                }

                Log.d("WidgetConfigActivity", "Entities by domain loaded: " + entities.size() + " domains");
                entitiesByDomain = entities;
                progressEntities.setVisibility(View.GONE);

                // Update entity spinner if domain is already selected
                if (selectedDomain != null) {
                    updateEntitiesSpinner();
                }

                // Try to restore saved settings
                restoreSavedSelections();
            }

            @Override
            public void onError(String error) {
                Log.e("WidgetConfigActivity", "Error loading entities: " + error);
                progressEntities.setVisibility(View.GONE);
                displayError("Error loading entities: " + error);
            }

            @Override
            public void onDetailedError(String error, String detailedInfo) {
                Log.e("WidgetConfigActivity", "Detailed error loading entities: " + error);
                progressEntities.setVisibility(View.GONE);
                displayDetailedError("Error loading entities: " + error, detailedInfo);
            }
        });
    }

    /**
     * Restore previously saved selections when editing a widget
     */
    private void restoreSavedSelections() {
        if (servicesByDomain == null || entitiesByDomain == null) {
            // Wait until both services and entities are loaded
            return;
        }

        // Get saved values
        String savedDomain = getServiceDomain(this, appWidgetId);
        String savedService = getServiceName(this, appWidgetId);
        String savedEntityId = getEntityId(this, appWidgetId);

        // If we have saved values and this is an existing widget, try to restore them
        if (!savedDomain.equals("light") || !savedService.equals("toggle") ||
                !savedEntityId.equals("light.living_room")) {

            // Find domain in spinner
            for (int i = 0; i < domains.size(); i++) {
                if (domains.get(i).equals(savedDomain)) {
                    spinnerDomain.setSelection(i);
                    selectedDomain = savedDomain;
                    break;
                }
            }

            // Update services for this domain
            if (selectedDomain != null) {
                updateServicesSpinner();

                // Find service in spinner
                if (servicesForSelectedDomain != null) {
                    for (int i = 0; i < servicesForSelectedDomain.size(); i++) {
                        if (servicesForSelectedDomain.get(i).getService().equals(savedService)) {
                            // +1 for prompt item
                            spinnerService.setSelection(i + 1);
                            selectedService = servicesForSelectedDomain.get(i);
                            break;
                        }
                    }
                }

                // Update entities for this domain
                updateEntitiesSpinner();

                // Find entity in spinner
                if (entitiesByDomain.containsKey(selectedDomain)) {
                    List<HomeAssistantEntity> entities = entitiesByDomain.get(selectedDomain);
                    for (int i = 0; i < entities.size(); i++) {
                        if (entities.get(i).getEntityId().equals(savedEntityId)) {
                            // +1 for prompt item
                            spinnerEntity.setSelection(i + 1);
                            selectedEntity = entities.get(i);
                            break;
                        }
                    }
                }
            }
        }

        // Update save button state
        updateSaveButtonState();
    }

    /**
     * Update the domain spinner with available domains
     */
    private void updateDomainSpinner() {
        // Get domains from services and sort them
        domains = new ArrayList<>(servicesByDomain.keySet());
        Collections.sort(domains);

        // Add a prompt as the first item
        List<String> displayDomains = new ArrayList<>();
        displayDomains.add(getString(R.string.select_domain));
        displayDomains.addAll(domains);

        // Create and set adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, displayDomains);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDomain.setAdapter(adapter);

        // Hide progress indicator
        progressDomains.setVisibility(View.GONE);
    }

    /**
     * Update the services spinner based on selected domain
     */
    private void updateServicesSpinner() {
        if (selectedDomain != null && servicesByDomain.containsKey(selectedDomain)) {
            List<HomeAssistantService> services = servicesByDomain.get(selectedDomain);

            // Debug log to track domain and services
            Log.d("WidgetConfigActivity", "Updating services spinner for domain: " + selectedDomain
                    + ", found " + services.size() + " services");

            // Sort services by name
            Collections.sort(services, new Comparator<HomeAssistantService>() {
                @Override
                public int compare(HomeAssistantService s1, HomeAssistantService s2) {
                    return s1.getService().compareTo(s2.getService());
                }
            });

            // Filter services to only include those belonging to the selected domain
            List<HomeAssistantService> filteredServices = new ArrayList<>();
            for (HomeAssistantService service : services) {
                if (selectedDomain.equals(service.getDomain())) {
                    filteredServices.add(service);
                    Log.d("WidgetConfigActivity", "Added service: " + service.getService() + " for domain: " + selectedDomain);
                } else {
                    Log.w("WidgetConfigActivity", "Skipping service with mismatched domain: " + service.getService() +
                            " has domain " + service.getDomain() + " but selected domain is " + selectedDomain);
                }
            }

            // Create adapter with a prompt as first item
            List<String> displayServices = new ArrayList<>();
            displayServices.add(getString(R.string.select_service));

            for (HomeAssistantService service : filteredServices) {
                displayServices.add(service.getDisplayName() + " (" + selectedDomain + "." + service.getService() + ")");
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, displayServices);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerService.setAdapter(adapter);
            spinnerService.setEnabled(true);

            // Store the filtered services for use in onItemSelected
            this.servicesForSelectedDomain = filteredServices;
        } else {
            if (selectedDomain != null) {
                Log.e("WidgetConfigActivity", "Domain selected but no services found: " + selectedDomain);
            }
            clearServicesSpinner();
        }
    }

    /**
     * Clear the services spinner when no domain is selected
     */
    private void clearServicesSpinner() {
        List<String> emptyList = new ArrayList<>();
        emptyList.add(getString(R.string.select_service));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, emptyList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerService.setAdapter(adapter);
        spinnerService.setEnabled(false);
        selectedService = null;
    }

    /**
     * Update the entities spinner based on selected domain
     */
    private void updateEntitiesSpinner() {
        if (selectedDomain != null && entitiesByDomain != null &&
                entitiesByDomain.containsKey(selectedDomain)) {
            List<HomeAssistantEntity> entities = entitiesByDomain.get(selectedDomain);

            // Sort entities by friendly name
            Collections.sort(entities, new Comparator<HomeAssistantEntity>() {
                @Override
                public int compare(HomeAssistantEntity e1, HomeAssistantEntity e2) {
                    return e1.getFriendlyName().compareTo(e2.getFriendlyName());
                }
            });

            // Create adapter with a prompt as first item
            List<String> displayEntities = new ArrayList<>();
            displayEntities.add(getString(R.string.select_entity));

            for (HomeAssistantEntity entity : entities) {
                displayEntities.add(entity.toString());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, displayEntities);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerEntity.setAdapter(adapter);
            spinnerEntity.setEnabled(true);
        } else {
            clearEntitiesSpinner();
        }
    }

    /**
     * Clear the entities spinner when no domain is selected
     */
    private void clearEntitiesSpinner() {
        List<String> emptyList = new ArrayList<>();
        emptyList.add(getString(R.string.select_entity));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, emptyList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEntity.setAdapter(adapter);
        spinnerEntity.setEnabled(false);
        selectedEntity = null;
    }

    /**
     * Display a detailed error message with headers and technical information
     */
    private void displayDetailedError(String message, String detailedInfo) {
        textError.setText(getString(R.string.error_loading_data, message));
        textError.setVisibility(View.VISIBLE);

        // Always show detailed error information section
        StringBuilder errorDetails = new StringBuilder();

        // Check if there's already text in the error details
        CharSequence existingText = textErrorDetails.getText();
        if (existingText != null && existingText.length() > 0) {
            errorDetails.append(existingText).append("\n\n");
            errorDetails.append("--- ADDITIONAL ERROR ---\n\n");
        } else {
            errorDetails.append("ERROR DETAILS:\n\n");
        }

        if (detailedInfo != null && !detailedInfo.isEmpty()) {
            errorDetails.append(detailedInfo);
        } else {
            // Include more diagnostic information
            errorDetails.append("No detailed information available from API response.\n\n");
            errorDetails.append("Connection Information:\n");
            String baseUrl = SettingsActivity.getHomeAssistantUrl(this);
            errorDetails.append("URL: ").append(baseUrl).append("/api/services\n");
            errorDetails.append("Authentication Method: ").append(SettingsActivity.isUsingOAuth(this) ? "OAuth2" : "Long-lived Token").append("\n");

            // Add network diagnostic hints
            errorDetails.append("\nTroubleshooting Steps:\n");
            errorDetails.append("1. Verify Home Assistant URL is correct (include http:// or https://)\n");
            errorDetails.append("2. Confirm your authentication token is valid\n");
            errorDetails.append("3. Check if you can access Home Assistant in a browser\n");
            errorDetails.append("4. Verify network connectivity on your device\n");
            errorDetails.append("5. Check if Home Assistant server is running\n");
        }

        // Set detailed error text and make it visible
        textErrorDetails.setText(errorDetails.toString());
        scrollErrorDetails.setVisibility(View.VISIBLE);

        // Make sure error section is fully visible by scrolling to it
        final ScrollView parentScrollView = findViewById(R.id.widget_config_scrollview);
        if (parentScrollView != null) {
            parentScrollView.post(new Runnable() {
                @Override
                public void run() {
                    parentScrollView.smoothScrollTo(0, textError.getTop());
                }
            });
        }

        // Log the error for debug purposes
        Log.e("WidgetConfigActivity", "Error: " + message + "\nDetails: " + errorDetails.toString());
    }

    /**
     * Display an error message
     */
    private void displayError(String message) {
        // Instead of hiding the details, show basic error info
        displayDetailedError(message, "Basic error without additional details.\nPlease check your Home Assistant connection and credentials.");
    }

    /**
     * Save the widget configuration
     */
    private void saveWidgetConfiguration() {
        // Get the entered values
        final String widgetTitle = editWidgetTitle.getText().toString().trim();
        final int iconIndex = spinnerIcon.getSelectedItemPosition();
        final int transparency = transparencySeekBar.getProgress();

        // Validate all required fields are selected
        if (widgetTitle.isEmpty() || selectedDomain == null ||
                selectedService == null || selectedEntity == null) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save configuration
        saveWidgetConfiguration(appWidgetId, widgetTitle, selectedDomain,
                selectedService.getService(), selectedEntity.getEntityId(),
                iconIndex, transparency);

        // Update widget
        updateWidget();

        // Return success
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    private void saveWidgetConfiguration(int appWidgetId, String title, String domain,
                                         String service, String entityId, int iconIndex,
                                         int transparency) {
        SharedPreferences.Editor prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + PREF_TITLE_KEY, title);
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + PREF_DOMAIN_KEY, domain);
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + PREF_SERVICE_KEY, service);
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + PREF_ENTITY_KEY, entityId);
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + PREF_ICON_KEY, iconIndex);
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + PREF_TRANSPARENCY_KEY, transparency);
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + PREF_REQUIRE_CONFIRMATION_KEY, switchRequireConfirmation.isChecked());
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + PREF_CONFIRMATION_MESSAGE_KEY, editConfirmationMessage.getText().toString().trim());
        prefs.apply();
    }

    private void updateWidget() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        HomeAssistantWidget.updateAppWidget(this, appWidgetManager, appWidgetId);
    }

    // Static methods to retrieve widget configuration
    public static String getWidgetTitle(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_PREFIX_KEY + appWidgetId + PREF_TITLE_KEY, context.getString(R.string.widget_text));
    }

    public static String getServiceDomain(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_PREFIX_KEY + appWidgetId + PREF_DOMAIN_KEY, "light");
    }

    public static String getServiceName(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_PREFIX_KEY + appWidgetId + PREF_SERVICE_KEY, "toggle");
    }

    public static String getEntityId(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_PREFIX_KEY + appWidgetId + PREF_ENTITY_KEY, "light.living_room");
    }

    public static int getIconIndex(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(PREF_PREFIX_KEY + appWidgetId + PREF_ICON_KEY, 0);
    }

    public static int getTransparency(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(PREF_PREFIX_KEY + appWidgetId + PREF_TRANSPARENCY_KEY, DEFAULT_TRANSPARENCY);
    }

    // Convert transparency percentage to alpha value (0-255)
    public static int getAlphaValue(int transparencyPercentage) {
        // Invert the percentage because higher transparency = lower alpha
        int invertedPercentage = 100 - transparencyPercentage;
        // Map from 0-100 to 0-255
        return (int)(invertedPercentage * 2.55f);
    }

    public static int getDrawableResource(int iconIndex) {
        switch (iconIndex) {
            case 0: return R.drawable.icon_light;
            case 1: return R.drawable.icon_switch;
            case 2: return R.drawable.icon_fan;
            case 3: return R.drawable.icon_door;
            case 4: return R.drawable.icon_window;
            default: return R.drawable.icon_custom;
        }
    }

    public static boolean requiresConfirmation(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_PREFIX_KEY + appWidgetId + PREF_REQUIRE_CONFIRMATION_KEY, true);
    }

    public static String getConfirmationMessage(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_PREFIX_KEY + appWidgetId + PREF_CONFIRMATION_MESSAGE_KEY, "");
    }

    public static void deleteWidgetConfiguration(Context context, int appWidgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + PREF_TITLE_KEY);
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + PREF_DOMAIN_KEY);
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + PREF_SERVICE_KEY);
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + PREF_ENTITY_KEY);
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + PREF_ICON_KEY);
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + PREF_TRANSPARENCY_KEY);
        prefs.apply();
    }
}