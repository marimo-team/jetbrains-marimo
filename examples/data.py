import pandas as pd


def add_metrics(df: pd.DataFrame) -> pd.DataFrame:
    """Return a copy of the cars frame with derived columns the chart uses."""
    out = df.copy()
    out["power_to_weight"] = out["Horsepower"] / out["Weight_in_lbs"] * 1000
    out["efficiency_tier"] = pd.cut(
        out["Miles_per_Gallon"],
        bins=[0, 18, 25, 100],
        # bins=[0, 15, 30, 40, 100],
        labels=["Thirsty", "Average", "Efficient"],
    )
    return out
