import {cookies} from 'next/headers';

const SUPABASE_URL='https://cvfkzqbpuwsvggsqrusp.supabase.co';
const SUPABASE_KEY='sb_publishable_5VGLcKYmatISHVBnrfCZxA_RuWFKuwL';
const ACCESS_COOKIE='mandoub_access';
const REFRESH_COOKIE='mandoub_refresh';

export type SupabaseIdentity={user:{id:string;email:string};accessToken:string};
type SessionPayload={access_token?:string;refresh_token?:string;expires_in?:number;user?:{id:string;email?:string};error?:string;error_description?:string;msg?:string};

const cookieBase={httpOnly:true,sameSite:'lax' as const,secure:process.env.NODE_ENV==='production',path:'/'};

async function sb(path:string,init:RequestInit={}){
  const headers=new Headers(init.headers);
  headers.set('apikey',SUPABASE_KEY);
  if(init.body&&!headers.has('content-type'))headers.set('content-type','application/json');
  return fetch(SUPABASE_URL+path,{...init,headers,cache:'no-store'});
}

async function saveSession(data:SessionPayload){
  if(!data.access_token||!data.refresh_token)return;
  const store=await cookies();
  store.set(ACCESS_COOKIE,data.access_token,{...cookieBase,maxAge:Math.max(60,data.expires_in||3600)});
  store.set(REFRESH_COOKIE,data.refresh_token,{...cookieBase,maxAge:60*60*24*60});
}

export async function clearSupabaseSession(){
  const store=await cookies();
  store.set(ACCESS_COOKIE,'',{...cookieBase,maxAge:0});
  store.set(REFRESH_COOKIE,'',{...cookieBase,maxAge:0});
}

async function userForToken(accessToken:string){
  const r=await sb('/auth/v1/user',{headers:{Authorization:'Bearer '+accessToken}});
  if(!r.ok)return null;
  const u=await r.json() as {id:string;email?:string};
  if(!u.id||!u.email)return null;
  return {id:u.id,email:u.email.toLowerCase()};
}

export async function getSupabaseIdentity():Promise<SupabaseIdentity|null>{
  const store=await cookies();
  let access=store.get(ACCESS_COOKIE)?.value||'';
  const refresh=store.get(REFRESH_COOKIE)?.value||'';
  if(access){
    const user=await userForToken(access);
    if(user)return {user,accessToken:access};
  }
  if(!refresh)return null;
  const rr=await sb('/auth/v1/token?grant_type=refresh_token',{method:'POST',body:JSON.stringify({refresh_token:refresh})});
  if(!rr.ok){await clearSupabaseSession();return null;}
  const data=await rr.json() as SessionPayload;
  await saveSession(data);
  access=data.access_token||'';
  const user=data.user?.id&&data.user.email?{id:data.user.id,email:data.user.email.toLowerCase()}:await userForToken(access);
  return user&&access?{user,accessToken:access}:null;
}

export async function signInWithPassword(email:string,password:string){
  const r=await sb('/auth/v1/token?grant_type=password',{method:'POST',body:JSON.stringify({email:email.trim().toLowerCase(),password})});
  const data=await r.json() as SessionPayload;
  if(!r.ok)throw Error(data.error_description||data.msg||data.error||'LOGIN_FAILED');
  await saveSession(data);
  return data;
}

export async function signUpWithPassword(email:string,password:string){
  const r=await sb('/auth/v1/signup',{method:'POST',body:JSON.stringify({email:email.trim().toLowerCase(),password})});
  const data=await r.json() as SessionPayload;
  if(!r.ok)throw Error(data.error_description||data.msg||data.error||'SIGNUP_FAILED');
  await saveSession(data);
  return data;
}

export async function signOutFromSupabase(){
  const identity=await getSupabaseIdentity();
  if(identity)await sb('/auth/v1/logout',{method:'POST',headers:{Authorization:'Bearer '+identity.accessToken}}).catch(()=>null);
  await clearSupabaseSession();
}

export async function getAdminMembership(accessToken:string,userId:string){
  const q=new URLSearchParams({select:'role',user_id:'eq.'+userId,limit:'1'});
  const r=await sb('/rest/v1/app_members?'+q.toString(),{headers:{Authorization:'Bearer '+accessToken}});
  if(!r.ok)return null;
  const rows=await r.json() as {role:string}[];
  return rows[0]||null;
}

export async function claimAdmin(accessToken:string,code:string){
  const r=await sb('/rest/v1/rpc/claim_admin',{method:'POST',headers:{Authorization:'Bearer '+accessToken},body:JSON.stringify({p_code:code.trim().toUpperCase()})});
  if(!r.ok){const e=await r.json().catch(()=>({})) as {message?:string;hint?:string};throw Error(e.message||e.hint||'CLAIM_FAILED');}
  return true;
}
