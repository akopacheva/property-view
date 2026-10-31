package com.example.propertyview.service;

import com.example.propertyview.dto.HotelDtos.*;
import com.example.propertyview.exception.HotelNotFoundException;
import com.example.propertyview.mapper.HotelMapper;
import com.example.propertyview.repository.HotelRepository;
import com.example.propertyview.repository.HotelSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements hotel catalog operations and manages transaction boundaries.
 * Uses read-only transactions by default; write operations override this setting.
 */
@Service
@Transactional(readOnly = true)
public class HotelService {

    private final HotelRepository repository;
    private final HotelMapper mapper;

    /**
     * Injects the repository and mapper used by hotel operations.
     */
    public HotelService(HotelRepository repository, HotelMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Returns hotel summaries sorted by ID in ascending order.
     */
    public List<HotelSummary> findAll() {
        return repository.findAll(Sort.by("id"))
                .stream()
                .map(mapper::toSummary)
                .toList();
    }

    /**
     * Returns detailed information about a hotel.
     *
     * @param id the ID of the requested hotel
     * @return the hotel details
     * @throws HotelNotFoundException if the hotel does not exist
     */
    public HotelDetails findById(Long id) {
        return mapper.toDetails(repository.findById(id)
                .orElseThrow(() -> new HotelNotFoundException(id)));
    }

    /**
     * Applies dynamic search filters and returns summaries sorted by ID.
     */
    public List<HotelSummary> search(SearchCriteria criteria) {
        return repository.findAll(HotelSpecifications.matching(criteria), Sort.by("id"))
                .stream()
                .map(mapper::toSummary)
                .toList();
    }

    /**
     * Converts the request to an entity, saves it and returns its summary.
     * Overrides the default read-only transaction setting.
     */
    @Transactional
    public HotelSummary create(CreateHotelRequest request) {
        return mapper.toSummary(repository.save(mapper.toEntity(request)));
    }

    /**
     * Adds amenities without replacing existing values.
     * Trims surrounding whitespace and ignores case-insensitive duplicates.
     *
     * @param id the ID of the hotel to update
     * @param amenities the validated amenities to add
     * @throws HotelNotFoundException if the hotel does not exist
     */
    @Transactional
    public void addAmenities(Long id, List<String> amenities) {
        // Serialize updates to the same hotel's collection to avoid lost additions.
        var hotel = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new HotelNotFoundException(id));

        for (String value : amenities) {
            String normalized = value.trim();

            // Check existing values without changing their stored capitalization.
            boolean exists = hotel.getAmenities().stream()
                    .anyMatch(existing -> existing.equalsIgnoreCase(normalized));

            if (!exists) {
                hotel.getAmenities().add(normalized);
            }
        }

        // Changes to the managed entity are persisted when the transaction commits.
    }

    /**
     * Returns hotel counts grouped by the requested attribute.
     *
     * @param param the grouping attribute: brand, city, country or amenities
     * @return a map of grouping values to hotel counts
     * @throws IllegalArgumentException if the grouping attribute is unsupported
     */
    public Map<String, Long> histogram(String param) {
        // Select the database aggregation query for the requested attribute.
        var rows = switch (param) {
            case "brand" -> repository.countByBrand();
            case "city" -> repository.countByCity();
            case "country" -> repository.countByCountry();
            case "amenities" -> repository.countByAmenity();
            default -> throw new IllegalArgumentException(
                    "Unsupported histogram parameter. Allowed: brand, city, country, amenities");
        };

        // Preserve the order of the rows returned by the repository.
        Map<String, Long> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(row.getValue(), row.getTotal()));

        return result;
    }
}