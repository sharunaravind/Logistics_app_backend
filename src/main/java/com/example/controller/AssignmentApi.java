// In file: src/main/java/com/example/controller/AssignmentApi.java
package com.example.controller;

import com.example.model.AssignOrdersRequest;
import com.example.model.AssignmentSummaryResponse;
import com.example.model.ErrorResponse;
import com.example.model.PaginatedOrderResponse;
import com.example.model.PaginatedVehicleAssignmentResponse;
import com.example.model.VehicleStatus; // Your enum from com.example.model
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.NativeWebRequest; // For the getRequest() method if your generator includes it

import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.Optional; // For the getRequest() method
import javax.annotation.Generated; // Standard generator annotation

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-05-04T15:16:19.844067600+05:30[Asia/Calcutta]", comments = "Generator version: 7.9.0") // Or your actual generation date
@Validated
@Tag(name = "Assignment", description = "Assigning orders to vehicles and viewing assignments")
public interface AssignmentApi {

    /**
     * Default method to get the native web request, often generated.
     * Your controller implementation doesn't need to override or use this typically.
     */
    default Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    /**
     * POST /api/v1/assign-orders : Assign ready orders to vehicles
     * Triggers the assignment process for orders that are 'ReadyForDispatch', assigning them to available vehicles based on capacity, proximity (using coordinates), and delivery type prioritization.
     *
     * @param assignOrdersRequest  (optional)
     * @return Assignment process completed successfully. (status code 200)
     * or Invalid request or unable to assign orders (e.g., no ready orders, no available vehicles). (status code 400)
     * or Authentication information is missing or invalid. (status code 401)
     */
    @Operation(
            operationId = "assignOrders",
            summary = "Assign ready orders to vehicles",
            description = "Triggers the assignment process for orders that are 'ReadyForDispatch', assigning them to available vehicles based on capacity, proximity (using coordinates), and delivery type prioritization.",
            tags = { "Assignment" },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Assignment process completed successfully.", content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = AssignmentSummaryResponse.class))
                    }),
                    @ApiResponse(responseCode = "400", description = "Invalid request or unable to assign orders (e.g., no ready orders, no available vehicles).", content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    }),
                    @ApiResponse(responseCode = "401", description = "Authentication information is missing or invalid.", content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    })
            },
            security = {
                    @SecurityRequirement(name = "bearerAuth") // As per your OpenAPI spec
            }
    )
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/api/v1/assign-orders",
            produces = { "application/json" },
            consumes = { "application/json" }
    )
    default ResponseEntity<AssignmentSummaryResponse> assignOrders(
            @Parameter(name = "AssignOrdersRequest", description = "Optional parameters for assignment logic") @Valid @RequestBody(required = false) AssignOrdersRequest assignOrdersRequest
    ) {
        // Default implementation from generator often returns NOT_IMPLEMENTED
        // or sets example responses. Your controller provides the real logic.
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }


    /**
     * GET /api/v1/assignments : Get all current vehicle assignments (Paginated)
     * Returns a paginated list showing which orders are currently assigned to which vehicles.
     *
     * @param limit Maximum number of vehicle assignments to return per page. (optional, default to 20)
     * @param offset Number of vehicle assignments to skip for pagination. (optional, default to 0)
     * @param vehicleStatus Filter assignments by vehicle status (e.g., 'In Transit'). (optional)
     * @param registrationNumber Filter assignments for a specific vehicle. (optional)
     * @return Successfully retrieved all assignments. (status code 200)
     * or Invalid query parameters. (status code 400)
     * or Authentication information is missing or invalid. (status code 401)
     */
    @Operation(
            operationId = "getAllAssignments",
            summary = "Get all current vehicle assignments (Paginated)",
            description = "Returns a paginated list showing which orders are currently assigned to which vehicles.",
            tags = { "Assignment" },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved all assignments.", content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = PaginatedVehicleAssignmentResponse.class))
                    }),
                    @ApiResponse(responseCode = "400", description = "Invalid query parameters.", content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    }),
                    @ApiResponse(responseCode = "401", description = "Authentication information is missing or invalid.", content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    })
            },
            security = {
                    @SecurityRequirement(name = "bearerAuth") // As per your OpenAPI spec
            }
    )
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/api/v1/assignments",
            produces = { "application/json" }
    )
    default ResponseEntity<PaginatedVehicleAssignmentResponse> getAllAssignments(
            @Min(1) @Parameter(name = "limit", description = "Maximum number of vehicle assignments to return per page.", in = ParameterIn.QUERY, schema = @Schema(defaultValue = "20")) @Valid @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
            @Min(0) @Parameter(name = "offset", description = "Number of vehicle assignments to skip for pagination.", in = ParameterIn.QUERY, schema = @Schema(defaultValue = "0")) @Valid @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
            @Parameter(name = "vehicleStatus", description = "Filter assignments by vehicle status (e.g., 'In Transit').", in = ParameterIn.QUERY) @Valid @RequestParam(value = "vehicleStatus", required = false) VehicleStatus vehicleStatus, // Correctly typed as enum
            @Parameter(name = "registrationNumber", description = "Filter assignments for a specific vehicle.", in = ParameterIn.QUERY) @Valid @RequestParam(value = "registrationNumber", required = false) String registrationNumber
    ) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }


    /**
     * GET /api/v1/vehicles/{registrationNumber}/assignments : Get assignments for a specific vehicle (Paginated Orders)
     * Returns the list of orders currently assigned to the specified vehicle, paginated.
     *
     * @param registrationNumber Registration number of the vehicle. (required)
     * @param limit Maximum number of assigned orders to return. (optional, default to 20)
     * @param offset Number of assigned orders to skip. (optional, default to 0)
     * @return Successfully retrieved the vehicle's assignments. (status code 200)
     * or Authentication information is missing or invalid. (status code 401)
     * or The specified resource was not found. (status code 404)
     */
    @Operation(
            operationId = "getAssignmentsByVehicle",
            summary = "Get assignments for a specific vehicle (Paginated Orders)",
            description = "Returns the list of orders currently assigned to the specified vehicle, paginated.",
            tags = { "Assignment" },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved the vehicle's assignments.", content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = PaginatedOrderResponse.class))
                    }),
                    @ApiResponse(responseCode = "401", description = "Authentication information is missing or invalid.", content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    }),
                    @ApiResponse(responseCode = "404", description = "The specified resource was not found.", content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    })
            },
            security = {
                    @SecurityRequirement(name = "bearerAuth") // As per your OpenAPI spec
            }
    )
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/api/v1/vehicles/{registrationNumber}/assignments",
            produces = { "application/json" }
    )
    default ResponseEntity<PaginatedOrderResponse> getAssignmentsByVehicle(
            @Parameter(name = "registrationNumber", description = "Registration number of the vehicle.", required = true, in = ParameterIn.PATH) @PathVariable("registrationNumber") String registrationNumber,
            @Min(1) @Parameter(name = "limit", description = "Maximum number of assigned orders to return.", in = ParameterIn.QUERY, schema = @Schema(defaultValue = "20")) @Valid @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
            @Min(0) @Parameter(name = "offset", description = "Number of assigned orders to skip.", in = ParameterIn.QUERY, schema = @Schema(defaultValue = "0")) @Valid @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset
    ) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}