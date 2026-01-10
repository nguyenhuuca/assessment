# APR Calculation Using Binary Search

## 1. Problem Statement

We want to find the APR (interest rate `r`) such that:

\[
\sum_{i=1}^{n} \frac{C_i}{(1+r)^{t_i}} = \text{Target}
\]

Where:
- `C_i`: cash flow at period *i* (repayment amount)
- `r`: APR to be solved
- `t_i`: accumulated time from disbursement date (days/(365 or 366)
- `Target`: initial disbursed amount

---

## 2. Why There Is No Closed-Form Formula

- The unknown `r` appears in the exponent
- There are multiple cash flows
- Payment intervals may be irregular

➡ This is a **transcendental equation**, which cannot be solved using algebraic formulas.

➡ A **numerical method** is required.

---

## 3. Why Binary Search Works

Define:
\[
f(r) = \sum \frac{C_i}{(1+r)^{t_i}} - Target
\]

Key properties:
- `f(r)` is **continuous**
- `f(r)` is **monotonically decreasing** with respect to `r`
- There is **exactly one root**

➡ Binary search is guaranteed to **converge**.

---

## 4. Correct Meaning of Time Interval `t_i`

**Critical rule**:
> `t_i` must always be calculated **cumulatively from the initial date (t0)**.

Correct:
\[
t_i = \frac{date_i - date_0}{365}
\]

Incorrect:
\[
t_i = \frac{date_i - date_{i-1}}{365}
\]

Using non-cumulative intervals will produce an **incorrect APR**.

---

## 5. Why 35 Iterations Are Sufficient

Binary search halves the search interval on each iteration.

After `k` iterations:
\[
L_k = \frac{hi - lo}{2^k}
\]

To reach precision `ε`:
\[
\frac{hi - lo}{2^k} \le ε
\]

Which implies:
\[
k \ge \log_2\left(\frac{hi - lo}{ε}\right)
\]

Example:
- Initial range: `hi − lo ≈ 1` (0%–100%)
- Desired precision: `ε ≈ 1e-10`

\[
\log_2(10^{10}) \approx 34
\]

➡ **35 iterations already provide more precision than double-precision floating point can represent.**

---

## 6. Why `hi` Can Be Set to 100%

- `hi` is **not a business assumption**
- It is only a **technical upper bound** to guarantee the root exists in the range

As `r` increases:
\[
PV(r) \rightarrow 0 < Target
\]

➡ Any sufficiently large `hi` works.

Safe implementation:
```java
while (pv(hi) > target) {
    hi *= 2;
}
```

---

## 7. Why Some Systems Use 100–200 Iterations

- Floating-point rounding errors
- Very complex or long cash-flow schedules
- Defensive programming

The computation cost is negligible, so extra iterations do not impact performance.

---

## 8. Summary

- APR  **no closed-form solution**
- Binary search is the **industry-standard approach**
- Time intervals must be **cumulative from t0**
- **35 iterations are mathematically sufficient**
- `hi` is a **technical bound**, not a business rate

---

**What problem are we solving?**

We want to calculate the true annual interest rate (APR) of a loan when money is paid and received at different times.

**Why can’t we use a simple formula?**

Because repayments happen over time and may not be evenly spaced. In such cases, there is no single shortcut formula that gives the correct result.

**How does the system calculate APR?**

The system tries different interest rates and checks which one makes the total value of all repayments equal to the original loan amount.
Each step narrows the range until the correct rate is found.

**Is this approach reliable?**

Yes. This is the same approach used by Excel (XIRR) and many banking systems worldwide.
Ref: https://support.microsoft.com/en-au/office/xirr-function-de1242ec-6477-445b-b11b-a303ad9adc9d

>Excel uses an iterative technique for calculating XIRR. Using a changing rate (starting with guess), XIRR cycles through the calculation until the result is accurate within 0.000001 percent. If XIRR can't find a result after 100 tries, it returns a #NUM! error value.

**Why do we run multiple iterations?**

Each iteration improves accuracy. About 35 iterations are already enough to reach very high precision.
Running more iterations is simply a safety margin and has no noticeable performance cost.

**Does the system assume unrealistic interest rates?**

No. High upper bounds are used only as a technical search range to guarantee correctness.
The final APR result is always realistic and based on actual cash flows.


