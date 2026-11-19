def test_workouts_carry_the_role_for_offline_use(api):
    assert api("owner").get("/workouts").json()["role"] == "owner"
    assert api("viewer").get("/workouts").json()["role"] == "viewer"
