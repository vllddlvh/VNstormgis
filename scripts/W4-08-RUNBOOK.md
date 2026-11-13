# W4-08 nightly runbook

The workflow `.github/workflows/week4-e2e-nightly.yml` runs daily at 02:20 UTC (09:20 Vietnam time) and accepts manual `workflow_dispatch`. It has no PR trigger. The hosted runner owns its PostGIS/Redis containers and uses a random DB password. The backend starts with external weather sync and Gemini disabled. Testcontainers starts separate PostGIS/WireMock fixtures for the generation and fault paths.

## Configure and verify

1. On the repository, set the Actions secret `W4_FAILURE_WEBHOOK_URL` to an approved HTTPS incoming webhook that accepts a JSON object with a `text` field. QA/DevOps owns the destination and must verify delivery to the intended recipient. The job fails visibly when the secret is absent on a failed run.
2. Check repository/org Actions artifact retention permits at least 30 days. The workflow requests `retention-days: 30`.
3. Dispatch once with `failure_probe=false`, then once with `failure_probe=true`. The probe fails G-02 after focused IT and should still upload `summary.json`, logs, and JUnit XML. Verify webhook delivery, run URL/SHA/gate, and the artifact in the failed run. The probe does not induce a JUnit assertion failure.
4. Observe one real scheduled run. Record all three run IDs/URLs and artifact expiry in `docs/week4/W4-08 - Verification report.md`. Confirm branch protection does not make this job a required PR check.

For local use, start the stack and backend per `RUN_LOCAL.md`, set `JAVA_HOME` to JDK 21, and run `bash scripts/verify-week4-pipeline.sh` from any directory. `BACKEND_BASE_URL`, `WAIT_TIMEOUT_SECONDS` (1–600 seconds), and `ARTIFACT_DIR` may be overridden. The script only reads live API data and does not stop Redis. `summary.json` records the failing gate, exit code, counts, and any empty-collection detail skip. No environment file or secret should be uploaded.

If a run fails, inspect `summary.json` first, then the relevant Gradle log or JUnit report. For a backend startup failure, check `backend.log` in the uploaded artifact. The workflow uploads evidence before cleanup; cleanup only targets containers it started on the job runner.
