/** Server-only Supabase Data API transport. Never import from a client component. */
export function createSupabaseStore({url,key,fetcher=fetch}) {
 const origin=new URL(url);
 if(origin.protocol!=='https:' || origin.username || origin.password || origin.search || origin.hash || origin.pathname!=='/') throw new Error('Invalid Supabase URL');
 if(!key?.startsWith('sb_secret_')) throw new Error('A server-only Supabase secret key is required');
 const tableNames={workspace:'mandoub_workspace',drivers:'mandoub_drivers',orders:'mandoub_orders'};
 async function request(table,method='GET',filters={},body,range){
  if(!tableNames[table]) throw new Error('Unknown table');
  const target=new URL('/rest/v1/'+tableNames[table],origin);
  target.searchParams.set('select','*');
  for(const [field,value] of Object.entries(filters)){
   if(!/^[a-zA-Z][a-zA-Z0-9]*$/.test(field)) throw new Error('Invalid field');
   target.searchParams.set(field,`eq.${JSON.stringify(value)}`);
  }
  if(range){target.searchParams.set('order','id.asc');target.searchParams.set('offset',String(range.offset));target.searchParams.set('limit',String(range.limit));}
  const response=await fetcher(target,{method,headers:{apikey:key,'Content-Type':'application/json',Prefer:'return=representation'},body:body===undefined?undefined:JSON.stringify(body),cache:'no-store',signal:AbortSignal.timeout(15000),redirect:'error'});
  if(!response.ok){if(response.status===409)throw new Error('409');throw new Error('503');}
  return response.status===204?[]:await response.json();
 }
 return {
  async list(table,filters={}){const result=[];const size=500;for(let offset=0;;){const page=await request(table,'GET',filters,undefined,{offset,limit:size});result.push(...page);if(page.length===0)return result;offset+=page.length;}},
  async one(table,filters){return (await request(table,'GET',filters,undefined,{offset:0,limit:1}))[0];},
  async insert(table,row){return request(table,'POST',{},row);},
  async update(table,row,filters){if(!Object.keys(filters).length)throw new Error('Update requires a filter');return request(table,'PATCH',filters,row);}
 };
}
