package com.example.propertyview.exception;

/**
 * Thrown when a hotel with the specified ID cannot be found.
 */
public class HotelNotFoundException extends RuntimeException {

    // Identifies the version of this class for Java serialization.
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message containing the missing hotel's ID.
     *
     * @param id the ID of the hotel that was not found
     */
    public HotelNotFoundException(Long id) {
        super("Hotel with id " + id + " was not found");
    }
}