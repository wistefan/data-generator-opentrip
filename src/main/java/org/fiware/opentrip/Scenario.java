package org.fiware.opentrip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.uri.UriBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fiware.opentrip.mapping.OpentripNgsiMapper;
import org.fiware.opentrip.model.*;
import org.fiware.opentrip.ngsi.Entity;
import org.threeten.bp.Instant;
import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.ZoneOffset;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@Slf4j
public class Scenario {


    private final URL orionURL;
    private final OpentripNgsiMapper opentripNgsiMapper;
    private final RxHttpClient httpClient;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Long DEFAULT_START_TIME = OffsetDateTime.of(2020, 12, 12, 12, 00, 00, 00, ZoneOffset.UTC).toEpochSecond();
    private static final String ID_TEMPLATE = "urn:ngsi-ld:%s:%s";

    public Scenario(@Value("${orionURL}") URL orionURL, OpentripNgsiMapper opentripNgsiMapper, RxHttpClient httpClient) {
        this.orionURL = orionURL;
        this.opentripNgsiMapper = opentripNgsiMapper;
        this.httpClient = httpClient;
    }

    @Get("/trip{?startTime*}")
    public HttpResponse<String> getScenarioAsNgsi(Optional<Long> startTime) throws JsonProcessingException {

        List<Entity> entityList = generateTripScenario(startTime.orElseGet(() -> DEFAULT_START_TIME))
                .entrySet().stream()
                .flatMap(entry -> opentripNgsiMapper.tripListToEnities(entry.getKey(), entry.getValue()).stream())
                .collect(Collectors.toList());

        return HttpResponse.ok(OBJECT_MAPPER.writeValueAsString(entityList));
    }

    // if an entity already exists, it will be deleted and recreated as an easy temp-solution for updating
    // TODO: do updates when temporal is implemented
    @Post("/trip{?startTime*}")
    public void createScenarioAtOrion(Optional<Long> startTime) {
        List<Entity> entityList = generateTripScenario(startTime.orElseGet(() -> DEFAULT_START_TIME))
                .entrySet().stream()
                .flatMap(entry -> opentripNgsiMapper.tripListToEnities(entry.getKey(), entry.getValue()).stream())
                .collect(Collectors.toList());

        entityList.forEach(entity -> {
            try {
                try {
                    HttpResponse response = postEntity(entity);
                    if (response.code() > 299 || response.code() < 200) {
                        log.warn("Was not able to create entity {}. Response was {}", entity, response);
                    }
                } catch (HttpClientResponseException e) {
                    if (e.getStatus() == HttpStatus.CONFLICT) {
                        log.info("Entity {} already exists. Will delete and rewrite.", entity);
                        deleteEntity(entity);
                        postEntity(entity);
                    } else {
                        log.error("Was not able to create entity {}", entity, e);
                    }
                } catch (ReadTimeoutException exception) {
                    // continue, even in case of timeout.
                    // might happen, since we use the rx client in a blocking fashion.
                }
            } catch (JsonProcessingException | URISyntaxException e) {
                log.error("Was not able to create entity {}", entity, e);
            }
        });
    }

    @Delete("/trip")
    public void deleteScenario() {
        generateTripScenario(DEFAULT_START_TIME)
                .entrySet().stream()
                .flatMap(entry -> opentripNgsiMapper.tripListToEnities(entry.getKey(), entry.getValue()).stream())
                .collect(Collectors.toList())
                .forEach(entity -> {
                    try {
                        deleteEntity(entity);
                    } catch (Exception e) {
                        // continue always
                    }
                });

    }

    private HttpResponse deleteEntity(Entity entity) throws URISyntaxException {
        URI requestURI = UriBuilder.of(orionURL.toURI()).path(String.format("/ngsi-ld/v1/entities/%s", entity.getId())).build();
        return httpClient.exchange(HttpRequest.DELETE(requestURI.toString()).contentType("application/ld+json")).blockingFirst();
    }

    private HttpResponse postEntity(Entity entity) throws JsonProcessingException, URISyntaxException {
        URI requestURI = UriBuilder.of(orionURL.toURI()).path("/ngsi-ld/v1/entities").build();
        return httpClient.exchange(HttpRequest.POST(requestURI, OBJECT_MAPPER.writeValueAsString(entity)).contentType("application/ld+json")).blockingFirst();
    }

