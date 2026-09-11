#!/usr/bin/env python3
"""
Concurrent load test against the real backend + real local MySQL instance.
Simulates N residents hitting the app at roughly the same moment (worst-case
morning "what's my bin today" rush), not N requests spread over time.
"""
import concurrent.futures
import statistics
import sys
import time
import uuid

import requests

BASE = "http://localhost:8080"
N_USERS = int(sys.argv[1]) if len(sys.argv) > 1 else 50


def timed(fn):
    start = time.perf_counter()
    try:
        result = fn()
        ok = True
    except Exception as exc:  # noqa: BLE001
        result = str(exc)
        ok = False
    elapsed_ms = (time.perf_counter() - start) * 1000
    return ok, elapsed_ms, result


def register_and_login(i):
    email = f"loadtest-{uuid.uuid4()}@example.com"
    session = requests.Session()

    def do_register():
        r = session.post(f"{BASE}/api/auth/register", json={
            "email": email, "password": "LoadTestPass123"
        }, timeout=15)
        r.raise_for_status()
        return r.json()["token"]

    reg_ok, reg_ms, reg_result = timed(do_register)
    if not reg_ok:
        return {"user": i, "phase": "register", "ok": False, "ms": reg_ms, "error": reg_result}

    token = reg_result
    headers = {"Authorization": f"Bearer {token}"}

    def do_home_load():
        # What a resident actually does on opening the app: fetch upcoming
        # collections for their sector, then their saved preferences.
        r1 = session.get(f"{BASE}/api/collections/upcoming", params={"sector": "north", "days": 30},
                          headers=headers, timeout=15)
        r1.raise_for_status()
        r2 = session.get(f"{BASE}/api/preferences", headers=headers, timeout=15)
        r2.raise_for_status()
        return len(r1.json())

    home_ok, home_ms, home_result = timed(do_home_load)

    return {
        "user": i,
        "phase": "register+home",
        "ok": reg_ok and home_ok,
        "register_ms": reg_ms,
        "home_ms": home_ms,
        "error": None if home_ok else home_result,
    }


def summarize(results, label):
    ok = [r for r in results if r["ok"]]
    failed = [r for r in results if not r["ok"]]
    print(f"\n=== {label}: {len(results)} concurrent virtual users ===")
    print(f"Succeeded: {len(ok)}/{len(results)}  ({100 * len(ok) / len(results):.1f}%)")
    if failed:
        print(f"Failed: {len(failed)}")
        for f in failed[:5]:
            print(f"  user {f['user']}: {f.get('error')}")
    if ok:
        reg_times = [r["register_ms"] for r in ok]
        home_times = [r["home_ms"] for r in ok]
        print(f"Register latency  (ms): avg={statistics.mean(reg_times):.0f}  "
              f"p95={sorted(reg_times)[int(len(reg_times) * 0.95)]:.0f}  max={max(reg_times):.0f}")
        print(f"Home-load latency (ms): avg={statistics.mean(home_times):.0f}  "
              f"p95={sorted(home_times)[int(len(home_times) * 0.95)]:.0f}  max={max(home_times):.0f}")


def main():
    print(f"Load test starting: {N_USERS} concurrent virtual users against {BASE}")
    wall_start = time.perf_counter()

    with concurrent.futures.ThreadPoolExecutor(max_workers=N_USERS) as pool:
        results = list(pool.map(register_and_login, range(N_USERS)))

    wall_ms = (time.perf_counter() - wall_start) * 1000
    summarize(results, f"{N_USERS} residents registering + loading home page simultaneously")
    print(f"\nTotal wall-clock time for all {N_USERS} to finish: {wall_ms:.0f} ms")


if __name__ == "__main__":
    main()
