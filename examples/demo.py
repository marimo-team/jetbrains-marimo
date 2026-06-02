import marimo

__generated_with = "0.23.8"
app = marimo.App(width="medium")


@app.cell(hide_code=True)
def _(mo):
    mo.md(r"""
    # 💰 Compound growth explorer

    Move the sliders — every dependent cell recomputes on its own.
    """)
    return


@app.cell
def _():
    import marimo as mo

    from finance import compound_balance, yearly_schedule

    return compound_balance, mo, yearly_schedule


@app.cell
def _(mo):
    principal = mo.ui.slider(
        start=100, stop=10_000, step=100, value=1_000, label="Principal ($)"
    )
    annual_rate = mo.ui.slider(
        start=0.0, stop=0.15, step=0.005, value=0.05, label="Annual rate"
    )
    years = mo.ui.slider(start=1, stop=40, value=10, label="Years")
    mo.vstack([principal, annual_rate, years])
    return annual_rate, principal, years


@app.cell
def _(annual_rate, compound_balance, principal, years):
    final_balance = round(
        compound_balance(principal.value, annual_rate.value, years.value), 2
    )
    return (final_balance,)


@app.cell(hide_code=True)
def _(annual_rate, final_balance, mo, principal, years):
    mo.md(f"""
    After **{years.value} years** at **{annual_rate.value:.1%}**, **{principal.value:,.0f}** → **{final_balance:,.2f}**.
    """)
    return


@app.cell(hide_code=True)
def _(annual_rate, mo, principal, yearly_schedule, years):
    schedule = yearly_schedule(principal.value, annual_rate.value, years.value)
    mo.ui.table(schedule, label="Year-by-year balance")
    return


if __name__ == "__main__":
    app.run()
