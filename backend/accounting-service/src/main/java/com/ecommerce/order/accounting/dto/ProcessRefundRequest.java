package com.ecommerce.order.accounting.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessRefundRequest {

    @JsonAlias({"approved", "isApproved"})
    private Boolean approve;

    @JsonAlias({"decisionNotes", "notes", "reason"})
    private String decisionReason;

    public Boolean getApprove() {
        return approve != null && approve;
    }
}
