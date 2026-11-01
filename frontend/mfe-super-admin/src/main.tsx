import React from 'react';
import ReactDOM from 'react-dom/client';
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';
import App from './App';

const theme = createTheme({
  palette: { mode: 'light', primary: { main: '#4F46E5' }, background: { default: '#F4F7FB' } },
  typography: { fontFamily: 'Inter, system-ui, -apple-system, Segoe UI, sans-serif' },
  shape: { borderRadius: 12 },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider theme={theme}><CssBaseline /><App /></ThemeProvider>
  </React.StrictMode>,
);
