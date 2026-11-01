import { Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { store } from '@ecommerce/shared-library';
import ProfilePage from './pages/ProfilePage';
import AdminUserList from './pages/AdminUserList';
import AddressBookPage from './pages/AddressBookPage';

function App() {
  return (
    <Provider store={store}>
      <Routes>
        <Route path="/" element={<ProfilePage />} />
        <Route path="/addresses" element={<AddressBookPage />} />
        <Route path="/admin" element={<AdminUserList />} />
      </Routes>
    </Provider>
  );
}

export default App;
