import pytest
from release_changes import Entry, PullRequest, classify, render


def entry_with_labels(*labels: str) -> Entry:
    return Entry(
        sha="1234567890abcdef",
        subject="fix: example change (#42)",
        pr=PullRequest(
            number=42,
            title="fix: example change",
            labels=set(labels),
            body="",
        ),
    )


@pytest.mark.parametrize(
    ("labels", "expected"),
    [
        (("internal", "bug"), "Fixed"),
        (("internal", "enhancement"), "Added"),
        (("documentation",), "excluded"),
        ((), "judgment"),
        (("internal",), "excluded"),
        (("dependencies", "bug"), "excluded"),
    ],
)
def test_classify(labels: tuple[str, ...], expected: str) -> None:
    assert classify(entry_with_labels(*labels)) == expected


def test_render_warns_and_includes_internal_bug(
    capsys: pytest.CaptureFixture[str],
) -> None:
    report = render("v1.0.0", "main", [entry_with_labels("internal", "bug")])

    assert "## Fixed" in report
    assert "Example change" in report
    assert "## Not included" not in report
    assert (
        "Warning: PR #42 has internal and user-facing labels (bug)"
        in capsys.readouterr().err
    )
