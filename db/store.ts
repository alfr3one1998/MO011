import {env} from 'cloudflare:workers';
import {eq,and} from 'drizzle-orm';
import {getDb} from './index';
import {workspace,drivers,orders} from './schema';
import {createSupabaseStore} from './supabase-rest.mjs';
const tables={workspace,drivers,orders};
type Table=keyof typeof tables;
export type Store={backend:'d1'|'supabase';list:(table:Table,filters?:Record<string,unknown>)=>Promise<any[]>;one:(table:Table,filters:Record<string,unknown>)=>Promise<any>;insert:(table:Table,row:any)=>Promise<any>;update:(table:Table,row:any,filters:Record<string,unknown>)=>Promise<any[]>};
export function getStore():Store {
 const runtime=env as Cloudflare.Env;
 if(runtime.STORAGE_BACKEND==='supabase') {
  if(!runtime.SUPABASE_URL||!runtime.SUPABASE_SECRET_KEY)throw Error('503');
  return {backend:'supabase',...createSupabaseStore({url:runtime.SUPABASE_URL,key:runtime.SUPABASE_SECRET_KEY})};
 }
 if(runtime.STORAGE_BACKEND&&runtime.STORAGE_BACKEND!=='d1')throw Error('503');
 const db=getDb();
 const where=(table:Table,filters:Record<string,unknown>)=>and(...Object.entries(filters).map(([key,value])=>{const col=(tables[table] as any)[key];if(!col)throw Error('Invalid field');return eq(col,value)}));
 return {backend:'d1',async list(table,filters={}){return db.select().from(tables[table]).where(where(table,filters))},async one(table,filters){return (await db.select().from(tables[table]).where(where(table,filters)).limit(1))[0]},async insert(table,row){return db.insert(tables[table]).values(row).returning()},async update(table,row,filters){if(!Object.keys(filters).length)throw Error('Update requires a filter');return db.update(tables[table]).set(row).where(where(table,filters)).returning()}};
}
