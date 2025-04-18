package cz.nsl.oneactionaclick.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HomeAssistantEntity {
    private String entityId;
    private String domain;
    private String entityName;
    private String friendlyName;
    private Map<String, Object> attributes;
    private String state;
    
    public HomeAssistantEntity(String entityId, String state) {
        this.entityId = entityId;
        this.state = state;
        this.attributes = new HashMap<>();
        
        // Parse domain and entity name from the entity ID
        String[] parts = entityId.split("\\.", 2);
        if (parts.length == 2) {
            this.domain = parts[0];
            this.entityName = parts[1];
        } else {
            this.domain = "";
            this.entityName = entityId;
        }
    }
    
    public String getEntityId() {
        return entityId;
    }
    
    public String getDomain() {
        return domain;
    }
    
    public String getEntityName() {
        return entityName;
    }
    
    public String getFriendlyName() {
        return friendlyName != null ? friendlyName : entityName.replace("_", " ");
    }
    
    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        if (attributes.containsKey("friendly_name")) {
            this.friendlyName = (String) attributes.get("friendly_name");
        }
    }
    
    public String getState() {
        return state;
    }
    
    @Override
    public String toString() {
        return getFriendlyName() + " (" + entityId + ")";
    }
    
    /**
     * Parse the Home Assistant states API response
     * 
     * @param jsonString JSON string response from Home Assistant API
     * @return Map of domain to list of entities
     */
    public static Map<String, List<HomeAssistantEntity>> parseEntities(String jsonString) {
        Map<String, List<HomeAssistantEntity>> entitiesByDomain = new HashMap<>();
        
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject entityObject = jsonArray.getJSONObject(i);
                String entityId = entityObject.getString("entity_id");
                String state = entityObject.getString("state");
                
                HomeAssistantEntity entity = new HomeAssistantEntity(entityId, state);
                
                // Parse attributes if available
                if (entityObject.has("attributes")) {
                    JSONObject attributesObj = entityObject.getJSONObject("attributes");
                    Map<String, Object> attributes = new HashMap<>();
                    
                    // Extract all attributes
                    for (Iterator<String> it = attributesObj.keys(); it.hasNext(); ) {
                        String key = it.next();
                        attributes.put(key, attributesObj.get(key));
                    }
                    
                    entity.setAttributes(attributes);
                }
                
                // Group entities by domain
                if (!entitiesByDomain.containsKey(entity.getDomain())) {
                    entitiesByDomain.put(entity.getDomain(), new ArrayList<>());
                }
                entitiesByDomain.get(entity.getDomain()).add(entity);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return entitiesByDomain;
    }
    
    /**
     * Parse the Home Assistant states API response and return a list of entities
     * 
     * @param jsonString JSON string response from Home Assistant API
     * @return List of all entities
     */
    public static List<HomeAssistantEntity> parseEntitiesList(String jsonString) {
        List<HomeAssistantEntity> entities = new ArrayList<>();
        
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject entityObject = jsonArray.getJSONObject(i);
                String entityId = entityObject.getString("entity_id");
                String state = entityObject.getString("state");
                
                HomeAssistantEntity entity = new HomeAssistantEntity(entityId, state);
                
                // Parse attributes if available
                if (entityObject.has("attributes")) {
                    JSONObject attributesObj = entityObject.getJSONObject("attributes");
                    Map<String, Object> attributes = new HashMap<>();
                    
                    // Extract all attributes
                    for (Iterator<String> it = attributesObj.keys(); it.hasNext(); ) {
                        String key = it.next();
                        attributes.put(key, attributesObj.get(key));
                    }
                    
                    entity.setAttributes(attributes);
                }
                
                entities.add(entity);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return entities;
    }
    
    /**
     * Group a list of entities by their domain
     * 
     * @param entities List of entities to group
     * @return Map of domain to list of entities
     */
    public static Map<String, List<HomeAssistantEntity>> groupByDomain(List<HomeAssistantEntity> entities) {
        Map<String, List<HomeAssistantEntity>> entitiesByDomain = new HashMap<>();
        
        for (HomeAssistantEntity entity : entities) {
            String domain = entity.getDomain();
            if (!entitiesByDomain.containsKey(domain)) {
                entitiesByDomain.put(domain, new ArrayList<>());
            }
            entitiesByDomain.get(domain).add(entity);
        }
        
        return entitiesByDomain;
    }
}