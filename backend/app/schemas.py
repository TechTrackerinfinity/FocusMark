from datetime import datetime

from pydantic import BaseModel, Field, field_validator


class FocusSchedule(BaseModel):
    enabled: bool = False
    start_minute: int = Field(default=540, ge=0, le=1439)
    end_minute: int = Field(default=1020, ge=0, le=1439)
    blocked_packages: set[str] = Field(default_factory=set, max_length=100)

    @field_validator("blocked_packages")
    @classmethod
    def package_names_are_valid(cls, values: set[str]) -> set[str]:
        if any(not package or len(package) > 255 for package in values):
            raise ValueError("Package names must be non-empty and no longer than 255 characters")
        return values


class RulesResponse(BaseModel):
    device_id: str
    schedule: FocusSchedule
    updated_at: datetime
