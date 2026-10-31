package com.example.propertyview.controller;

import com.example.propertyview.dto.HotelDtos.*;
import com.example.propertyview.service.HotelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.List;
import java.util.Map;

/**
 * Handles HTTP requests for the hotel catalog.
 * Delegates application logic to HotelService.
 */
@RestController
@Tag(name = "Hotels", description = "Hotel catalog API")
public class HotelController {

    // Service responsible for hotel operations.
    private final HotelService service;

    /**
     * Injects the hotel service through the constructor.
     */
    public HotelController(HotelService service) {
        this.service = service;
    }

    /**
     * Returns short information about all hotels.
     */
    @GetMapping("/hotels")
    @Operation(summary = "Get short information about all hotels")
    public List<HotelSummary> findAll() {
        return service.findAll();
    }

    /**
     * Returns detailed information about the hotel with the given ID.
     */
    @GetMapping("/hotels/{id}")
    @Operation(summary = "Get full information about a hotel")
    public HotelDetails findById(@PathVariable Long id) {
        return service.findById(id);
    }

    /**
     * Searches hotels using optional query parameters.
     * All non-empty filters must match, including every requested amenity.
     */
    @GetMapping("/search")
    @Operation(summary = "Search hotels",
            description = "Case-insensitive AND filters. Name is a substring; other values are exact. "
                    + "All requested amenities must be present. Empty filters are ignored.")
    public List<HotelSummary> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @Parameter(description = "Repeat the parameter or use comma-separated values")
            @RequestParam(required = false) List<String> amenities) {

        // Collect the query parameters into a search criteria object.
        return service.search(new SearchCriteria(name, brand, city, country, amenities));
    }

    /**
     * Validates the request body and creates a hotel.
     * Returns HTTP 201 with the hotel summary and a Location header.
     */
    @PostMapping("/hotels")
    @Operation(summary = "Create a hotel", description = "Description and checkOut are optional.")
    public ResponseEntity<HotelSummary> create(@Valid @RequestBody CreateHotelRequest request) {
        var created = service.create(request);

        // Build the URL of the newly created hotel using its generated ID.
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();

        return ResponseEntity.created(location).body(created);
    }

    /**
     * Adds amenities to a hotel without replacing existing values.
     * Accepts up to 200 non-blank strings, each at most 255 characters long.
     */
    @PostMapping("/hotels/{id}/amenities")
    @Operation(summary = "Add amenities without replacing existing values",
            description = "Duplicates are ignored case-insensitively; an empty list is a no-op.")
    public ResponseEntity<Void> addAmenities(
            @PathVariable Long id,
            @RequestBody @Size(max = 200)
            List<@NotBlank @Size(max = 255) String> amenities) {

        service.addAmenities(id, amenities);

        // Return HTTP 204 without a response body.
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns hotel counts grouped by the requested attribute.
     * Supported attributes are brand, city, country and amenities.
     */
    @GetMapping("/histogram/{param}")
    @Operation(summary = "Count hotels grouped by brand, city, country or amenities")
    public Map<String, Long> histogram(@PathVariable String param) {
        return service.histogram(param);
    }
}