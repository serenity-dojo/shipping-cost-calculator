# Free Shipping on Qualifying Domestic Orders

**As an online retailer, I want to offer free shipping on qualifying domestic orders, so that I can incentivise larger purchases.**

## Rules and Examples

### Rule: Must waive shipping (total cost = £0.00) only when the order is Domestic, its order total is at least £75.00, and the parcel weighs 20kg or less

This is **step 3** of the calculation, applied after the weight-tier base rate (step 1) and the zone multiplier (step 2). All three conditions must hold; if any fails, the order pays its normal zoned rate. The weight cap means a qualifying *total* is not enough on its own — a heavy parcel still pays.

| Zone | Order total | Weight | Zoned rate (steps 1–2) | Total cost |
|---|---|---|---|---|
| Domestic | £120.00 | 3kg | £4.99 | £0.00 — qualifies |
| Domestic | £75.00 | 3kg | £4.99 | £0.00 — boundary: exactly £75 qualifies |
| Domestic | £74.99 | 3kg | £4.99 | £4.99 — one penny under, pays |
| Domestic | £120.00 | 20.0kg | £8.99 | £0.00 — boundary: 20kg still qualifies |
| Domestic | £120.00 | 25.0kg | £11.49 | £11.49 — over 20kg, pays despite qualifying total |
| European | £200.00 | 3kg | £7.49 | £7.49 — non-domestic never qualifies |
| International | £500.00 | 3kg | £12.48 | £12.48 — non-domestic never qualifies |

The boundary rows are the counter-examples: £74.99 (under threshold), 25kg (over the weight cap), and the European/International rows (wrong zone) all pay in full.

---

### Rule: Must require a valid order total on every request, rejecting a missing or negative value

`orderTotal` is now a required request field. A value of £0.00 is valid (it simply falls below the threshold); a missing or negative value is rejected as an invalid request, consistent with how invalid weight and unknown zones are handled.

| Order total | Outcome |
|---|---|
| £120.00 | accepted — priced normally |
| £0.00 | accepted — valid, below threshold, no free shipping |
| missing | rejected — invalid request |
| −10.00 | rejected — invalid request |

The boundary rows (£0.00 accepted, missing and −10.00 rejected) define the valid range.

## Resolved decisions (for implementation)

- **Threshold:** £75.00 inclusive — exactly £75.00 qualifies; £74.99 does not.
- **Zone restriction:** Domestic only. European and International never qualify regardless of order total.
- **Weight cap:** parcels of 20kg or less qualify; over 20kg pays the (surcharged) zoned rate even when the total and zone qualify.
- **orderTotal field:** required on every request; missing or negative is rejected (400). £0.00 is valid.
- **Processing order:** free-shipping is step 3, evaluated after base rate (step 1) and zone multiplier (step 2).
- **Out of scope:** weight-tier base rates and over-20kg surcharge (weight-tiers spec); zone multipliers (distance-zones spec).
