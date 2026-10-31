package com.example.propertyview.mapper;

import com.example.propertyview.dto.HotelDtos.*;
import com.example.propertyview.entity.Hotel;
import org.springframework.stereotype.Component;

/**
 * Converts hotel API models to persistence entities and entities to response models.
 */
@Component
public class HotelMapper {

    /**
     * Creates a hotel entity from a creation request.
     * Trims surrounding whitespace from selected text fields.
     *
     * @param request the validated hotel creation request
     * @return a new hotel entity without a generated ID
     */
    public Hotel toEntity(CreateHotelRequest request) {
        Hotel hotel = new Hotel();

        // Copy general hotel information.
        hotel.setName(request.name().trim());
        hotel.setDescription(request.description());
        hotel.setBrand(request.brand().trim());

        // Flatten the structured address into entity fields.
        hotel.setHouseNumber(request.address().houseNumber());
        hotel.setStreet(request.address().street().trim());
        hotel.setCity(request.address().city().trim());
        hotel.setCountry(request.address().country().trim());
        hotel.setPostCode(request.address().postCode().trim());

        // Copy contact information and remove surrounding whitespace.
        hotel.setPhone(request.contacts().phone().trim());
        hotel.setEmail(request.contacts().email().trim());

        // Copy arrival times, preserving a null check-out value if provided.
        hotel.setCheckIn(request.arrivalTime().checkIn());
        hotel.setCheckOut(request.arrivalTime().checkOut());

        return hotel;
    }

    /**
     * Converts a hotel entity to a compact response for list and search results.
     *
     * @param hotel the hotel entity to convert
     * @return a summary containing a formatted address and phone number
     */
    public HotelSummary toSummary(Hotel hotel) {
        // Format the address as: house number, street, city, postal code and country.
        String address = "%d %s, %s, %s, %s".formatted(hotel.getHouseNumber(),
                hotel.getStreet(), hotel.getCity(), hotel.getPostCode(), hotel.getCountry());

        return new HotelSummary(hotel.getId(), hotel.getName(), hotel.getDescription(),
                address, hotel.getPhone());
    }

    /**
     * Converts a hotel entity to a detailed response with nested API models.
     *
     * @param hotel the hotel entity to convert
     * @return full hotel information with a sorted list of amenities
     */
    public HotelDetails toDetails(Hotel hotel) {
        return new HotelDetails(hotel.getId(), hotel.getName(), hotel.getDescription(),
                hotel.getBrand(),

                // Reconstruct the structured address from entity fields.
                new Address(hotel.getHouseNumber(), hotel.getStreet(), hotel.getCity(),
                        hotel.getCountry(), hotel.getPostCode()),

                // Group contact information and arrival times into nested models.
                new Contacts(hotel.getPhone(), hotel.getEmail()),
                new ArrivalTime(hotel.getCheckIn(), hotel.getCheckOut()),

                // Return amenities sorted by their natural string order.
                hotel.getAmenities().stream().sorted().toList());
    }
}