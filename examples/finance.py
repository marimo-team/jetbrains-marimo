"""Small helpers for exploring compound growth."""


def compound_balance(principal: float, annual_rate: float, years: int) -> float:
    """Balance after `years` of annually compounded growth."""
    return principal * (1 + annual_rate) ** years


def yearly_schedule(
    principal: float, annual_rate: float, years: int
) -> list[dict[str, float]]:
    """Year-by-year balance, one row per year."""
    rows = []
    balance = principal
    for year in range(1, years + 1):
        balance *= 1 + annual_rate
        rows.append({"year": year, "balance": round(balance, 2)})
    return rows
