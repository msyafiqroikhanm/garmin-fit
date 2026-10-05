from cli import summary_doc

BASE = {
    "period": "weekly",
    "range": {"from": "2026-09-28", "to": "2026-10-04"},
    "headline": "Minggu yang solid",
    "sections": [{"title": "Recovery", "body": "HRV stabil."}],
}


def test_summary_without_en_keeps_indonesian_only():
    assert "en" not in summary_doc(BASE)


def test_summary_with_en_is_stored():
    en = {"headline": "A solid week", "sections": [{"title": "Recovery", "body": "HRV held steady."}], "extra": 1}
    assert summary_doc({**BASE, "en": en})["en"] == {"headline": "A solid week", "sections": en["sections"]}


def test_malformed_en_is_rejected():
    assert summary_doc({**BASE, "en": {"headline": "A solid week"}}) is None
    assert summary_doc({**BASE, "en": "A solid week"}) is None
