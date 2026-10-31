package com.example.propertyview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * Groups request and response models used by the hotel API.
 * These models are independent of persistence entities.
 */
public final class HotelDtos {

    // Prevent instantiation of this utility container.
    private HotelDtos() {}

    /**
     * Represents a hotel's structured address.
     * The house number must be positive, and all text fields must be non-blank.
     * The postal code is stored as text to preserve leading zeros.
     */
    public record Address(
            @NotNull @Positive Integer houseNumber,
            @NotBlank @Size(max = 255) String street,
            @NotBlank @Size(max = 255) String city,
            @NotBlank @Size(max = 255) String country,
            @NotBlank @Size(max = 32) String postCode) {}

    /**
     * Represents a hotel's contact information.
     * Both fields are required, and the email must pass email validation.
     */
    public record Contacts(
            @NotBlank @Size(max = 64) String phone,
            @NotBlank @Email @Size(max = 254) String email) {}

    /**
     * Represents check-in and check-out times in 24-hour HH:mm format.
     * Check-in is required; check-out may be null.
     * Any provided time must be between 00:00 and 23:59.
     */
    public record ArrivalTime(
            @NotBlank @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d",
                    message = "must be a time in HH:mm format") String checkIn,
            @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d",
                    message = "must be a time in HH:mm format") String checkOut) {}

    /**
     * Defines the request body for creating a hotel.
     * Description is optional.
     * Address, contacts and arrival time are required and validated recursively.
     */
    public record CreateHotelRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 10000) String description,
            @NotBlank @Size(max = 255) String brand,
            @NotNull @Valid Address address,
            @NotNull @Valid Contacts contacts,
            @NotNull @Valid ArrivalTime arrivalTime) {}

    /**
     * Provides a compact hotel representation for list and search responses.
     * The address is returned as a formatted string.
     */
    public record HotelSummary(Long id, String name, String description,
                               String address, String phone) {}

    /**
     * Provides detailed hotel information, including structured address,
     * contact details, arrival times and amenities.
     */
    public record HotelDetails(Long id, String name, String description, String brand,
                               Address address, Contacts contacts, ArrivalTime arrivalTime,
                               List<String> amenities) {}

    /**
     * Groups optional hotel search filters.
     * Filtering rules are applied by the service and repository layers.
     */
    public record SearchCriteria(String name, String brand, String city,
                                 String country, List<String> amenities) {}
}