import type { Metadata } from 'next';
import './globals.css';
export const metadata: Metadata={title:'مندوب | إدارة التوصيل',description:'طلباتك ومناديبك وحساباتك في مكان واحد'};
export default function Layout({children}:{children:React.ReactNode}){return <html lang="ar" dir="rtl"><body>{children}</body></html>}
