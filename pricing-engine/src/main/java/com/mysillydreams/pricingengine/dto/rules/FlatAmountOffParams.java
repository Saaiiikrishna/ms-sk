package com.mysillydreams.pricingengine.dto.rules;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlatAmountOffParams {
    private BigDecimal amountOff; // Using BigDecimal for monetary amounts
}
