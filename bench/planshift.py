"""A minimal client for the PlanShift API, shared by the benchmark scripts. Standard library only."""

import json
import time
import urllib.request


class PlanShift:
    def __init__(self, base_url="http://localhost:8080"):
        self.base_url = base_url.rstrip("/")

    def call(self, path, body=None):
        request = urllib.request.Request(
            self.base_url + path,
            data=json.dumps(body).encode() if body is not None else None,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.load(response)

    def run(self, path, body, timeout_s=900):
        """Starts an experiment and waits for it to finish."""
        experiment = self.call(path, body)
        deadline = time.time() + timeout_s
        while experiment["status"] in ("QUEUED", "RUNNING"):
            if time.time() > deadline:
                raise TimeoutError(f"experiment {experiment['experimentId']} did not finish")
            time.sleep(1)
            experiment = self.call(f"/api/experiments/{experiment['experimentId']}")
        if experiment["status"] != "COMPLETED":
            raise RuntimeError(f"experiment {experiment['experimentId']} {experiment['status']}")
        return experiment
