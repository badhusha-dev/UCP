package com.ecommerce.user;

import com.ecommerce.user.dto.EarnPointsRequest;
import com.ecommerce.user.dto.RedeemPointsRequest;
import com.ecommerce.user.model.CustomerAddress;
import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.CustomerAddressRepository;
import com.ecommerce.user.repository.CustomerLoyaltyRepository;
import com.ecommerce.user.repository.LoyaltyTransactionRepository;
import com.ecommerce.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LoyaltyAndAddressIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerLoyaltyRepository loyaltyRepository;

    @Autowired
    private LoyaltyTransactionRepository transactionRepository;

    @Autowired
    private CustomerAddressRepository addressRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        addressRepository.deleteAll();
        transactionRepository.deleteAll();
        loyaltyRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setEmail("loyalty.user@example.com");
        user.setPassword("password123");
        user.setName("Loyal Shopper");
        user.setRole("USER");
        user.setActive(true);
        testUser = userRepository.save(user);
    }

    @Test
    @DisplayName("GET /api/users/{id}/loyalty should return user loyalty rewards profile")
    void testGetLoyaltyProfile() throws Exception {
        mockMvc.perform(get("/api/users/" + testUser.getId() + "/loyalty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(testUser.getId()))
                .andExpect(jsonPath("$.pointsBalance").value(0))
                .andExpect(jsonPath("$.tier").value("BRONZE"))
                .andExpect(jsonPath("$.cashValue").value(0.00))
                .andExpect(jsonPath("$.nextTier").value("SILVER"));
    }

    @Test
    @DisplayName("POST /api/users/{id}/loyalty/earn should accrue points and upgrade tier")
    void testEarnPointsAndUpgradeTier() throws Exception {
        EarnPointsRequest earnReq = new EarnPointsRequest();
        earnReq.setOrderId(101L);
        earnReq.setOrderAmount(new BigDecimal("600.00"));

        mockMvc.perform(post("/api/users/" + testUser.getId() + "/loyalty/earn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(earnReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsBalance", greaterThanOrEqualTo(600)))
                .andExpect(jsonPath("$.tier").value("SILVER"));

        // Verify transaction audit trail
        mockMvc.perform(get("/api/users/" + testUser.getId() + "/loyalty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentTransactions", hasSize(1)))
                .andExpect(jsonPath("$.recentTransactions[0].transactionType").value("EARN"));
    }

    @Test
    @DisplayName("POST /api/users/{id}/loyalty/redeem should deduct points and return discount")
    void testRedeemPointsSuccess() throws Exception {
        // First earn points
        EarnPointsRequest earnReq = new EarnPointsRequest();
        earnReq.setOrderId(200L);
        earnReq.setOrderAmount(new BigDecimal("400.00"));
        mockMvc.perform(post("/api/users/" + testUser.getId() + "/loyalty/earn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(earnReq)))
                .andExpect(status().isOk());

        // Redeem 100 points ($5.00 discount)
        RedeemPointsRequest redeemReq = new RedeemPointsRequest();
        redeemReq.setPoints(100);
        redeemReq.setOrderId(201L);

        mockMvc.perform(post("/api/users/" + testUser.getId() + "/loyalty/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.pointsRedeemed").value(100))
                .andExpect(jsonPath("$.discountAmount").value(5.00))
                .andExpect(jsonPath("$.remainingBalance").value(300));
    }

    @Test
    @DisplayName("POST /api/users/{id}/loyalty/redeem should reject redemption if insufficient points")
    void testRedeemPointsInsufficient() throws Exception {
        RedeemPointsRequest redeemReq = new RedeemPointsRequest();
        redeemReq.setPoints(200);

        mockMvc.perform(post("/api/users/" + testUser.getId() + "/loyalty/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Insufficient reward points")));
    }

    @Test
    @DisplayName("Address Book: Add address, list addresses, and delete address")
    void testAddressBookCRUD() throws Exception {
        CustomerAddress address = new CustomerAddress();
        address.setStreet("500 Tech Blvd");
        address.setCity("Austin");
        address.setState("TX");
        address.setPostalCode("78701");
        address.setCountry("US");
        address.setIsDefault(true);
        address.setAddressType("SHIPPING");

        // 1. Add address
        String resp = mockMvc.perform(post("/api/users/" + testUser.getId() + "/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(address)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Austin"))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andReturn().getResponse().getContentAsString();

        Long addressId = objectMapper.readTree(resp).get("id").asLong();

        // 2. List addresses
        mockMvc.perform(get("/api/users/" + testUser.getId() + "/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].street").value("500 Tech Blvd"));

        // 3. Delete address
        mockMvc.perform(delete("/api/users/" + testUser.getId() + "/addresses/" + addressId))
                .andExpect(status().isNoContent());

        // 4. Verify empty list
        mockMvc.perform(get("/api/users/" + testUser.getId() + "/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
