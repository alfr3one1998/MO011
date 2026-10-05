import {getChatGPTUser} from '../../chatgpt-auth';
import {getStore} from '../../../db/store';
import {z} from 'zod';
export const dynamic='force-dynamic';
async function context(){
 const user=await getChatGPTUser();if(!user)throw Error('401');
 const db=getStore();let w=await db.one('workspace',{id:1});
 if(!w){
  // A migrated backend must retain the existing owner's identity; never claim it on first login.
  if(db.backend==='supabase')throw Error('503');
  try{await db.insert('workspace',{id:1,owner:user.userId,name:'تاج الملكة'})}catch{ /* concurrent owner initialization */ }
  w=await db.one('workspace',{id:1});if(!w)throw Error('503');
 }
 const admin=w.owner===user.userId;const driver=await db.one('drivers',{email:user.email.toLowerCase()});
 if(!admin&&!driver)throw Error('403');return {db,admin,driver,w};
}
function fail(e:unknown){const m=e instanceof Error?e.message:'';const errors:Record<string,string>={'401':'سجل الدخول أولاً','403':'ليس لديك صلاحية الدخول','409':'تغير الطلب أو البيانات موجودة مسبقًا؛ حدّث الصفحة','503':'تعذر الاتصال بقاعدة البيانات. يرجى المحاولة لاحقًا'};return Response.json({error:errors[m]||'راجع البيانات المدخلة وحاول مجددًا'},{status:errors[m]?Number(m):400});}
export async function GET(){try{const c=await context();const [ds,os]=await Promise.all([c.admin?c.db.list('drivers'):Promise.resolve([c.driver]),c.db.list('orders',c.admin?{}:{driverId:c.driver.id})]);return Response.json({admin:c.admin,name:c.w.name,driverId:c.driver?.id,drivers:ds,orders:os},{headers:{'Cache-Control':'no-store'}})}catch(e){return fail(e)}}
const short=z.string().trim().min(1).max(150),phone=z.string().regex(/^\+?[\d\s-]{8,20}$/),money=z.number().int().min(0).max(100000000);
export async function POST(req:Request){try{
 if(req.headers.get('origin')!==new URL(req.url).origin)return Response.json({error:'طلب غير مسموح'},{status:403});
 const c=await context(),b=z.record(z.any()).parse(await req.json());
 if(b.action==='status'){
  const id=short.parse(b.id),o=await c.db.one('orders',{id});
  if(!o||(!c.admin&&o.driverId!==c.driver.id))throw Error('403');
  const next:Record<string,string>={new:'received',received:'onway',onway:'delivered'};
  const status=z.enum(['received','onway','delivered']).parse(b.status);
  if(next[o.status]!==status||!o.driverId)throw Error('409');
  const updated=await c.db.update('orders',{status,delivered:status==='delivered'?new Date().toISOString():null},{id,status:o.status,driverId:o.driverId});
  if(!updated.length)throw Error('409');
 }else{
  if(!c.admin)throw Error('403');
  if(b.action==='driver'){
   const v=z.object({name:short,email:z.string().trim().email().max(200),phone}).parse(b);
   await c.db.insert('drivers',{...v,id:crypto.randomUUID(),email:v.email.toLowerCase()});
  }else if(b.action==='order'){
   const v=z.object({customer:short,phone,address:z.string().trim().min(3).max(500),district:short,amount:money,fee:money,payment:z.enum(['cash','paid']),driverId:z.string().nullable(),notes:z.string().max(1000)}).parse(b);
   if(v.driverId&&!await c.db.one('drivers',{id:v.driverId}))throw Error('400');
   await c.db.insert('orders',{...v,id:crypto.randomUUID(),status:'new',created:new Date().toISOString(),settled:0});
  }else if(b.action==='assign'){
   const id=short.parse(b.id),driverId=short.parse(b.driverId);
   if(!await c.db.one('drivers',{id:driverId}))throw Error('400');
   if(!(await c.db.update('orders',{driverId},{id,status:'new'})).length)throw Error('409');
  }else if(b.action==='settle'){
   const ids=z.array(short).min(1).max(500).parse(b.ids);
   for(const id of ids)await c.db.update('orders',{settled:1},{id,status:'delivered',settled:0});
  }else if(b.action==='settings')await c.db.update('workspace',{name:short.parse(b.name)},{id:1});
  else throw Error('400');
 }
 return Response.json({ok:true});
}catch(e){return fail(e)}}
