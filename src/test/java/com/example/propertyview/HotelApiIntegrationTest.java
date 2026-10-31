package com.example.propertyview;

import com.example.propertyview.repository.HotelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests the hotel API using the Spring application context and an in-memory H2 database.
 * Exercises request handling, validation, service logic and persistence through MockMvc.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:propertyview-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class HotelApiIntegrationTest {

    // Application context path included in all test requests.
    private static final String PREFIX = "/property-view";

    // Valid hotel creation request reused across tests.
    private static final String HOTEL = """
            {
              "name": "DoubleTree by Hilton Minsk",
              "description": "Hotel with views of Minsk",
              "brand": "Hilton",
              "address": {
                "houseNumber": 9, "street": "Pobediteley Avenue",
                "city": "Minsk", "country": "Belarus", "postCode": "220004"
              },
              "contacts": {
                "phone": "+375 17 309-80-00", "email": "doubletreeminsk.info@hilton.com"
              },
              "arrivalTime": {"checkIn": "14:00", "checkOut": "12:00"}
            }
            """;

    // Sends simulated HTTP requests through the application's MVC infrastructure.
    @Autowired MockMvc mvc;

    // Reads and writes JSON request and response data.
    @Autowired ObjectMapper json;

    // Provides direct database access for cleanup and persistence assertions.
    @Autowired HotelRepository repository;

    /**
     * Removes existing hotels before each test invocation.
     */
    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    /**
     * Verifies that an empty catalog produces empty lists and histogram data.
     */
    @Test
    void emptyCatalogAndHistogram() throws Exception {
        mvc.perform(api(get(PREFIX + "/hotels")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(api(get(PREFIX + "/search")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(api(get(PREFIX + "/histogram/amenities")))
                .andExpect(status().isOk()).andExpect(content().json("{}"));
    }

    /**
     * Verifies HTTP 201, the resource Location header and the summary response format.
     */
    @Test
    void createReturnsSummaryAndLocation() throws Exception {
        mvc.perform(api(post(PREFIX + "/hotels")).content(HOTEL))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        matchesPattern("http://localhost/property-view/hotels/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("DoubleTree by Hilton Minsk"))
                .andExpect(jsonPath("$.description").value("Hotel with views of Minsk"))
                .andExpect(jsonPath("$.address").value("9 Pobediteley Avenue, Minsk, 220004, Belarus"))
                .andExpect(jsonPath("$.phone").value("+375 17 309-80-00"))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.brand").doesNotExist());
    }

    /**
     * Verifies nested detail objects and an initially empty amenities collection.
     */
    @Test
    void detailReturnsNestedObjectsAndEmptyAmenities() throws Exception {
        long id = create(HOTEL);
        mvc.perform(api(get(PREFIX + "/hotels/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.brand").value("Hilton"))
                .andExpect(jsonPath("$.address.houseNumber").value(9))
                .andExpect(jsonPath("$.address.street").value("Pobediteley Avenue"))
                .andExpect(jsonPath("$.address.city").value("Minsk"))
                .andExpect(jsonPath("$.address.country").value("Belarus"))
                .andExpect(jsonPath("$.address.postCode").value("220004"))
                .andExpect(jsonPath("$.contacts.email").value("doubletreeminsk.info@hilton.com"))
                .andExpect(jsonPath("$.contacts.phone").value("+375 17 309-80-00"))
                .andExpect(jsonPath("$.arrivalTime.checkIn").value("14:00"))
                .andExpect(jsonPath("$.arrivalTime.checkOut").value("12:00"))
                .andExpect(jsonPath("$.amenities", hasSize(0)));
    }

    /**
     * Verifies that omitted optional properties are returned as JSON null values.
     */
    @Test
    void optionalPropertiesCanBeAbsent() throws Exception {
        var request = hotel();
        request.remove("description");
        ((ObjectNode) request.get("arrivalTime")).remove("checkOut");

        long id = create(request.toString());
        mvc.perform(api(get(PREFIX + "/hotels/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.arrivalTime.checkOut").value(nullValue()));
    }

    /**
     * Verifies that the hotel list contains summaries ordered by ascending ID.
     */
    @Test
    void listContainsOnlySummariesInIdOrder() throws Exception {
        long first = create(HOTEL);
        long second = create(HOTEL.replace("DoubleTree", "Another"));

        mvc.perform(api(get(PREFIX + "/hotels")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[1].id").value(second))
                .andExpect(jsonPath("$[0].address").isString())
                .andExpect(jsonPath("$[0].contacts").doesNotExist());
    }

    /**
     * Checks case-insensitive matching for each supported text filter.
     */
    @ParameterizedTest
    @CsvSource({"name, doubletree", "brand, hILTon", "city, mINsk", "country, belarus"})
    void searchFiltersAreCaseInsensitive(String parameter, String value) throws Exception {
        long id = create(HOTEL);
        mvc.perform(api(get(PREFIX + "/search")).param(parameter, value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));
    }

    /**
     * Verifies that all supplied search filters must match simultaneously.
     */
    @Test
    void searchCombinesAllFilters() throws Exception {
        long id = create(HOTEL);
        addAmenities(id, "[\"Free WiFi\", \"Free parking\"]");
        create(HOTEL.replace("Minsk", "Grodno"));

        mvc.perform(api(get(PREFIX + "/search"))
                        .param("name", "hilton").param("brand", "Hilton").param("city", "Minsk")
                        .param("country", "Belarus").param("amenities", "free wifi", "free parking"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        // No hotel matches both the requested city and the different brand.
        mvc.perform(api(get(PREFIX + "/search")).param("city", "Minsk").param("brand", "Marriott"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    /**
     * Checks that every requested amenity is required.
     * Covers repeated parameters, comma-separated values and duplicate filters.
     */
    @Test
    void amenitiesSearchRequiresAllValuesAndHasNoDuplicates() throws Exception {
        long first = create(HOTEL);
        long second = create(HOTEL);
        addAmenities(first, "[\"Free WiFi\", \"Free parking\"]");
        addAmenities(second, "[\"Free WiFi\"]");

        // Repeated parameters require both amenities.
        mvc.perform(api(get(PREFIX + "/search")).param("amenities", "free wifi", "FREE PARKING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(first));

        // A comma-separated parameter produces the same result.
        mvc.perform(api(get(PREFIX + "/search")).param("amenities", "Free WiFi,Free parking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(first));

        // Duplicate filters do not duplicate hotels in the response.
        mvc.perform(api(get(PREFIX + "/search")).param("amenities", "Free WiFi", "free wifi"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    /**
     * Verifies that blank search values do not restrict the results.
     */
    @Test
    void blankSearchFiltersAreIgnored() throws Exception {
        create(HOTEL);
        mvc.perform(api(get(PREFIX + "/search")).param("city", " ").param("amenities", ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    /**
     * Verifies literal matching of SQL LIKE wildcard characters.
     * Also checks that SQL-like input is treated as a search value.
     */
    @Test
    void nameSearchEscapesSqlWildcards() throws Exception {
        create(HOTEL);
        long special = create(HOTEL.replace("DoubleTree", "100%_Hotel"));

        mvc.perform(api(get(PREFIX + "/search")).param("name", "%_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(special));

        mvc.perform(api(get(PREFIX + "/search")).param("name", "' OR 1=1 --"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    /**
     * Checks additive updates, whitespace trimming and case-insensitive deduplication.
     * An empty amenities list must leave existing values unchanged.
     */
    @Test
    void amenitiesAreAdditiveTrimmedAndDeduplicated() throws Exception {
        long id = create(HOTEL);
        addAmenities(id, "[\"Free WiFi\", \"Free WiFi\", \" free wifi \"]");
        addAmenities(id, "[\"Free parking\", \"Free WiFi\"]");
        addAmenities(id, "[]");

        mvc.perform(api(get(PREFIX + "/hotels/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amenities", containsInAnyOrder("Free WiFi", "Free parking")));

        mvc.perform(api(get(PREFIX + "/histogram/amenities")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"Free WiFi\":1,\"Free parking\":1}"));
    }

    /**
     * Verifies hotel counts grouped by brand, city and country.
     */
    @ParameterizedTest
    @CsvSource({"brand,Hilton,Marriott", "city,Minsk,Grodno", "country,Belarus,Poland"})
    void histogramCountsHotels(String param, String firstValue, String secondValue) throws Exception {
        create(HOTEL);
        create(HOTEL);
        create(HOTEL.replace(firstValue, secondValue));

        mvc.perform(api(get(PREFIX + "/histogram/{param}", param)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"" + firstValue + "\":2,\"" + secondValue + "\":1}"));
    }

    /**
     * Verifies that each hotel contributes at most once to each amenity count.
     * Hotels without amenities do not add histogram entries.
     */
    @Test
    void amenityHistogramCountsDistinctHotels() throws Exception {
        long first = create(HOTEL);
        long second = create(HOTEL);
        create(HOTEL);
        addAmenities(first, "[\"Free WiFi\", \"Free WiFi\", \"Free parking\"]");
        addAmenities(second, "[\"Free WiFi\"]");

        mvc.perform(api(get(PREFIX + "/histogram/amenities")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"Free WiFi\":2,\"Free parking\":1}"));
    }

    /**
     * Verifies HTTP 404 for reading or adding amenities to a missing hotel.
     */
    @Test
    void missingHotelReturns404ForReadAndAmenities() throws Exception {
        mvc.perform(api(get(PREFIX + "/hotels/999999")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));

        mvc.perform(api(post(PREFIX + "/hotels/999999/amenities")).content("[]"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    /**
     * Checks that unsupported histogram attributes produce HTTP 400.
     */
    @ParameterizedTest
    @ValueSource(strings = {"name", "description", "id", "unknown"})
    void invalidHistogramParameterReturns400(String parameter) throws Exception {
        mvc.perform(api(get(PREFIX + "/histogram/{param}", parameter)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    /**
     * Verifies validation errors for missing required top-level properties.
     * Invalid requests must not create database records.
     */
    @ParameterizedTest
    @ValueSource(strings = {"name", "brand", "address", "contacts", "arrivalTime"})
    void requiredPropertiesAreValidated(String property) throws Exception {
        var request = hotel();
        request.remove(property);

        mvc.perform(api(post(PREFIX + "/hotels")).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + property).exists());

        assertThat(repository.count()).isZero();
    }

    /**
     * Verifies validation of nested address, contact and arrival-time properties.
     */
    @Test
    void nestedPropertiesAreValidated() throws Exception {
        var request = hotel();
        ((ObjectNode) request.get("address")).put("houseNumber", 0).put("city", " ");
        ((ObjectNode) request.get("contacts")).put("email", "not-an-email");
        ((ObjectNode) request.get("arrivalTime")).put("checkIn", "25:80");

        mvc.perform(api(post(PREFIX + "/hotels")).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['address.houseNumber']").exists())
                .andExpect(jsonPath("$.errors['address.city']").exists())
                .andExpect(jsonPath("$.errors['contacts.email']").exists())
                .andExpect(jsonPath("$.errors['arrivalTime.checkIn']").exists());
    }

    /**
     * Checks that a provided check-out time must use valid HH:mm format.
     * An empty string is invalid even though the property is optional.
     */
    @ParameterizedTest
    @ValueSource(strings = {"24:00", "12:60", "9:00", ""})
    void checkoutIsValidatedWhenProvided(String time) throws Exception {
        var request = hotel();
        ((ObjectNode) request.get("arrivalTime")).put("checkOut", time);

        mvc.perform(api(post(PREFIX + "/hotels")).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['arrivalTime.checkOut']").exists());
    }

    /**
     * Verifies that invalid amenities requests are rejected without partial updates.
     */
    @ParameterizedTest
    @ValueSource(strings = {"[\"\"]", "[\" \"]", "[null]", "null", "{}", "[\"valid\", \"\"]"})
    void invalidAmenitiesAreRejectedAtomically(String body) throws Exception {
        long id = create(HOTEL);

        mvc.perform(api(post(PREFIX + "/hotels/{id}/amenities", id)).content(body))
                .andExpect(status().isBadRequest());

        mvc.perform(api(get(PREFIX + "/hotels/{id}", id)))
                .andExpect(jsonPath("$.amenities", hasSize(0)));
    }

    /**
     * Verifies rejection of an amenity exceeding the 255-character limit.
     */
    @Test
    void oversizedAmenityIsRejected() throws Exception {
        long id = create(HOTEL);
        mvc.perform(api(post(PREFIX + "/hotels/{id}/amenities", id))
                        .content(json.writeValueAsString(java.util.List.of("a".repeat(256)))))
                .andExpect(status().isBadRequest());
    }

    /**
     * Checks HTTP 400 responses for malformed JSON and a non-numeric hotel ID.
     */
    @Test
    void malformedJsonAndWrongIdReturn400() throws Exception {
        mvc.perform(api(post(PREFIX + "/hotels")).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));

        mvc.perform(api(get(PREFIX + "/hotels/not-a-number")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    /**
     * Verifies that unknown JSON properties are rejected during deserialization.
     */
    @Test
    void unknownRequestPropertyIsRejected() throws Exception {
        var request = hotel().put("unexpected", "value");
        mvc.perform(api(post(PREFIX + "/hotels")).content(request.toString()))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that the generated OpenAPI document includes every API operation
     * and the expected server URL with the application context path.
     */
    @Test
    void swaggerSpecificationContainsAllRoutes() throws Exception {
        mvc.perform(api(get(PREFIX + "/v3/api-docs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/hotels'].get").exists())
                .andExpect(jsonPath("$.paths['/hotels'].post").exists())
                .andExpect(jsonPath("$.paths['/hotels/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/hotels/{id}/amenities'].post").exists())
                .andExpect(jsonPath("$.paths['/search'].get").exists())
                .andExpect(jsonPath("$.paths['/histogram/{param}'].get").exists())
                .andExpect(jsonPath("$.servers[0].url").value("http://localhost/property-view"));
    }

    /**
     * Configures the application context path and JSON content type for a request.
     */
    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.contextPath(PREFIX).contentType(MediaType.APPLICATION_JSON);
    }

    /**
     * Returns a fresh, editable JSON tree based on the valid hotel request.
     */
    private ObjectNode hotel() throws Exception {
        return (ObjectNode) json.readTree(HOTEL);
    }

    /**
     * Creates a hotel through the API and extracts its ID from the response.
     */
    private long create(String body) throws Exception {
        String response = mvc.perform(api(post(PREFIX + "/hotels")).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        return json.readTree(response).get("id").asLong();
    }

    /**
     * Adds amenities through the API and verifies an HTTP 204 response.
     */
    private void addAmenities(long id, String body) throws Exception {
        mvc.perform(api(post(PREFIX + "/hotels/{id}/amenities", id)).content(body))
                .andExpect(status().isNoContent());
    }
}