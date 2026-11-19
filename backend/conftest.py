import mongomock
import pytest
from fastapi.testclient import TestClient

import core


@pytest.fixture
def mdb(monkeypatch):
    """An in-memory Mongo in place of the real one, so endpoint tests never touch production."""
    d = mongomock.MongoClient()["garmin_fit_test"]
    monkeypatch.setattr(core, "_db", d)
    return d


@pytest.fixture
def api(mdb):
    """call(role) -> a TestClient signed in with a legacy access token of that role on the owner's data."""
    from api.index import app

    def call(role="owner"):
        token = f"test-{role}"
        mdb.access_tokens.update_one({"_id": core.sha256(token)},
                                     {"$set": {"name": role, "role": role, "data_user_id": core.OWNER, "revoked": False}}, upsert=True)
        return TestClient(app, headers={"Authorization": f"Bearer {token}"})
    return call
