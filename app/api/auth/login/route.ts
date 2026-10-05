import {z} from 'zod';
import {signInWithPassword} from '../../../supabase-auth';
export const dynamic='force-dynamic';
export async function POST(req:Request){
  try{
    const b=z.object({email:z.string().email().max(200),password:z.string().min(6).max(200)}).parse(await req.json());
    await signInWithPassword(b.email,b.password);
    return Response.json({ok:true},{headers:{'Cache-Control':'private, no-store'}});
  }catch(e){
    const m=e instanceof Error?e.message:'LOGIN_FAILED';
    return Response.json({error:m.includes('Invalid login credentials')?'الإيميل أو كلمة المرور غير صحيحة':'تعذر تسجيل الدخول'},{status:400,headers:{'Cache-Control':'private, no-store'}});
  }
}
