'use client';
import {useEffect} from 'react';

export default function AuthLinkEnhancer(){
  useEffect(()=>{
    if(location.pathname==='/login')return;
    fetch('/api/data',{cache:'no-store'}).then(r=>{if(r.status===401)location.replace('/login')}).catch(()=>null);
    const patch=()=>{
      document.querySelectorAll<HTMLAnchorElement>('a[href^="/signin-with-chatgpt"]').forEach(a=>{a.href='/login';a.removeAttribute('target')});
      document.querySelectorAll<HTMLAnchorElement>('a[href^="/signout-with-chatgpt"]').forEach(a=>{
        if(a.dataset.supabaseLogout)return;a.dataset.supabaseLogout='1';a.href='/login';a.removeAttribute('target');
        a.addEventListener('click',async e=>{e.preventDefault();try{await fetch('/api/auth/logout',{method:'POST'})}finally{location.href='/login'}});
      });
    };
    patch();const observer=new MutationObserver(patch);observer.observe(document.body,{childList:true,subtree:true});return()=>observer.disconnect();
  },[]);
  return null;
}
