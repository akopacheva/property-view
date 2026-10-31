package com.example.propertyview.repository;

import com.example.propertyview.entity.Hotel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/**
 * Provides persistence operations for hotel entities.
 * JpaRepository supplies standard CRUD operations.
 * JpaSpecificationExecutor supports queries built from dynamic search criteria.
 */
public interface HotelRepository extends JpaRepository<Hotel, Long>, JpaSpecificationExecutor<Hotel> {

    /**
     * Finds a hotel and acquires a pessimistic write lock within a transaction.
     * Used to coordinate concurrent updates to the same hotel.
     *
     * @param id the ID of the hotel to lock
     * @return the hotel if it exists, or an empty Optional otherwise
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hotel h where h.id = :id")
    Optional<Hotel> findByIdForUpdate(@Param("id") Long id);

    /**
     * Projects each histogram query result into a grouping value and hotel count.
     * Getter names correspond to the "value" and "total" query aliases.
     */
    interface HistogramCount {
        String getValue();
        Long getTotal();
    }

    /**
     * Counts hotels per brand and sorts the results by brand.
     */
    @Query("select h.brand as value, count(h.id) as total from Hotel h group by h.brand order by h.brand")
    List<HistogramCount> countByBrand();

    /**
     * Counts hotels per city and sorts the results by city.
     */
    @Query("select h.city as value, count(h.id) as total from Hotel h group by h.city order by h.city")
    List<HistogramCount> countByCity();

    /**
     * Counts hotels per country and sorts the results by country.
     */
    @Query("select h.country as value, count(h.id) as total from Hotel h group by h.country order by h.country")
    List<HistogramCount> countByCountry();

    /**
     * Counts distinct hotels offering each amenity and sorts the results by amenity.
     * Hotels without amenities do not contribute to the results.
     */
    @Query("select a as value, count(distinct h.id) as total from Hotel h join h.amenities a group by a order by a")
    List<HistogramCount> countByAmenity();
}