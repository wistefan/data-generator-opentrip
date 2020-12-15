package org.fiware.opentrip.ngsi;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class Relationship {

    private final String type = "Relationship";
    private String object;
}
