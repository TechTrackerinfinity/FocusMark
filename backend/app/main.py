"""Development API scaffold for future authenticated FocusLock rule sync.

The Android app intentionally works offline; do not make enforcement depend on this API.
"""

from datetime import UTC, datetime

from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware

from app.schemas import FocusSchedule, RulesResponse

app = FastAPI(title="FocusLock API", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_methods=["GET", "PUT"],
    allow_headers=["Content-Type", "Authorization"],
)

# Development only. Replace this with PostgreSQL plus authenticated device ownership before release.
_rules: dict[str, RulesResponse] = {}


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/v1/devices/{device_id}/rules", response_model=RulesResponse)
def get_rules(device_id: str) -> RulesResponse:
    response = _rules.get(device_id)
    if response is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No rules for this device")
    return response


@app.put("/v1/devices/{device_id}/rules", response_model=RulesResponse)
def save_rules(device_id: str, schedule: FocusSchedule) -> RulesResponse:
    if not device_id or len(device_id) > 128:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Invalid device id")
    response = RulesResponse(
        device_id=device_id,
        schedule=schedule,
        updated_at=datetime.now(UTC),
    )
    _rules[device_id] = response
    return response
