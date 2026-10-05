import {z} from 'zod';
import {claimAdmin,getSupabaseIdentity} from '../../../supabase-auth';
export const dynamic='force-dynamic';
export async function POST(req:Request){
  try{
    const identity=await getSupabaseIdentity();
    if(!identity)return Response.json({error:'سجل الدخول أولاً'},{status:401});
    const b=z.object({code:z.string().min(8).max(100)}).parse(await req.json());
    await claimAdmin(identity.accessToken,b.code);
    return Response.json({ok:true},{headers:{'Cache-Control':'private, no-store'}});
  }catch(e){
    const m=e instanceof Error?e.message:'';
    const error=m.includes('invalid_setup_code')?'كود تفعيل المدير غير صحيح':m.includes('admin_already_claimed')?'تم تفعيل حساب مدير بالفعل':'تعذر تفعيل حساب المدير';
    return Response.json({error},{status:400,headers:{'Cache-Control':'private, no-store'}});
  }
}
