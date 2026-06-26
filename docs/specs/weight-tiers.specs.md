# Weight-Based Tiered Shipping

**As an online retailer, I want shipping costs calculated based on parcel weight using tiered pricing, so that heavier parcels are charged appropriately.**

## Rules and Examples

### Rule: Must set the base rate from the parcel's weight tier, with each tier's lower bound inclusive

The base rate is the first step of the cost calculation. Tier boundaries are lower-bound inclusive — a weight exactly on a boundary belongs to the *higher* tier.

| Weight (kg) | Base rate | Why |
|---|---|---|
| 0.5  | £2.99 | normal — under 1kg |
| 1.0  | £4.99 | boundary — 1kg belongs to the 1–5kg tier |
| 3.0  | £4.99 | normal — 1–5kg |
| 5.0  | £8.99 | boundary — 5kg belongs to the 5–20kg tier |
| 12.0 | £8.99 | normal — 5–20kg |
| 20.0 | £8.99 | boundary — 20kg belongs to the over-20kg tier (surcharge = £0.00) |

The boundary rows (1.0, 5.0, 20.0) are the counter-examples: the lower bound always belongs to the higher tier.

---

### Rule: Must add a surcharge of £0.50 per kg over 20kg, pro-rata to the exact weight, on top of the £8.99 base

For parcels over 20kg the cost is £8.99 plus £0.50 for every kilogram above 20, applied pro-rata to the exact (fractional) weight — not rounded to whole kilograms. Money is `BigDecimal`, scale 2, `RoundingMode.HALF_UP`.

| Weight (kg) | Surcharge | Total base cost |
|---|---|---|
| 20.0 | £0.00 | £8.99 |
| 22.5 | £0.50 × 2.5 = £1.25 | £10.24 |
| 25.0 | £0.50 × 5 = £2.50 | £11.49 |

- **Counter-example:** The one where a parcel is exactly 20kg — the excess is 0, the surcharge is £0.00, and the cost stays at £8.99.

---

### Rule: Must reject a parcel whose weight is zero, negative, or above 50kg

Valid weight is greater than 0kg and up to and including 50kg. Anything outside that range is rejected as an invalid request.

| Weight (kg) | Outcome |
|---|---|
| 0.01  | accepted — charged £2.99 (tiny but positive) |
| 50.0  | accepted — charged £8.99 + £0.50 × 30 = £23.99 (upper bound inclusive) |
| 0     | rejected — invalid |
| −2.0  | rejected — invalid |
| 50.01 | rejected — exceeds the 50kg maximum |

The boundary rows (0.01 accepted, 50.0 accepted, 50.01 rejected) define the valid range.

## Resolved decisions (for implementation)

- **Fractional surcharge:** pro-rata to exact weight — £0.50 × (weight − 20), no rounding of the kilogram count; monetary result rounded HALF_UP to 2 decimal places.
- **Upper weight limit:** 50kg, inclusive. Parcels over 50kg are rejected like zero/negative weight.
- **Tier boundaries:** lower-bound inclusive.
- **Out of scope:** zone multipliers and free-shipping logic (covered by their own specs); this story covers the weight-based base rate only.
