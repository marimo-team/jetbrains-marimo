import marimo

__generated_with = "0.23.8"
app = marimo.App(width="medium")


@app.cell
def _():
    import marimo as mo

    return (mo,)


@app.cell
def _(mo):
    fruits = ["apple", "banana", "cherry", "date", "elderberry"]
 
    df = mo.ui.table(
        [{"index": i, "fruit": f, "length": len(f)} for i, f in enumerate(fruits)]
    )
    df
    return


if __name__ == "__main__":
    app.run()