    /*
     * Banana Company ships a container to its customers storage at the port:
     * -> load truck at banana companies
     * -> start driving to the port
     * --- current time ---
     * -> stop driving on arrival
     * -> customer receives bananas at port
     * -> truck is unloaded at port
     */
    private Map<String, List<?>> generateTripScenario(long tripStartEpoch) {
        OffsetDateTime tripStartTime = Instant.ofEpochSecond(tripStartEpoch).atOffset(ZoneOffset.UTC);

        List scenarioElements = new ArrayList<>();

        Location bananaCompany = new Location()
                .id(String.format(ID_TEMPLATE, Location.class.getSimpleName().toLowerCase(), "banana-company-id"))
                .name("The Banana Company")
                .type(new LocationType().other("shipper"));
        scenarioElements.add(bananaCompany);
        Location portStorage = new Location()
                .id(String.format(ID_TEMPLATE, Location.class.getSimpleName().toLowerCase(), "port-storage-id"))
                .name("Port storage")
                .type(new LocationType().type(LocationType.TypeEnum.WAREHOUSE));
        scenarioElements.add(portStorage);
        Location bananaCustomer = new Location()
                .id(String.format(ID_TEMPLATE, Location.class.getSimpleName().toLowerCase(), "banana-customer-id"))
                .name("Banana Customer")
                .type(new LocationType().type(LocationType.TypeEnum.CUSTOMER));
        scenarioElements.add(bananaCustomer);
        Shipment bananaShipment = new Shipment()
                .physicalSender(bananaCompany)
                .legalSender(bananaCompany)
                .physicalAddressee(portStorage)
                .legalAddressee(bananaCustomer)
                .type("Container")
                .id(String.format(ID_TEMPLATE, Shipment.class.getSimpleName().toLowerCase(), "banana-shipment-id"));
        scenarioElements.add(bananaShipment);
        Vehicle bananaTruck = new Vehicle()
                .id(String.format(ID_TEMPLATE, Vehicle.class.getSimpleName().toLowerCase(), "banana-truck-id"))
                .name("Banana Truck")
                .vehicleType(new VehicleType().type(VehicleType.TypeEnum.BOXTRUCK));
        scenarioElements.add(bananaTruck);
        Trip bananaTransportTrip = new Trip()
                .id(String.format(ID_TEMPLATE, Trip.class.getSimpleName().toLowerCase(), "transport-trip-id"))
                .name("Banana Company to Port Storage");
        scenarioElements.add(bananaTransportTrip);
        Event loadingEvent = new Event()
                .id(String.format(ID_TEMPLATE, Event.class.getSimpleName().toLowerCase(), "loading-event-id"))
                .involvedObjects(List.of(bananaShipment.getId(), bananaTruck.getId()))
                .lifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.REALIZED))
                .eventGenerationTime(tripStartTime)
                .type("loadShipmentEvent");
        scenarioElements.add(loadingEvent);
        Event unloadingEvent = new Event()
                .id(String.format(ID_TEMPLATE, Event.class.getSimpleName().toLowerCase(), "unloading-event-id"))
                .involvedObjects(List.of(bananaShipment.getId(), bananaTruck.getId()))
                .lifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.PLANNED))
                .eventGenerationTime(tripStartTime.plusDays(1).plusHours(2))
                .type("unloadShipmentEvent");
        scenarioElements.add(unloadingEvent);
        Event receiveBananasEvent = new Event()
                .id(String.format(ID_TEMPLATE, Event.class.getSimpleName().toLowerCase(), "receive-bananas-event"))
                .involvedObjects(List.of(portStorage.getId(), bananaShipment.getId()))
                .lifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.PLANNED))
                .eventGenerationTime(tripStartTime.plusDays(1).plusHours(1))
                .type("receiveShipmentEvent");
        scenarioElements.add(receiveBananasEvent);
        Event startDrivingEvent = new Event()
                .id(String.format(ID_TEMPLATE, Event.class.getSimpleName().toLowerCase(), "start-driving-event"))
                .involvedObjects(List.of(bananaTruck.getId()))
                .lifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.REALIZED))
                .eventGenerationTime(tripStartTime.plusMinutes(30))
                .type("startMovingEvent");
        scenarioElements.add(startDrivingEvent);
        Event stopDrivingEvent = new Event()
                .id(String.format(ID_TEMPLATE, Event.class.getSimpleName().toLowerCase(), "stop-driving-event"))
                .involvedObjects(List.of(bananaTruck.getId()))
                .lifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.PLANNED))
                .eventGenerationTime(tripStartTime.plusDays(1).plusMinutes(30))
                .type("stopMovingEvent");
        scenarioElements.add(stopDrivingEvent);

        return Map.of(bananaTransportTrip.getId(), scenarioElements);
    }
}
