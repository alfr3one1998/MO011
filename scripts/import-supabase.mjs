// Usage: SUPABASE_URL=... SUPABASE_SECRET_KEY=... node scripts/import-supabase.mjs /secure/export.json
// Export format: {workspace:[{id,owner,name}],drivers:[...],orders:[...]}
// Supply secrets through the execution environment, never CLI arguments.
import {readFile} from 'node:fs/promises';
import {createSupabaseStore} from '../db/supabase-rest.mjs';
const input=process.argv[2];if(!input)throw Error('Provide a protected JSON export path');
const payload=JSON.parse(await readFile(input,'utf8'));
if(!Array.isArray(payload.workspace)||payload.workspace.length!==1||payload.workspace[0].id!==1||!payload.workspace[0].owner||!Array.isArray(payload.drivers)||!Array.isArray(payload.orders))throw Error('Invalid export; original owner must be preserved');
const store=createSupabaseStore({url:process.env.SUPABASE_URL,key:process.env.SUPABASE_SECRET_KEY});
for(const table of ['workspace','drivers','orders']){
 const rows=payload[table];const existing=await store.list(table);const byId=new Map(existing.map(row=>[row.id,row]));
 for(const original of rows){
  const row={...original};if(table==='orders'&&'driver_id' in row){row.driverId=row.driver_id;delete row.driver_id;}
  const stored=byId.get(row.id);
  if(stored){
   for(const key of Object.keys(row)){
    let a=stored[key],b=row[key];
    if(['created','delivered'].includes(key)&&a&&b){a=new Date(a).toISOString();b=new Date(b).toISOString();}
    if(a!==b)throw Error(`Target conflict in ${table}; stopping without overwriting`);
   }
  }else await store.insert(table,row);
 }
 const verified=await store.list(table);
 if(verified.length!==rows.length||rows.some(row=>!verified.some(v=>v.id===row.id)))throw Error(`Verification failed: ${table}`);
 console.log(`${table}: ${verified.length} records verified`);
}
console.log('Import completed. Keep writes paused until cutover and application smoke tests finish.');
