'use client';

import {useEffect} from 'react';

type ApiOrder={id:string;mapsUrl?:string};
type ApiData={orders?:ApiOrder[]};

const isGoogleMapsUrl=(value:string)=>/^https:\/\/(maps\.app\.goo\.gl|goo\.gl\/maps|(?:www\.)?google\.[^/]+\/maps)/i.test(value);

export default function MapsLinkEnhancer(){
  useEffect(()=>{
    let stopped=false;
    let timer:number|undefined;
    let lastPrefix='';

    const addMapsField=()=>{
      const form=[...document.querySelectorAll<HTMLFormElement>('.dialog form')].find(x=>x.querySelector('input[name="customer"]'));
      if(!form||form.querySelector('input[name="mapsUrl"]'))return;
      const label=document.createElement('label');
      label.dataset.mapsField='true';
      label.append(document.createTextNode('رابط موقع العميل على Google Maps'));
      const input=document.createElement('input');
      input.name='mapsUrl';
      input.type='url';
      input.dir='ltr';
      input.maxLength=1000;
      input.autocomplete='off';
      input.placeholder='https://maps.app.goo.gl/...';
      input.setAttribute('inputmode','url');
      label.appendChild(input);
      const address=form.querySelector('input[name="address"]')?.closest('label');
      address?.after(label);
      if(!address)form.prepend(label);
    };

    const applySavedMapLink=async()=>{
      const dialog=document.querySelector<HTMLElement>('.dialog');
      if(!dialog)return;
      const idText=dialog.querySelector<HTMLElement>('.subtle')?.textContent?.trim()||'';
      const prefix=idText.startsWith('#')?idText.slice(1):'';
      const direction=[...dialog.querySelectorAll<HTMLAnchorElement>('a')].find(a=>a.textContent?.includes('الاتجاهات'));
      if(!prefix||!direction||prefix===lastPrefix)return;
      lastPrefix=prefix;
      try{
        const response=await fetch('/api/data',{cache:'no-store'});
        if(!response.ok)return;
        const data=await response.json() as ApiData;
        const order=data.orders?.find(o=>o.id.startsWith(prefix));
        if(stopped||!order?.mapsUrl||!isGoogleMapsUrl(order.mapsUrl))return;
        direction.href=order.mapsUrl;
        direction.title='فتح رابط الموقع الذي أضافه المتجر';
        direction.dataset.savedMapsUrl='true';
      }catch{}
    };

    const enhance=()=>{
      addMapsField();
      void applySavedMapLink();
    };
    const observer=new MutationObserver(()=>{
      window.clearTimeout(timer);
      timer=window.setTimeout(enhance,80);
    });
    observer.observe(document.body,{childList:true,subtree:true});
    enhance();
    return()=>{stopped=true;observer.disconnect();window.clearTimeout(timer)};
  },[]);
  return null;
}
