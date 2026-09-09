# Matrix tooling

`report.py` merges fresh generated simulation evidence with the dedicated Minecraft GameTest log and fails closed if expected coverage is missing.

Run it after the matrix tests:

```bash
python tools/matrix/report.py \
  --server-log build/ci/car-matrix/server.log \
  --output-dir build/ci/car-matrix/report
```

It writes JSON, Markdown and HTML reports. The full coverage model, reproduction commands and extension rules are documented in [`docs/MATRIX-TESTING.md`](../../docs/MATRIX-TESTING.md).
