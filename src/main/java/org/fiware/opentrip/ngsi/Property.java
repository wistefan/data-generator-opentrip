package org.fiware.opentrip.ngsi;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class Property {

     private final String type = "Property";
     private Object value;

}
