#!/usr/bin/env python3
"""
Measures what the Redis cache is actually worth on the read path.

Fires N concurrent GETs at the two endpoints every home page load hits
(`/api/collections/upcoming` and `/api/notices/active`) against the real
running backend. Run it once with the backend started normally and once with
`CACHE_TYPE=none` to get the before/after numbers - the script does not (and
should not) reach into the app to toggle the cache itself.

    python3 cache_benchmark.py 200
"""
import concurrent.futures
import statistics
import sys
import time

import requests

BASE = "http://localhost:8080"
N = int(sys.argv[1]) if len(sys.argv) > 1 else 200
ENDPOINTS = [
    "/api/collections/upcoming?sector=north&days=30",
    "/api/notices/active",
]


def one_page_load(i):
    """One resident opening the home page: both reads, as the frontend does."""
    session = requests.Session()
    start = time.perf_counter()
    try:
        for path in ENDPOINTS:
            r = session.get(f"{BASE}{path}", timeout=30)
            r.raise_for_status()
        return True, (time.perf_counter() - start) * 1000
    except Exception:  # noqa: BLE001
        return False, (time.perf_counter() - start) * 1000


def main():
    # Warm-up: the first request of a cold cache pays for the miss, and we are
    # measuring steady state, not startup.
    for path in ENDPOINTS:
        requests.get(f"{BASE}{path}", timeout=30)

    wall_start = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=N) as pool:
        results = list(pool.map(one_page_load, range(N)))
    wall = time.perf_counter() - wall_start

    ok = [ms for success, ms in results if success]
    failed = len(results) - len(ok)
    ok.sort()

    print(f"concurrent home-page loads : {N}")
    print(f"successful                 : {len(ok)}/{N}  (failed: {failed})")
    if ok:
        print(f"latency avg                : {statistics.mean(ok):.0f} ms")
        print(f"latency p50                : {ok[len(ok) // 2]:.0f} ms")
        print(f"latency p95                : {ok[int(len(ok) * 0.95) - 1]:.0f} ms")
        print(f"latency max                : {ok[-1]:.0f} ms")
    print(f"wall clock                 : {wall:.2f} s")
    print(f"throughput                 : {N / wall:.0f} page loads/s")


if __name__ == "__main__":
    main()
