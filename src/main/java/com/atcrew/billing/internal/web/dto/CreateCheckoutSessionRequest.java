package com.atcrew.billing.internal.web.dto;

import com.atcrew.billing.BillingProduct;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CreateCheckoutSessionRequest(

        @Schema(description = "구매할 상품", example = "PRO_MONTHLY")
        @NotNull(message = "상품을 선택해 주세요")
        BillingProduct product
) {
}
