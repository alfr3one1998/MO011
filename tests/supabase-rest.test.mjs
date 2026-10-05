import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createSupabaseStore} from '../db/supabase-rest.mjs';
const config={url:'https://test.supabase.co',key:'sb_secret_test'};
test('continues pagination when the server caps pages below the requested size',async()=>{const offsets=[];const store=createSupabaseStore({...config,fetcher:async(url)=>{offsets.push(url.searchParams.get('offset'));return Response.json(offsets.length===1?[{id:'1'},{id:'2'}]:offsets.length===2?[{id:'3'}]:[])}});assert.equal((await store.list('orders')).length,3);assert.deepEqual(offsets,['0','2','3'])});
test('assignment and status predicates survive transport; secret stays in header',async()=>{let seen;const store=createSupabaseStore({...config,fetcher:async(url,init)=>{seen={url,init};return Response.json([])}});assert.deepEqual(await store.update('orders',{status:'received'},{id:'a',status:'new',driverId:'d'}),[]);assert.equal(seen.url.searchParams.get('driverId'),'eq."d"');assert.equal(seen.init.headers.apikey,config.key);assert.equal(seen.url.href.includes(config.key),false);assert.equal(seen.init.redirect,'error')});
test('refuses unfiltered writes',async()=>{const store=createSupabaseStore(config);await assert.rejects(store.update('orders',{settled:1},{}),/filter/)});
test('does not leak server response details',async()=>{const store=createSupabaseStore({...config,fetcher:async()=>new Response('sensitive detail',{status:500})});await assert.rejects(store.one('workspace',{id:1}),/^Error: 503$/)});
test('rejects insecure URL and public keys',()=>{assert.throws(()=>createSupabaseStore({...config,url:'http://test.supabase.co'}));assert.throws(()=>createSupabaseStore({...config,key:'sb_publishable_test'}))});
