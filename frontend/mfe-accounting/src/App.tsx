import React from 'react';
import { useLocation } from 'react-router-dom';
import AdminLedgerPage from './pages/AdminLedgerPage';
import CustomerLedgerPage from './pages/CustomerLedgerPage';
import { AccountingAdminApp } from './pages/AdvancedAccountingPages';

export default function App() {
  const pathname = useLocation().pathname.replace(/\/$/, '');
  const route = pathname.startsWith('/accounting') ? pathname.slice('/accounting'.length) || '/' : pathname || '/';
  if (route === '/ledger') return <CustomerLedgerPage />;
  if (route === '/admin') return <AdminLedgerPage />;
  return <AccountingAdminApp />;
}
