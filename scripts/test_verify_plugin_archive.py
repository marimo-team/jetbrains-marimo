from __future__ import annotations

import hashlib
import subprocess
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify_plugin_archive.sh")


def _write_archive(tmp_path: Path, payload: bytes = b"plugin") -> tuple[Path, str]:
    zip_path = tmp_path / "plugin.zip"
    zip_path.write_bytes(payload)
    digest = hashlib.sha256(payload).hexdigest()
    (tmp_path / "plugin.sha256").write_text(f"{digest}  plugin.zip\n")
    return zip_path, digest


def _run(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [str(SCRIPT), *args],
        check=False,
        text=True,
        capture_output=True,
    )


def test_accepts_matching_zip_and_digest(tmp_path: Path) -> None:
    zip_path, digest = _write_archive(tmp_path)
    result = _run(str(tmp_path))
    assert result.returncode == 0, result.stderr
    assert f"zip={zip_path}" in result.stdout
    assert f"sha256={digest}" in result.stdout
    assert "file=plugin.zip" in result.stdout


def test_rejects_digest_file_mismatch(tmp_path: Path) -> None:
    _write_archive(tmp_path)
    (tmp_path / "plugin.sha256").write_text("0" * 64 + "  plugin.zip\n")
    result = _run(str(tmp_path))
    assert result.returncode == 1
    assert "SHA-256 mismatch" in result.stderr


def test_rejects_expected_digest_mismatch(tmp_path: Path) -> None:
    _write_archive(tmp_path)
    result = _run(str(tmp_path), "0" * 64)
    assert result.returncode == 1
    assert "expected " + "0" * 64 in result.stderr


def test_rejects_missing_or_extra_files(tmp_path: Path) -> None:
    result = _run(str(tmp_path))
    assert result.returncode == 1
    _write_archive(tmp_path)
    (tmp_path / "other.zip").write_bytes(b"other")
    result = _run(str(tmp_path))
    assert result.returncode == 1
