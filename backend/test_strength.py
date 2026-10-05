from core import strength_summary, trim_catalog

CATALOG = trim_catalog({"categories": {
    "SQUAT": {"primaryMuscles": ["QUADS", "GLUTES"], "secondaryMuscles": ["HAMSTRINGS"], "exercises": {}},
    "HIP_RAISE": {"primaryMuscles": ["GLUTES"], "secondaryMuscles": [""], "exercises": {
        "BARBELL_HIP_THRUST_ON_FLOOR": {"primaryMuscles": ["GLUTES"], "secondaryMuscles": ["HAMSTRINGS", "HIPS"]}}},
}})


def test_counts_sets_per_muscle_and_puts_unknown_last():
    raw = {"activeSets": 7, "totalReps": 80, "summarizedExerciseSets": [
        {"category": "UNKNOWN", "sets": 4, "reps": 30, "duration": 60000},
        {"category": "SQUAT", "sets": 2, "reps": 30, "duration": 90000},
        {"category": "HIP_RAISE", "subCategory": "BARBELL_HIP_THRUST_ON_FLOOR", "sets": 1, "reps": 20},
    ]}
    s = strength_summary(raw, CATALOG)
    assert [e["category"] for e in s["exercises"]] == ["SQUAT", "HIP_RAISE", "UNKNOWN"]
    assert s["muscles"] == {
        "QUADS": {"primary": 2, "secondary": 0},
        "GLUTES": {"primary": 3, "secondary": 0},
        "HAMSTRINGS": {"primary": 0, "secondary": 3},
        "HIPS": {"primary": 0, "secondary": 1},
    }
    assert s["exercises"][0]["duration_s"] == 90
    assert s["total_sets"] == 7


def test_empty_catalog_and_no_sets():
    assert strength_summary({}, CATALOG) is None
    assert strength_summary({"summarizedExerciseSets": [{"category": "CURL", "sets": 3}]}, {})["muscles"] == {}
