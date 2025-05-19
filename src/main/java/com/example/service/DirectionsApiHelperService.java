package com.example.service;

import com.example.entity.Address; // Your Address entity with lat/lon
import java.util.List;

public interface DirectionsApiHelperService {

    // Represents the result of a directions API call for a route segment or full route
    class RouteDetails {
        public final long durationSeconds; // Travel time in seconds
        public final long distanceMeters;  // Distance in meters
        public final String polyline;      // Encoded polyline for the route path
        // You can add more fields if needed, e.g., List<LatLng> waypointsInOrder

        public RouteDetails(long durationSeconds, long distanceMeters, String polyline) {
            this.durationSeconds = durationSeconds;
            this.distanceMeters = distanceMeters;
            this.polyline = polyline;
        }
    }

    /**
     * Calculates route details (duration, distance, polyline) between an origin and destination,
     * potentially via a list of waypoints.
     *
     * @param origin      The starting address.
     * @param destination The ending address.
     * @param waypoints   A list of intermediate addresses (orders to be delivered), in desired sequence. Can be null or empty.
     * @param optimizeWaypoints If true and waypoints are provided, the API will attempt to reorder them optimally.
     * @return RouteDetails object, or null if routing fails.
     */
    RouteDetails getRouteDetails(Address origin, Address destination, List<Address> waypoints, boolean optimizeWaypoints);
}