declare namespace Cloudflare {
  interface Env {
    DB?: D1Database;
    STORAGE_BACKEND?: "d1" | "supabase";
    SUPABASE_URL?: string;
    SUPABASE_SECRET_KEY?: string;
    BUCKET?: R2Bucket;
  }
}
