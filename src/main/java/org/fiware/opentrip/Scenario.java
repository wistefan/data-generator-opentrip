package org.fiware.opentrip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.BlockingHttpClient;
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
import org.threeten.bp.temporal.ChronoUnit;
import org.threeten.bp.temporal.TemporalUnit;

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
    private final BlockingHttpClient httpClient;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final OffsetDateTime DEFAULT_START_TIME = OffsetDateTime.of(2020, 12, 12, 12, 00, 00, 00, ZoneOffset.UTC);
    private static final String ID_TEMPLATE = "urn:ngsi-ld:%s:%s";

    public Scenario(@Value("${orionURL}") URL orionURL, OpentripNgsiMapper opentripNgsiMapper, BlockingHttpClient httpClient) {
        this.orionURL = orionURL;
        this.opentripNgsiMapper = opentripNgsiMapper;
        this.httpClient = httpClient;
    }

    @Get("/trip")
    public HttpResponse<String> getScenarioAsNgsi(@QueryValue("hourOffset") Optional<Integer> hourOffset) throws JsonProcessingException {
        List<Entity> entityList = generateTripScenario(hourOffset.orElse(0))
                .entrySet().stream()
                .flatMap(entry -> opentripNgsiMapper.tripListToEnities(entry.getKey(), entry.getValue()).stream())
                .collect(Collectors.toList());

        return HttpResponse.ok(OBJECT_MAPPER.writeValueAsString(entityList));
    }

    // if an entity already exists, it will be deleted and recreated as an easy temp-solution for updating
    // TODO: do updates when temporal is implemented
    @Post("/trip")
    public void createScenarioAtOrion(@QueryValue("hourOffset") Optional<Integer> hourOffset) {
        List<Entity> entityList = generateTripScenario(hourOffset.orElse(0))
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
        generateTripScenario(0)
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
        return httpClient.exchange(HttpRequest.DELETE(requestURI.toString()).contentType("application/ld+json"));
    }

    private HttpResponse postEntity(Entity entity) throws JsonProcessingException, URISyntaxException {
        URI requestURI = UriBuilder.of(orionURL.toURI()).path("/ngsi-ld/v1/entities").build();
        return httpClient.exchange(HttpRequest.POST(requestURI, OBJECT_MAPPER.writeValueAsString(entity)).contentType("application/ld+json"));
    }

    /*
     * Banana Company ships a container to its customers storage at the port:
     * -> everything planned - offeset < 0
     * -> load truck at banana companies - offset 0
     * -> start driving to the port - offset 1
     * -> stop driving on arrival - offset 8
     * -> customer receives bananas at port - offset 9
     * -> truck is unloaded at port - offset 10
     * -> everything realized - offset > 10
     */
    private Map<String, List<?>> generateTripScenario(int hourOffset) {
        OffsetDateTime tripStartTime = DEFAULT_START_TIME;
        OffsetDateTime currentTime = DEFAULT_START_TIME.plus(hourOffset, ChronoUnit.HOURS);

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
                .location(bananaCompany.getId())
                .time(tripStartTime)
                .type("loadShipmentEvent");
        setLifecyclePhase(loadingEvent, currentTime);
        scenarioElements.add(loadingEvent);
        Event unloadingEvent = new Event()
                .id(String.format(ID_TEMPLATE, Event.class.getSimpleName().toLowerCase(), "unloading-event-id"))
                .involvedObjects(List.of(bananaShipment.getId(), bananaTruck.getId()))
                .location(portStorage.getId())
                .lifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.PLANNED))
                .time(tripStartTime.plusHours(10))
                .type("unloadShipmentEvent");
        setLifecyclePhase(unloadingEvent, currentTime);
        scenarioElements.add(unloadingEvent);
        Event receiveBananasEvent = new Event()
                .id(String.format(ID_TEMPLATE, Event.class.getSimpleName().toLowerCase(), "receive-bananas-event"))
                .location(portStorage.getId())
                .involvedObjects(List.of(portStorage.getId(), bananaShipment.getId()))
                .lifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.PLANNED))
                .time(tripStartTime.plusHours(9))
                .type("receiveShipmentEvent");
        setLifecyclePhase(receiveBananasEvent, currentTime);
        scenarioElements.add(receiveBananasEvent);
        Event startDrivingEvent = new Event()
                .id(String.format(ID_TEMPLATE, Event.class.getSimpleName().toLowerCase(), "start-driving-event"))
                .involvedObjects(List.of(bananaTruck.getId()))
                .location(bananaCompany.getId())
                .lifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.REALIZED))
                .time(tripStartTime.plusHours(1))
                .type("startMovingEvent");
        setLifecyclePhase(startDrivingEvent, currentTime);
        scenarioElements.add(startDrivingEvent);
        Event stopDrivingEvent = new Event()
                .id(String.format(ID_TEMPLATE, Event.class.getSimpleName().toLowerCase(), "stop-driving-event"))
                .involvedObjects(List.of(bananaTruck.getId()))
                .location(portStorage.getId())
                .lifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.PLANNED))
                .time(tripStartTime.plusHours(8))
                .type("stopMovingEvent");
        setLifecyclePhase(stopDrivingEvent, currentTime);
        scenarioElements.add(stopDrivingEvent);

        return Map.of(bananaTransportTrip.getId(), scenarioElements);
    }

    private void setLifecyclePhase(Event event, OffsetDateTime currentTime) {
        if(event.getTime().isBefore(currentTime)) {
            event.setLifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.REALIZED));
        } else if (event.getTime().isEqual(currentTime)) {
            event.setLifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.ACTUAL));
        } else if(event.getTime().isAfter(currentTime)) {
            event.setLifecyclePhase(new LifecyclePhase().phase(LifecyclePhase.PhaseEnum.PLANNED));
        }
    }
}
