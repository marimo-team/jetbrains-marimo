# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "marimo>=0.23.9",
#     "altair>=5.0",
#     "pandas>=2.0",
#     "vega-datasets>=0.9",
#     "pyarrow>=15.0",
# ]
# ///

import marimo

__generated_with = "0.23.9"
app = marimo.App(width="medium")


@app.cell(hide_code=True)
def _(mo):
    mo.md(r"""
    # 🚗 Cars explorer

    Filter with the controls, then **drag a box on the chart** to select points —
    the table and stats below react to your selection.

    The derived columns (color tier, point size) come from `add_metrics` in
    **`data.py`**. Edit that function while this notebook is open and watch every
    dependent cell recompute on its own.
    """)
    return


@app.cell
def _():
    import altair as alt
    import marimo as mo
    from vega_datasets import data as vega_data

    from data import add_metrics

    return add_metrics, alt, mo, vega_data


@app.cell
def _(add_metrics, vega_data):
    cars = add_metrics(vega_data.cars())
    return (cars,)


@app.cell(hide_code=True)
def _(cars, mo):
    origins = mo.ui.multiselect(
        options=sorted(cars["Origin"].unique()),
        value=sorted(cars["Origin"].unique()),
        label="Origin",
    )
    cylinders = mo.ui.multiselect(
        options=sorted(cars["Cylinders"].unique().tolist()),
        value=sorted(cars["Cylinders"].unique().tolist()),
        label="Cylinders",
    )
    mo.hstack([origins, cylinders], justify="start", gap=2)
    return cylinders, origins


@app.cell
def _(cars, cylinders, origins):
    filtered = cars[
        cars["Origin"].isin(origins.value) & cars["Cylinders"].isin(cylinders.value)
    ]
    return (filtered,)


@app.cell
def _(alt, filtered, mo):
    scatter = (
        alt.Chart(filtered)
        .mark_circle(opacity=0.7)
        .encode(
            x=alt.X("Horsepower:Q", title="Horsepower"),
            y=alt.Y("Miles_per_Gallon:Q", title="Miles per gallon"),
            color=alt.Color("efficiency_tier:N", title="Efficiency tier"),
            size=alt.Size("power_to_weight:Q", title="Power / weight"),
            tooltip=["Name", "Origin", "Cylinders", "Horsepower", "Miles_per_Gallon"],
        )
        .properties(height=380)
    )
    chart = mo.ui.altair_chart(scatter)
    chart
    return (chart,)


@app.cell(hide_code=True)
def _(chart, filtered, mo):
    selected = chart.value if len(chart.value) else filtered
    scope = "brushed selection" if len(chart.value) else "all filtered cars"
    mo.md(f"""
    **{len(selected)} cars** in the {scope} —
    avg **{selected["Miles_per_Gallon"].mean():.1f} MPG**,
    avg **{selected["Horsepower"].mean():.0f} HP**.
    """)
    return (selected,)


@app.cell(hide_code=True)
def _(mo, selected):
    mo.ui.table(
        selected,
        label="Cars in scope",
        show_column_summaries=True
    )
    return


if __name__ == "__main__":
    app.run()
