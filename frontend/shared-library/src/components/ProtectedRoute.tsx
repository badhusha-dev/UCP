import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { RootState } from '../store';

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles: string[];
  requiredPermission?: string;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, allowedRoles, requiredPermission }) => {
  const { isAuthenticated, user } = useSelector((state: RootState) => state.auth);
  const location = useLocation();

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  const permissions = user.permissions || [];
  const granted = permissions.includes('*') || (requiredPermission ? permissions.includes(requiredPermission) : false);
  const legacyRoleAllowed = !user.permissions && (allowedRoles.includes(user.role) || user.role === 'ADMIN' || user.role === 'SUPER_ADMIN');

  if (user.role !== 'SUPER_ADMIN' && (requiredPermission ? !granted && !legacyRoleAllowed : !allowedRoles.includes(user.role) && user.role !== 'ADMIN')) {
    return <Navigate to="/unauthorized" replace />;
  }

  return <>{children}</>;
};
