from datetime import datetime

from core import identity, invite_rows


def test_identity_without_access_has_no_role():
    ident = identity({"_id": "u1", "name": "Ibu", "email": "ibu@example.com"}, None, "s1")
    assert ident["role"] is None and ident["data_user_id"] is None and ident["kind"] == "session"


def test_identity_takes_role_and_data_from_linked_token():
    ident = identity({"_id": "u1", "name": "Ibu"}, {"_id": "t1", "role": "viewer", "data_user_id": "owner"}, "s1")
    assert (ident["role"], ident["data_user_id"], ident["access_id"]) == ("viewer", "owner", "t1")


def test_invite_rows_group_users_skip_revoked_newest_first():
    tokens = [
        {"_id": "a", "name": "ibu", "role": "viewer", "created_at": datetime(2026, 10, 1)},
        {"_id": "b", "name": "adik", "role": "viewer", "created_at": datetime(2026, 10, 20)},
        {"_id": "c", "name": "lama", "role": "viewer", "created_at": datetime(2026, 9, 1), "revoked": True},
    ]
    users = [{"name": "Ibu", "access_id": "a"}, {"email": "x@example.com", "access_id": "a"}, {"name": "Z", "access_id": "c"}]
    rows = invite_rows(tokens, users)
    assert [r["name"] for r in rows] == ["adik", "ibu"]
    assert rows[1]["users"] == ["Ibu", "x@example.com"] and rows[0]["users"] == []
