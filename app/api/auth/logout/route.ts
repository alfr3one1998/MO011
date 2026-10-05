import {signOutFromSupabase} from '../../../supabase-auth';
export const dynamic='force-dynamic';
export async function POST(){
  await signOutFromSupabase();
  return Response.json({ok:true},{headers:{'Cache-Control':'private, no-store'}});
}
