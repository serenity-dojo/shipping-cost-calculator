# Distance-Zone Shipping Adjustment

**As an online retailer, I want shipping costs adjusted by destination zone, so that international deliveries reflect higher logistics costs.**

## Rules and Examples

### Rule: Must scale the weight-based cost by the destination zone's multiplier, applied after the weight-tier step

This is step 2 of the calculation. The multiplier applies to the **full weight cost** — the tier base rate *plus* any over-20kg surcharge, not just the base. The product is carried at full precision; only the final total cost is rounded HALF_UP to 2 decimal places.

| Zone | Multiplier | Weight cost | Total cost |
|---|---|---|---|
| Domestic | ×1.0 | £4.99 (3kg) | £4.99 |
| European | ×1.5 | £4.99 (3kg) | £7.49 |
| International | ×2.5 | £4.99 (3kg) | £12.48 |
| International | ×2.5 | £11.49 (25kg) | £28.73 |

---

### Rule: Must reject a request whose zone is missing, empty, or not one of the three recognised zones

Recognised zones are Domestic, European and International. Zone matching is **case-insensitive**. A missing or empty zone is rejected exactly like an unrecognised value.

- **Example:** The one where the zone is `"ANTARCTIC"` and the request is rejected as an invalid request.
- **Example:** The one where the zone is `"domestic"` or `"INTERNATIONAL"` — accepted and priced, because matching ignores case.
- **Counter-example:** The one where the zone is missing or empty — rejected the same as an unknown value, not defaulted.

## Resolved decisions (for implementation)

- **Surcharge scaling:** the multiplier applies to the full weight cost (tier base + over-20kg surcharge), not the base alone.
- **Precision:** carry the zoned product at full precision; round only the final total HALF_UP to 2 dp.
- **Zone matching:** case-insensitive against the three recognised zones.
- **Missing zone:** treated identically to an unrecognised zone — rejected, never defaulted.
- **Out of scope:** weight-tier base rates and the over-20kg surcharge (weight-tiers spec); free-shipping logic (free-shipping spec).
