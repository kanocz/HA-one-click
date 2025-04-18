package cz.nsl.oneactionaclick.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HomeAssistantService {
    private String domain;
    private String serviceName;
    private String displayName;
    private Map<String, Object> serviceAttributes;
    
    public HomeAssistantService(String domain, String serviceName) {
        this.domain = domain;
        this.serviceName = serviceName;
        this.displayName = serviceName.replace("_", " ");
        this.serviceAttributes = new HashMap<>();
    }
    
    public String getDomain() {
        return domain;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getFullServiceName() {
        return domain + "." + serviceName;
    }
    
    public String getService() {
        return serviceName;
    }
    
    public Map<String, Object> getServiceAttributes() {
        return serviceAttributes;
    }
    
    public void setServiceAttributes(Map<String, Object> serviceAttributes) {
        this.serviceAttributes = serviceAttributes;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
    
    /**
     * Parse the Home Assistant services API response
     * 
     * @param jsonString JSON string response from Home Assistant API
     * @return Map of domain to list of services
     */
    public static Map<String, List<HomeAssistantService>> parseServices(String jsonString) {
        Map<String, List<HomeAssistantService>> servicesByDomain = new HashMap<>();
        
        try {
            // Log the first part of the response for debugging
            String logPreview = jsonString.length() > 200 ? jsonString.substring(0, 200) + "..." : jsonString;
            android.util.Log.d("HomeAssistantService", "Parsing services response: " + logPreview);
            
            // Check if the string is empty or null
            if (jsonString == null || jsonString.trim().isEmpty()) {
                android.util.Log.e("HomeAssistantService", "Empty or null JSON response from API");
                return servicesByDomain;
            }
            
            // Handle both array and object response formats
            if (jsonString.trim().startsWith("[")) {
                // Array format (newer Home Assistant versions)
                JSONArray jsonArray = new JSONArray(jsonString);
                
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject serviceObj = jsonArray.getJSONObject(i);
                    
                    if (serviceObj.has("domain") && serviceObj.has("services")) {
                        String domain = serviceObj.getString("domain");
                        List<HomeAssistantService> services = new ArrayList<>();
                        JSONObject servicesObj = serviceObj.getJSONObject("services");
                        
                        Iterator<String> serviceIterator = servicesObj.keys();
                        while (serviceIterator.hasNext()) {
                            String serviceName = serviceIterator.next();
                            HomeAssistantService service = new HomeAssistantService(domain, serviceName);
                            
                            // Parse service attributes if available
                            JSONObject serviceDetails = servicesObj.getJSONObject(serviceName);
                            if (serviceDetails.has("fields")) {
                                JSONObject fields = serviceDetails.getJSONObject("fields");
                                Map<String, Object> attributes = new HashMap<>();
                                
                                Iterator<String> fieldIterator = fields.keys();
                                while (fieldIterator.hasNext()) {
                                    String fieldName = fieldIterator.next();
                                    JSONObject fieldDetails = fields.getJSONObject(fieldName);
                                    attributes.put(fieldName, fieldDetails);
                                }
                                
                                service.setServiceAttributes(attributes);
                            }
                            
                            services.add(service);
                        }
                        
                        servicesByDomain.put(domain, services);
                    }
                }
            } else {
                // Object format (older Home Assistant versions)
                JSONObject jsonResponse = new JSONObject(jsonString);
                Iterator<String> domainIterator = jsonResponse.keys();
                
                while (domainIterator.hasNext()) {
                    String domain = domainIterator.next();
                    JSONObject domainServices = jsonResponse.getJSONObject(domain);
                    List<HomeAssistantService> services = new ArrayList<>();
                    
                    Iterator<String> serviceIterator = domainServices.keys();
                    while (serviceIterator.hasNext()) {
                        String serviceName = serviceIterator.next();
                        HomeAssistantService service = new HomeAssistantService(domain, serviceName);
                        
                        // Parse service attributes if available
                        JSONObject serviceDetails = domainServices.getJSONObject(serviceName);
                        if (serviceDetails.has("fields")) {
                            JSONObject fields = serviceDetails.getJSONObject("fields");
                            Map<String, Object> attributes = new HashMap<>();
                            
                            Iterator<String> fieldIterator = fields.keys();
                            while (fieldIterator.hasNext()) {
                                String fieldName = fieldIterator.next();
                                JSONObject fieldDetails = fields.getJSONObject(fieldName);
                                attributes.put(fieldName, fieldDetails);
                            }
                            
                            service.setServiceAttributes(attributes);
                        }
                        
                        services.add(service);
                    }
                    
                    servicesByDomain.put(domain, services);
                }
            }

            android.util.Log.d("HomeAssistantService", "Found " + servicesByDomain.size() + " service domains");
        } catch (JSONException e) {
            android.util.Log.e("HomeAssistantService", "Error parsing services JSON: " + e.getMessage(), e);
            e.printStackTrace();
        } catch (Exception e) {
            android.util.Log.e("HomeAssistantService", "Unexpected error parsing services: " + e.getMessage(), e);
            e.printStackTrace();
        }
        
        return servicesByDomain;
    }
}