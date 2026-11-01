package com.ecommerce.product.storefront;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The checkout-side storefront lookup: service token required, only ACTIVE storefronts, tenant decided by the server. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"SERVICE_TO_SERVICE_TOKEN=test-service-token", "storefront.scheduler.delay-ms=3600000", "pricing.scheduler.delay-ms=3600000"})
class InternalStorefrontControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void schema() {
        for (String s : new String[]{"V7__pricing_and_promotions.sql", "V8__storefront_content_studio.sql"})
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/" + s)).execute(dataSource);
        wipe();
        jdbc.update("INSERT INTO storefronts(tenant_id, store_id, name, slug, storefront_type, status) VALUES (?,?,?,?,?,?)",
                java.util.UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a"), "main", "Acme", "acme", "RETAIL", "ACTIVE");
        jdbc.update("INSERT INTO storefronts(tenant_id, store_id, name, slug, storefront_type, status) VALUES (?,?,?,?,?,?)",
                java.util.UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b"), "kitchen", "Bistro", "bistro", "RESTAURANT", "SUSPENDED");
    }

    @AfterEach
    void wipe() { jdbc.update("DELETE FROM storefronts"); }

    @Test
    @DisplayName("Resolves an active storefront slug (case-insensitive) to its tenant, store and type")
    void resolvesActiveStorefront() throws Exception {
        mvc.perform(get("/internal/storefronts/ACME").header("X-Service-Token", "test-service-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId", is("aaaaaaaa-0000-4000-8000-00000000000a")))
                .andExpect(jsonPath("$.storeId", is("main")))
                .andExpect(jsonPath("$.storefrontType", is("RETAIL")));
    }

    @Test
    @DisplayName("Requires the exact service token, and hides suspended or unknown storefronts")
    void refusesWithoutTokenAndHidesInactive() throws Exception {
        mvc.perform(get("/internal/storefronts/acme")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/storefronts/acme").header("X-Service-Token", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/storefronts/bistro").header("X-Service-Token", "test-service-token")).andExpect(status().isNotFound());
        mvc.perform(get("/internal/storefronts/nope").header("X-Service-Token", "test-service-token")).andExpect(status().isNotFound());
    }
}
