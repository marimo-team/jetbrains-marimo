import marimo

__generated_with = "0.23.8"
app = marimo.App(width="medium")


@app.cell
def _():
    import marimo as mo
    from test_no_marimo import test_fn_2

    return mo, test_fn_2


@app.cell
def _(mo):
    fruits = ["apple", "banana", "cherry", "date", "elderberry"]

    df = mo.ui.table(
        [{"index": i, "fruit": f, "length": len(f)} for i, f in enumerate(fruits)]
    )
    df
    return


@app.cell
def _():
    print("Hello, World!")
    return


@app.cell
def _(test_fn_2):
    test_fn_2(1, 3)
    return


@app.cell(hide_code=True)
def _(mo):
    mo.md(r"""
    # Hello, World
    """)
    return


if __name__ == "__main__":
    app.run()
