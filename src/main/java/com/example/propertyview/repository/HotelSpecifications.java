package com.example.propertyview.repository;

import com.example.propertyview.dto.HotelDtos.SearchCriteria;
import com.example.propertyview.entity.Hotel;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Builds hotel search predicates from optional filters.
 * All active filters, including each requested amenity, are combined using AND.
 */
public final class HotelSpecifications {

    // Prevent instantiation of this utility class.
    private HotelSpecifications() {}

    /**
     * Creates a specification for case-insensitive hotel searches.
     * Null and blank filter values are ignored.
     *
     * @param criteria the search filters to apply
     * @return a specification combining all active search conditions
     */
    public static Specification<Hotel> matching(SearchCriteria criteria) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            // Search for a substring in the hotel name.
            if (hasText(criteria.name())) {
                // Escape LIKE special characters so user input is matched literally.
                String escaped = normalize(criteria.name()).replace("\\", "\\\\")
                        .replace("%", "\\%").replace("_", "\\_");

                predicates.add(cb.like(
                        cb.lower(root.get("name")), "%" + escaped + "%", '\\'));
            }

            // Match the complete brand value, ignoring case.
            if (hasText(criteria.brand())) {
                predicates.add(cb.equal(
                        cb.lower(root.get("brand")), normalize(criteria.brand())));
            }

            // Match the complete city value, ignoring case.
            if (hasText(criteria.city())) {
                predicates.add(cb.equal(
                        cb.lower(root.get("city")), normalize(criteria.city())));
            }

            // Match the complete country value, ignoring case.
            if (hasText(criteria.country())) {
                predicates.add(cb.equal(
                        cb.lower(root.get("country")), normalize(criteria.country())));
            }

            if (criteria.amenities() != null) {
                // Ignore blank amenities and remove duplicates after normalization.
                criteria.amenities().stream()
                        .filter(HotelSpecifications::hasText)
                        .map(HotelSpecifications::normalize)
                        .distinct()
                        .forEach(amenity -> {
                            // Correlate the subquery with the hotel in the outer query.
                            var subquery = query.subquery(Long.class);
                            var correlatedHotel = subquery.correlate(root);
                            var amenityJoin =
                                    correlatedHotel.<Hotel, String>joinSet("amenities");

                            // Check whether this hotel has the requested amenity.
                            subquery.select(cb.literal(1L))
                                    .where(cb.equal(cb.lower(amenityJoin), amenity));

                            // A separate EXISTS condition requires each requested amenity
                            // without multiplying rows in the outer query.
                            predicates.add(cb.exists(subquery));
                        });
            }

            // With no active filters, the empty conjunction matches all hotels.
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Checks whether a value is non-null and contains non-whitespace characters.
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Trims surrounding whitespace and lowercases using a locale-neutral rule.
     */
    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}