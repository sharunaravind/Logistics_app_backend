package com.example.service;

import com.example.entity.Address;
import com.google.maps.GeoApiContext;
import com.google.maps.DirectionsApi;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.LatLng;
import com.google.maps.model.TravelMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GoogleDirectionsApiHelperServiceImpl implements DirectionsApiHelperService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDirectionsApiHelperServiceImpl.class);
    private final GeoApiContext geoApiContext;

    @Autowired
    public GoogleDirectionsApiHelperServiceImpl(GeoApiContext geoApiContext) {
        this.geoApiContext = geoApiContext;
    }

    @Override
    public RouteDetails getRouteDetails(Address origin, Address destination, List<Address> waypoints, boolean optimizeWaypoints) {
        if (origin == null || origin.getLocation() == null || origin.getLatitude() == null || origin.getLongitude() == null) {
            log.error("Origin address, its JTS Point location, or its coordinates are null/invalid. Cannot calculate route. Origin: [{}]",
                    origin != null ? origin.getStreet() : "null address object");
            return null;
        }
        if (destination == null || destination.getLocation() == null || destination.getLatitude() == null || destination.getLongitude() == null) {
            log.error("Destination address, its JTS Point location, or its coordinates are null/invalid. Cannot calculate route. Destination: [{}]",
                    destination != null ? destination.getStreet() : "null address object");
            return null;
        }


        LatLng originLatLng = new LatLng(origin.getLatitude(), origin.getLongitude());
        LatLng destinationLatLng = new LatLng(destination.getLatitude(), destination.getLongitude());

        List<LatLng> waypointLatLngs = new ArrayList<>();
        if (waypoints != null) {
            for (Address waypointAddress : waypoints) {
                if (waypointAddress != null && waypointAddress.getLocation() != null && waypointAddress.getLatitude() != null && waypointAddress.getLongitude() != null ) {
                    waypointLatLngs.add(new LatLng(waypointAddress.getLatitude(), waypointAddress.getLongitude()));
                } else {
                    log.warn("A waypoint address or its coordinates are null/invalid, skipping it: {}", waypointAddress != null ? waypointAddress.getStreet() : "null waypoint");
                }
            }
        }

        try {
            DirectionsResult result = DirectionsApi.newRequest(geoApiContext)
                    .origin(originLatLng)
                    .destination(destinationLatLng)
                    .waypoints(waypointLatLngs.toArray(new LatLng[0]))
                    .mode(TravelMode.DRIVING)
                    .optimizeWaypoints(optimizeWaypoints && !waypointLatLngs.isEmpty())
                    .await();

            if (result != null && result.routes != null && result.routes.length > 0) {
                DirectionsRoute route = result.routes[0];
                long totalDurationSeconds = 0;
                long totalDistanceMeters = 0;

                for (DirectionsLeg leg : route.legs) {
                    totalDurationSeconds += leg.duration.inSeconds;
                    totalDistanceMeters += leg.distance.inMeters;
                }
                String polyline = route.overviewPolyline != null ? route.overviewPolyline.getEncodedPath() : null;
                log.debug("Route calculated: Duration={}s, Distance={}m. Origin: {}, Dest: {}, Waypoints: {}",
                        totalDurationSeconds, totalDistanceMeters, originLatLng, destinationLatLng, waypointLatLngs.size());
                return new RouteDetails(totalDurationSeconds, totalDistanceMeters, polyline);
            } else {
                log.warn("No routes found by Google Directions API for Origin: {}, Dest: {}, Waypoints: {}",
                        originLatLng, destinationLatLng, waypointLatLngs.size());
                return null;
            }
        } catch (Exception e) {
            log.error("Error calling Google Directions API: Origin: {}, Dest: {}, Error: {}", originLatLng, destinationLatLng, e.getMessage(), e);
            return null;
        }
    }
}
