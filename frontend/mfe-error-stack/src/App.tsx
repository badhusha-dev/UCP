import React, { useState } from 'react';
import {
  Box,
  Container,
  CssBaseline,
  Tab,
  Tabs,
  ThemeProvider,
  createTheme,
  Stack,
  Typography,
} from '@mui/material';
import {
  DashboardRounded,
  ViewListRounded,
  BugReportRounded,
} from '@mui/icons-material';
import axios from 'axios';
import ErrorOverviewPage from './pages/ErrorOverviewPage';
import ErrorGroupsPage from './pages/ErrorGroupsPage';
import ErrorDetailPage from './pages/ErrorDetailPage';

// Set up Axios interceptor for JWT authorization
axios.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('jwt_token');
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    const userStr = localStorage.getItem('user');
    if (userStr) {
      try {
        const u = JSON.parse(userStr);
        if (u.role && !config.headers['X-User-Role']) config.headers['X-User-Role'] = u.role;
        if (u.id && !config.headers['X-User-Id']) config.headers['X-User-Id'] = String(u.id);
      } catch (e) {
        // ignore
      }
    }
  }
  return config;
});

const darkTheme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#4F46E5' },
    background: { default: '#0B0F19', paper: '#111827' },
    text: { primary: '#F8FAFC', secondary: '#94A3B8' },
  },
  typography: {
    fontFamily: '"Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  },
});

export default function App() {
  const [activeTab, setActiveTab] = useState<'overview' | 'groups'>('overview');
  const [selectedFingerprint, setSelectedFingerprint] = useState<string | null>(null);

  return (
    <ThemeProvider theme={darkTheme}>
      <CssBaseline />
      <Box sx={{ bgcolor: '#0B0F19', minHeight: '100vh', py: 3 }}>
        {selectedFingerprint ? (
          <ErrorDetailPage
            fingerprint={selectedFingerprint}
            onBack={() => setSelectedFingerprint(null)}
          />
        ) : (
          <Box sx={{ maxWidth: 1400, mx: 'auto', px: 3 }}>
            {/* Navigation Tabs */}
            <Box sx={{ borderBottom: '1px solid #1F2937', mb: 3 }}>
              <Tabs
                value={activeTab}
                onChange={(_, val) => setActiveTab(val)}
                sx={{
                  '& .MuiTab-root': {
                    color: '#94A3B8',
                    fontWeight: 700,
                    textTransform: 'none',
                    fontSize: '0.95rem',
                  },
                  '& .Mui-selected': { color: '#818CF8' },
                  '& .MuiTabs-indicator': { bgcolor: '#4F46E5', height: 3 },
                }}
              >
                <Tab icon={<DashboardRounded />} iconPosition="start" label="Executive Overview" value="overview" />
                <Tab icon={<ViewListRounded />} iconPosition="start" label="Error Groups & Triage" value="groups" />
              </Tabs>
            </Box>

            {activeTab === 'overview' && (
              <ErrorOverviewPage onSelectGroup={(fp) => setSelectedFingerprint(fp)} />
            )}

            {activeTab === 'groups' && (
              <ErrorGroupsPage onSelectGroup={(fp) => setSelectedFingerprint(fp)} />
            )}
          </Box>
        )}
      </Box>
    </ThemeProvider>
  );
}
