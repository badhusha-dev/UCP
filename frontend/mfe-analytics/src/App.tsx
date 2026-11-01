import { Route, Routes } from 'react-router-dom';
import AdminAnalyticsDashboard from './pages/AdminAnalyticsDashboard';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<AdminAnalyticsDashboard />} />
      <Route path="/*" element={<AdminAnalyticsDashboard />} />
    </Routes>
  );
}
