'use client';
import {useState} from 'react';
import styles from './login.module.css';

export default function LoginPage(){
  const [mode,setMode]=useState<'login'|'signup'>('login');
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const [managerSetup,setManagerSetup]=useState(false);

  async function submit(e:React.FormEvent<HTMLFormElement>){
    e.preventDefault();setBusy(true);setError('');setNotice('');
    const f=new FormData(e.currentTarget);const email=String(f.get('email')||'').trim();const password=String(f.get('password')||'');const code=String(f.get('setupCode')||'').trim();
    try{
      const r=await fetch('/api/auth/'+mode,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password})});
      const j=await r.json() as {ok?:boolean,error?:string,signedIn?:boolean,needsConfirmation?:boolean};
      if(!r.ok)throw Error(j.error||'تعذر إكمال العملية');
      if(mode==='signup'&&j.needsConfirmation){setNotice('تم إنشاء الحساب. افتح بريدك وأكد الإيميل ثم ارجع وسجّل الدخول.');setMode('login');return;}
      if(managerSetup&&code){const cr=await fetch('/api/auth/claim-admin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code})});const cj=await cr.json() as {error?:string};if(!cr.ok)throw Error(cj.error||'تعذر تفعيل المدير');}
      const test=await fetch('/api/data',{cache:'no-store'});
      if(test.ok){location.href='/';return;}
      if(test.status===403)throw Error('الحساب صحيح لكنه غير مصرح له. لو أنت المدير فعّل خيار "تفعيل حساب المدير لأول مرة"، ولو مندوب تأكد أن الإدارة أضافت نفس الإيميل في قائمة المناديب.');
      throw Error('تعذر فتح لوحة التحكم');
    }catch(e){setError(e instanceof Error?e.message:'حدث خطأ غير متوقع');}
    finally{setBusy(false)}
  }

  return <main className={styles.page}><section className={styles.card}>
    <div className={styles.logo}>م</div><h1>مندوب</h1><p className={styles.subtitle}>إدارة التوصيل من حساباتك الخاصة</p>
    <div className={styles.tabs}><button className={mode==='login'?styles.active:''} onClick={()=>{setMode('login');setError('');setNotice('')}}>تسجيل الدخول</button><button className={mode==='signup'?styles.active:''} onClick={()=>{setMode('signup');setError('');setNotice('')}}>إنشاء حساب</button></div>
    <form onSubmit={submit}>
      <label>البريد الإلكتروني<input name="email" type="email" autoComplete="email" required placeholder="name@example.com" dir="ltr"/></label>
      <label>كلمة المرور<input name="password" type="password" autoComplete={mode==='login'?'current-password':'new-password'} minLength={6} required placeholder="••••••••" dir="ltr"/></label>
      <label className={styles.check}><input type="checkbox" checked={managerSetup} onChange={e=>setManagerSetup(e.target.checked)}/><span>تفعيل حساب المدير لأول مرة</span></label>
      {managerSetup&&<label>كود تفعيل المدير<input name="setupCode" type="text" required placeholder="XXXX-XXXX-XXXX-XXXX" dir="ltr" autoCapitalize="characters"/></label>}
      {error&&<div className={styles.error}>{error}</div>}{notice&&<div className={styles.notice}>{notice}</div>}
      <button className={styles.submit} disabled={busy}>{busy?'جاري التنفيذ…':mode==='login'?'دخول':'إنشاء الحساب'}</button>
    </form>
    <p className={styles.note}>حسابات الدخول محفوظة في مشروع Supabase الخاص بك. لا يوجد تسجيل دخول عبر OpenAI أو ChatGPT.</p>
  </section></main>;
}
