#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Report the merged pull requests that will go into the next release.

Walks main's squash-merge history since a release tag, joins each commit to the
pull request it came from, and groups the results by the label that pull request
carries. Prints a markdown report to stdout; writes nothing.

Usage:
    uv run scripts/release_changes.py [since-tag]
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass

REPO_URL = "https://github.com/marimo-team/jetbrains-marimo"

# Where a label sends its entry in CHANGELOG.md, in precedence order: a pull
# request carrying several labels lands under the first one that matches.
LABEL_DESTINATIONS: list[tuple[str, str]] = [
    ("breaking", "Changed"),
    ("preview", "Added"),
    ("bug", "Fixed"),
    ("enhancement", "Added"),
]

# Labels that keep a change out of CHANGELOG.md. Checked before the
# destinations above, so `internal` wins over any other label.
HARD_EXCLUDED = ("internal", "dependencies")

# Excluded only when no destination label applies, so a documentation change
# that also matters to users can carry `enhancement` and still be listed.
SOFT_EXCLUDED = ("documentation",)

SECTION_ORDER = ("Added", "Changed", "Fixed")


@dataclass
class PullRequest:
    number: int
    title: str
    labels: set[str]
    body: str


@dataclass
class Entry:
    sha: str
    subject: str
    pr: PullRequest | None

    @property
    def link(self) -> str:
        if self.pr is None:
            return f"`{self.sha[:7]}`"
        return f"[#{self.pr.number}]({REPO_URL}/pull/{self.pr.number})"

    @property
    def headline(self) -> str:
        source = self.pr.title if self.pr else self.subject
        return strip_conventional_prefix(source)


def run(*args: str) -> str:
    result = subprocess.run(args, capture_output=True, text=True, check=True)
    return result.stdout.strip()


def latest_tag() -> str:
    run("git", "fetch", "--tags", "--quiet")
    tags = run("git", "tag", "--sort=-v:refname").splitlines()
    if not tags:
        raise SystemExit(
            "No tags found. Pass the previous release explicitly: "
            "uv run scripts/release_changes.py <since-tag>"
        )
    return tags[0]


def describe_head() -> str:
    """Name the current branch, falling back to the short commit when HEAD is detached."""
    branch = run("git", "rev-parse", "--abbrev-ref", "HEAD")
    if branch != "HEAD":
        return branch
    return run("git", "rev-parse", "--short", "HEAD")


def commits_since(tag: str) -> list[tuple[str, str]]:
    log = run("git", "log", f"{tag}..HEAD", "--first-parent", "--format=%H %s")
    commits = []
    for line in log.splitlines():
        sha, _, subject = line.partition(" ")
        if subject:
            commits.append((sha, subject))
    return commits


def merged_pull_requests(limit: int) -> dict[int, PullRequest]:
    raw = run(
        "gh", "pr", "list",
        "--base", "main",
        "--state", "merged",
        "--limit", str(limit),
        "--json", "number,title,labels,body",
    )
    return {
        item["number"]: PullRequest(
            number=item["number"],
            title=item["title"],
            labels={label["name"] for label in item["labels"]},
            body=item.get("body") or "",
        )
        for item in json.loads(raw)
    }


def strip_conventional_prefix(title: str) -> str:
    match = re.match(r"^\w+(?:\([^)]+\))?!?:\s*(.+)", title)
    text = match.group(1) if match else title
    # Drop the trailing "(#56)" that squash merges append to the subject.
    text = re.sub(r"\s*\(#\d+\)\s*$", "", text)
    return text[:1].upper() + text[1:] if text else text


def classify(entry: Entry) -> str:
    """Return a CHANGELOG.md section name, or "judgment" / "excluded"."""
    if entry.pr is None:
        return "judgment"

    labels = entry.pr.labels
    for label in HARD_EXCLUDED:
        if label in labels:
            return "excluded"

    for label, section in LABEL_DESTINATIONS:
        if label in labels:
            return section

    for label in SOFT_EXCLUDED:
        if label in labels:
            return "excluded"

    return "judgment"


def format_entry(entry: Entry) -> str:
    prefix = ""
    if entry.pr and "breaking" in entry.pr.labels:
        prefix = "**Breaking:** "
    elif entry.pr and "preview" in entry.pr.labels:
        prefix = "**Preview:** "
    return f"* {prefix}{entry.headline} ({entry.link})"


def render(tag: str, head: str, entries: list[Entry]) -> str:
    sections: dict[str, list[Entry]] = {name: [] for name in SECTION_ORDER}
    judgment: list[Entry] = []
    excluded: list[Entry] = []
    highlights: list[Entry] = []

    for entry in entries:
        destination = classify(entry)
        if destination in sections:
            sections[destination].append(entry)
            if entry.pr and "release-highlight" in entry.pr.labels:
                highlights.append(entry)
        elif destination == "judgment":
            judgment.append(entry)
        else:
            excluded.append(entry)

    out = [
        f"# Changes since {tag}",
        "",
        f"Range: `{tag}..HEAD` at `{head}` — {len(entries)} commit(s).",
        "",
    ]

    if highlights:
        out += ["## Release highlights", ""]
        for entry in highlights:
            out += [
                f"TODO: write a user-facing paragraph for {entry.headline} ({entry.link}).",
                "",
            ]

    for name in SECTION_ORDER:
        if sections[name]:
            out += [f"## {name}", ""]
            out += [format_entry(entry) for entry in sections[name]]
            out.append("")

    if judgment:
        out += [
            "## Needs judgment",
            "",
            "No destination label. Fix the label and re-run, or decide by hand.",
            "",
        ]
        for entry in judgment:
            labels = ", ".join(sorted(entry.pr.labels)) if entry.pr else "no pull request found"
            out.append(f"* TODO: {entry.headline} ({entry.link}) — labels: {labels}")
        out.append("")

    if excluded:
        out += ["## Not included", "", "Kept out of CHANGELOG.md by label.", ""]
        for entry in excluded:
            labels = ", ".join(sorted(entry.pr.labels)) if entry.pr else ""
            out.append(f"* {entry.headline} ({entry.link}) — {labels}")
        out.append("")

    return "\n".join(out)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "since_tag",
        nargs="?",
        help="Release tag to compare against. Defaults to the highest existing tag.",
    )
    parser.add_argument(
        "--pr-limit",
        type=int,
        default=100,
        help="How many merged pull requests to index (default: 100).",
    )
    args = parser.parse_args()

    tag = args.since_tag or latest_tag()
    head = describe_head()
    commits = commits_since(tag)
    if not commits:
        print(f"No commits since {tag}.")
        return

    pull_requests = merged_pull_requests(args.pr_limit)
    entries = []
    for sha, subject in commits:
        match = re.search(r"\(#(\d+)\)\s*$", subject)
        pr = pull_requests.get(int(match.group(1))) if match else None
        entries.append(Entry(sha=sha, subject=subject, pr=pr))

    print(render(tag, head, entries))


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        command = " ".join(error.cmd)
        sys.exit(f"Command failed: {command}\n{error.stderr.strip()}")
