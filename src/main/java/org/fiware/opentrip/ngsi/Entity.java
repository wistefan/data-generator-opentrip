package org.fiware.opentrip.ngsi;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Entity {

    @JsonProperty("@context")
    private String context;
    private String type;
    private String id;

    public void putProperty(String name, Object property) {
        propertiesAndRelationships.put(name, new Property(property));
    }

    public void putRelationship(String name, String relationship) {
        propertiesAndRelationships.put(name, new Relationship(relationship));
    }

    @JsonIgnore
    private Map<String, Object> propertiesAndRelationships = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getMap() {
        return propertiesAndRelationships;
    }
}
