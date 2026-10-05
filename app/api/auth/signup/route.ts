import {z} from 'zod';
import {signUpWithPassword} from '../../../supabase-auth';
export const dynamic='force-dynamic';
export async function POST(req:Request){
  try{
    const b=z.object({email:z.string().email().max(200),password:z.string().min(6).max(200)}).parse(await req.json());
    const data=await signUpWithPassword(b.email,b.password);
    return Response.json({ok:true,signedIn:!!data.access_token,needsConfirmation:!data.access_token},{headers:{'Cache-Control':'private, no-store'}});
  }catch(e){
    const m=e instanceof Error?e.message:'SIGNUP_FAILED';
    const friendly=m.toLowerCase().includes('already')?'الحساب موجود بالفعل، استخدم تسجيل الدخول':m.toLowerCase().includes('password')?'كلمة المرور ضعيفة أو غير مقبولة':'تعذر إنشاء الحساب';
    return Response.json({error:friendly},{status:400,headers:{'Cache-Control':'private, no-store'}});
  }
}
