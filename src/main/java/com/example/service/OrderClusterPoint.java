package com.example.service;

import com.example.entity.Order;
import org.apache.commons.math3.ml.clustering.Clusterable;

public class OrderClusterPoint implements Clusterable {
    private final Order order;
    private final double[] point;

    public OrderClusterPoint(Order order) {
        this.order = order;

        this.point = new double[] {
                order.getCustomer().getDeliveryAddress().getLatitude(),
                order.getCustomer().getDeliveryAddress().getLongitude()
        };
    }

    public Order getOrder() {
        return order;
    }

    @Override
    public double[] getPoint() {
        return this.point;
    }
}