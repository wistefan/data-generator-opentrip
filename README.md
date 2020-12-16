# Data generator

Intended to generate a small scenario at the ngsi-ld api.

## Scenario

> Banana Company ships a container to its customers storage at the port.

Steps: 
1.  everything is planned - scenario offset hours < 0
2. load container to truck at banana companies location - scenario offset hours = 0
3. start driving to the port - scenario offset hours = 1
4. stop driving after arrival at port - scenario offset hours = 8
5. customer receives bananas at port - scenario offset hours = 9
6. container is unloaded from the truck at port - scenario offset hours = 10
7. everything is realized - scenario offset hours > 10

## API

The generator provides a very easy api.

> **GET /trip{?hourOffset}** -  returns the data of the whole trip at the given offset in the ngsi-ld data format

> **POST /trip{?hourOffset}** - creates the trip at the given offset in the connected broker instance

> **DELETE /trip** - deletes the trip at the connected broker instance

Implementation of POST is done very trivial, if an entity already exists it is deleted and newly created(therefore the endpoint is slow - ~15s response time are normal). Therefore its not suitable for brokers that 
already support the temporal inteface.  
 
 ## Usage
 
 Connect locally to the broker instance, f.e. via port-forwarding:
```
kubectl port-forward <POD_NAME> 8026:1026 -n fiware
```
Start the generator:
```
docker run --network host  -p 8080:8080  wistefan/data-generator-opentrip
```
Create the scenario at start offset:
```
curl -X POST http://localhost:8080/trip?hourOffset=0
```
Get the 'stopMovingEvent' and it phase and timestamp, the phase will be 'planned':
```
curl -X GET 'http://localhost:8026/ngsi-ld/v1/entities?type=event&q=(eventType==%22stopMovingEvent%22);(involvedObject0==%22urn:ngsi-ld:vehicle:banana-truck-id%22)'
```
Update scenario to offset 8:
```
curl -X POST http://localhost:8080/trip?hourOffset=8
```
Get the 'stopMovingEvent' and it phase and timestamp, the phase will be 'actual':
```
curl -X GET 'http://localhost:8026/ngsi-ld/v1/entities?type=event&q=(eventType==%22stopMovingEvent%22);(involvedObject0==%22urn:ngsi-ld:vehicle:banana-truck-id%22)'
```
Update scenario to offset >8:
```
curl -X POST http://localhost:8080/trip?hourOffset=9
```
Get the 'stopMovingEvent' and it phase and timestamp, the phase will be 'realized':
```
curl -X GET 'http://localhost:8026/ngsi-ld/v1/entities?type=event&q=(eventType==%22stopMovingEvent%22);(involvedObject0==%22urn:ngsi-ld:vehicle:banana-truck-id%22)'
```