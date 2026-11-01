package com.ecommerce.cart.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartSyncRequest {
    private String sessionToken;
    private List<AddToCartRequest> items;
}
