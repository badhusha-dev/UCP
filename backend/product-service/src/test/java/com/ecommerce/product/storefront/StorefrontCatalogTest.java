package com.ecommerce.product.storefront;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
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
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The shopper catalog limited to a storefront: the server maps the public slug to its tenant, as checkout does. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"SERVICE_TO_SERVICE_TOKEN=test-service-token", "storefront.scheduler.delay-ms=3600000", "pricing.scheduler.delay-ms=3600000"})
class StorefrontCatalogTest {
    private static final UUID ACME = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");
    private static final UUID BISTRO = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b");

    @Autowired private MockMvc mvc;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProductRepository products;

    @BeforeEach
    void seed() {
        for (String s : new String[]{"V7__pricing_and_promotions.sql", "V8__storefront_content_studio.sql"})
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/" + s)).execute(dataSource);
        wipe();
        jdbc.update("INSERT INTO storefronts(tenant_id, store_id, name, slug, storefront_type, status) VALUES (?,?,?,?,?,?)",
                ACME, "main", "Acme", "acme", "RETAIL", "ACTIVE");
        jdbc.update("INSERT INTO storefronts(tenant_id, store_id, name, slug, storefront_type, status) VALUES (?,?,?,?,?,?)",
                BISTRO, "kitchen", "Bistro", "bistro", "RESTAURANT", "SUSPENDED");
        save("Legacy Lamp", Product.LEGACY_TENANT_ID, true);
        save("Acme Anvil", ACME, true);
        save("Acme Archived Anvil", ACME, false);
        save("Bistro Bread", BISTRO, true);
    }

    @AfterEach
    void wipe() {
        jdbc.update("DELETE FROM storefronts");
        products.deleteAll();
    }

    private void save(String name, UUID tenant, boolean active) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(name + " description");
        p.setPrice(10.0);
        p.setStockQuantity(5);
        p.setTenantId(tenant);
        p.setActive(active);
        products.save(p);
    }

    @Test
    @DisplayName("A storefront slug lists only that storefront's active products, in the plain and the paged form")
    void storefrontListsOnlyItsTenant() throws Exception {
        mvc.perform(get("/api/products").param("storefront", "ACME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Acme Anvil"));

        mvc.perform(get("/api/products").param("storefront", "acme").param("query", "a").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Acme Anvil"));
    }

    @Test
    @DisplayName("Unknown and suspended storefronts get 404 instead of falling back to another tenant's catalog")
    void unknownOrInactiveStorefrontIsNotFound() throws Exception {
        mvc.perform(get("/api/products").param("storefront", "nope")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").param("storefront", "bistro")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").param("storefront", "bistro").param("page", "0")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("The platform address <slug>.<base-domain> resolves to an active storefront only")
    void platformSubdomainResolvesToItsStorefront() throws Exception {
        mvc.perform(get("/api/products/public/storefront/resolve").param("host", "Acme.storefront.localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("acme"))
                .andExpect(jsonPath("$.primary").value(false));
        mvc.perform(get("/api/products/public/storefront/resolve").param("host", "bistro.storefront.localhost")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/public/storefront/resolve").param("host", "x.acme.storefront.localhost")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/public/storefront/resolve").param("host", "acme.example.com")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Without a storefront the listing is unchanged: every active product")
    void noStorefrontKeepsTheExistingListing() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Legacy Lamp", "Acme Anvil", "Bistro Bread")));
    }
}
