import type { Metadata } from 'next';
import './globals.css';
import MapsLinkEnhancer from './maps-link-enhancer';
import AuthLinkEnhancer from './auth-link-enhancer';
export const metadata: Metadata={title:'مندوب | إدارة التوصيل',description:'طلباتك ومناديبك وحساباتك في مكان واحد'};
export default function Layout({children}:{children:React.ReactNode}){return <html lang="ar" dir="rtl"><body>{children}<MapsLinkEnhancer/><AuthLinkEnhancer/></body></html>}
