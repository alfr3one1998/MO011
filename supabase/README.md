# Supabase cutover — prepared, not activated

The live Site still uses D1. No Supabase project is linked, no schema has been applied remotely, and no existing data has been copied yet. The Supabase plugin is installed but no project tools were exposed in the authoring session.

## Architecture

Keep Sites authentication and existing owner/driver permissions. Only the server calls Supabase over HTTPS using the `apikey` header and a modern secret key. No browser credentials, public database grants, or user-editable JWT role claims. RLS is enabled and browser roles have no grants or policies. The server remains responsible for role and order ownership checks.

## Activation checklist

1. Connect the Supabase plugin and select the intended project. Inspect existing schema before applying `schema.sql`; it uses prefixed tables to avoid touching unrelated objects. Run database security advisors and a test query afterwards.
2. Pause writes to the old backend. Take a protected export of all D1 tables, including `workspace.owner`. Never add that export to GitHub. Convert the export to JSON with arrays `workspace`, `drivers`, and `orders`. Order `driver_id` or `driverId` are both supported by the import script.
3. Configure `SUPABASE_URL` and `SUPABASE_SECRET_KEY` securely in the migration environment and run `node scripts/import-supabase.mjs /secure/export.json`. The script imports missing rows, rejects conflicting rows, preserves owner/IDs/amounts/statuses, and verifies counts/IDs. It can resume a partial import; it does not overwrite rows. Keep the old database and export for recovery.
4. Set server runtime variables `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, and `STORAGE_BACKEND=supabase` on the existing Site. Never use `NEXT_PUBLIC_` for the secret. Preserve the D1 binding for a controlled rollback.
5. Build/publish this branch. Verify authenticated owner and driver sessions, denied unauthorized reads, creating an order, assignment, delivery, cash totals, and settlement against the actual project. Then resume writes. If new records have already been written to Supabase, reconcile them before reverting to D1.

The switch is explicit: missing Supabase configuration returns an error; it never silently writes into D1. An empty Supabase workspace is not automatically claimed by the next visitor: the migrated owner row is required.

## Local checks

`node --test tests/supabase-rest.test.mjs`

`node node_modules/typescript/bin/tsc --noEmit`

`pnpm build`

Transport tests use mocks, not a live Supabase database. Database schema validation, advisors and a live query remain pending account access.
