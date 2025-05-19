package com.example.controller;

import com.example.model.AssignOrdersRequest;
import com.example.model.AssignmentSummaryResponse;
import com.example.service.AssignmentService;
import com.example.model.PaginatedVehicleAssignmentResponse;
// Removed VehicleStatus import as String is passed
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid; // For @Valid on requestBody
import javax.validation.constraints.Min;

@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:5174"}) // Added your React dev port
@RestController
@RequestMapping("/api/v1")
@Validated
public class AssignmentController {

    private final AssignmentService assignmentService;

    @Autowired
    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping("/assign-orders")
    public ResponseEntity<AssignmentSummaryResponse> assignOrders(
            @Valid @RequestBody(required = false) AssignOrdersRequest assignOrdersRequest) {
        AssignmentSummaryResponse summary = assignmentService.performBatchAssignment(assignOrdersRequest);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/assignments")
    public ResponseEntity<PaginatedVehicleAssignmentResponse> getAllAssignments(
            @RequestParam(value = "limit", defaultValue = "20") @Min(1) int limit,
            @RequestParam(value = "offset", defaultValue = "0") @Min(0) int offset,
            @RequestParam(value = "vehicleStatus", required = false) String vehicleStatus,
            @RequestParam(value = "registrationNumber", required = false) String registrationNumber,
            @RequestParam(value = "sortBy", defaultValue = "vehicleIdentifier.registrationNumber") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "asc") String sortDirection
    ) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(direction, sortBy));

        PaginatedVehicleAssignmentResponse assignments = assignmentService.getGroupedAssignments(pageable, vehicleStatus, registrationNumber);
        return ResponseEntity.ok(assignments);
    }
}
