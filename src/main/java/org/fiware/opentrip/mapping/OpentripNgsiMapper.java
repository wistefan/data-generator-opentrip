package org.fiware.opentrip.mapping;

import org.fiware.opentrip.model.*;
import org.fiware.opentrip.ngsi.Entity;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Mapper(componentModel = "jsr330")
public interface OpentripNgsiMapper {

    String DATA_MODEL_CONTEXT = "https://fiware.github.io/data-models/context.jsonld";

    default Entity locationToEntity(Location location) {
        String type = Location.class.getSimpleName().toLowerCase();
        Entity locationEntity = new Entity();
        locationEntity.setContext(DATA_MODEL_CONTEXT);
        locationEntity.setType(type);
        locationEntity.setId(location.getId());
        Optional.ofNullable(location.getRemarks()).ifPresent(remarks -> locationEntity.putProperty("remarks", remarks));
        Optional.ofNullable(location.getName()).ifPresent(name -> locationEntity.putProperty("name", name));
        Optional.ofNullable(location.getType()).ifPresent(locationType -> locationEntity.putProperty("locationType",
                Optional.ofNullable(locationType.getType()).map(LocationType.TypeEnum::getValue).orElseGet(() -> locationType.getOther())));
        return locationEntity;
    }

    default Entity shipmentToEntity(Shipment shipment) {
        String type = Shipment.class.getSimpleName().toLowerCase();
        Entity shipmentEntity = new Entity();
        shipmentEntity.setContext(DATA_MODEL_CONTEXT);
        shipmentEntity.setType(type);
        shipmentEntity.setId(shipment.getId());
        Optional.ofNullable(shipment.getPhysicalSender()).ifPresent(sender -> shipmentEntity.putRelationship("physicalSender", sender.getId()));
        Optional.ofNullable(shipment.getLegalSender()).ifPresent(sender -> shipmentEntity.putRelationship("legalSender", sender.getId()));
        Optional.ofNullable(shipment.getLegalAddressee()).ifPresent(addressee -> shipmentEntity.putRelationship("legalAddressee", addressee.getId()));
        Optional.ofNullable(shipment.getPhysicalAddressee()).ifPresent(addressee -> shipmentEntity.putRelationship("physicalAddressee", addressee.getId()));
        return shipmentEntity;
    }

    default Entity vehicleToEntity(Vehicle vehicle) {
        String type = Vehicle.class.getSimpleName().toLowerCase();
        Entity vehicleEntity = new Entity();
        vehicleEntity.setContext(DATA_MODEL_CONTEXT);
        vehicleEntity.setType(type);
        vehicleEntity.setId(vehicle.getId());
        Optional.ofNullable(vehicle.getName()).ifPresent(name -> vehicleEntity.putProperty("name", name));
        Optional.ofNullable(vehicle.getVehicleType()).ifPresent(vehicleType -> vehicleEntity.putProperty("vehicleType",
                Optional.ofNullable(vehicleType.getType()).map(VehicleType.TypeEnum::getValue).orElseGet(() -> vehicleType.getOther())));
        return vehicleEntity;
    }

    default Entity tripToEntity(Trip trip) {
        String type = Trip.class.getSimpleName().toLowerCase();
        Entity tripEntity = new Entity();
        tripEntity.setContext(DATA_MODEL_CONTEXT);
        tripEntity.setId(trip.getId());
        tripEntity.setType(type);
        Optional.ofNullable(trip.getName()).ifPresent(name -> tripEntity.putProperty("name", name));
        return tripEntity;
    }

    default Entity eventToEntity(Event event, String tripId) {
        String type = Event.class.getSimpleName().toLowerCase();
        Entity eventEntity = new Entity();
        eventEntity.setContext(DATA_MODEL_CONTEXT);
        eventEntity.setId(event.getId());
        eventEntity.setType(type);
        Optional.ofNullable(event.getLifecyclePhase()).ifPresent(lifecyclePhase -> eventEntity.putProperty("lifeCyclePhase", lifecyclePhase.getPhase().getValue()));
        Optional.ofNullable(event.getEventGenerationTime()).ifPresent(time -> eventEntity.putProperty("eventGenerationTime", time.toEpochSecond()));
        Optional.ofNullable(event.getType()).ifPresent(eventType -> eventEntity.putProperty("eventType", eventType));
        AtomicInteger counter = new AtomicInteger(0);
        event.getInvolvedObjects().forEach(involvedObject -> eventEntity.putRelationship(String.format("involvedObject%s", counter.getAndIncrement()), involvedObject));
        eventEntity.putRelationship("trip", tripId);
        return eventEntity;
    }

    default List<Entity> tripListToEnities(String trip, List<?> scenarioList) {

        return scenarioList.stream().map(scenarioObject -> {
            if (scenarioObject instanceof Location) {
                return locationToEntity((Location) scenarioObject);
            } else if (scenarioObject instanceof Shipment) {
                return shipmentToEntity((Shipment) scenarioObject);
            } else if (scenarioObject instanceof Vehicle) {
                return vehicleToEntity((Vehicle) scenarioObject);
            } else if (scenarioObject instanceof Trip) {
                return tripToEntity((Trip) scenarioObject);
            } else if (scenarioObject instanceof Event) {
                return eventToEntity((Event) scenarioObject, trip);
            } else {
                return null;
            }
        }).filter(entity -> entity != null).collect(Collectors.toList());
    }
}
