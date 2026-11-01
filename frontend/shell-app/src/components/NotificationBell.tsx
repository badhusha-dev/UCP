import React, { useState, useEffect } from 'react';
import { Badge, IconButton, Menu, MenuItem, Typography, Box, Divider } from '@mui/material';
import NotificationsIcon from '@mui/icons-material/Notifications';
import DeleteIcon from '@mui/icons-material/Delete';
import { useSelector } from 'react-redux';
import type { RootState } from '@ecommerce/shared-library';

interface Notification {
    id: number;
    message: string;
    type: string;
    read: boolean;
    createdAt: string;
}

const NotificationBell: React.FC = () => {
    const { isAuthenticated, user, token } = useSelector((state: RootState) => state.auth);
    const [notifications, setNotifications] = useState<Notification[]>([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

    const open = Boolean(anchorEl);
    
    const fetchNotifications = async () => {
        if (!isAuthenticated || !user || !token) return;
        
        try {
            const res = await fetch(`http://localhost:8080/api/notifications/user/${user.id}`, {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });
            if (res.ok) {
                const data = await res.json();
                setNotifications(data);
                setUnreadCount(data.filter((n: Notification) => !n.read).length);
            }
        } catch (error) {
            console.error("Failed to fetch notifications", error);
        }
    };

    useEffect(() => {
        if (isAuthenticated) {
            fetchNotifications();
            const intervalId = setInterval(fetchNotifications, 30000);
            return () => clearInterval(intervalId);
        }
    }, [isAuthenticated, user, token]);

    const handleClick = (event: React.MouseEvent<HTMLElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const handleClose = () => {
        setAnchorEl(null);
    };

    const markAsRead = async (id: number) => {
        try {
            await fetch(`http://localhost:8080/api/notifications/${id}/read`, {
                method: 'PUT',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            fetchNotifications();
        } catch (e) {
            console.error(e);
        }
    };

    const markAllAsRead = async () => {
        if (!user || !token) return;
        try {
            await fetch(`http://localhost:8080/api/notifications/user/${user.id}/read-all`, {
                method: 'PUT',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            fetchNotifications();
        } catch (e) {
            console.error(e);
        }
    };

    const deleteNotification = async (id: number) => {
        try {
            await fetch(`http://localhost:8080/api/notifications/${id}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            fetchNotifications();
        } catch (e) {
            console.error(e);
        }
    };

    return (
        <Box sx={{ mr: 2 }}>
            <IconButton color="inherit" onClick={handleClick}>
                <Badge badgeContent={unreadCount} color="error">
                    <NotificationsIcon />
                </Badge>
            </IconButton>
            <Menu
                anchorEl={anchorEl}
                open={open}
                onClose={handleClose}
                PaperProps={{
                    style: {
                        maxHeight: 400,
                        width: '350px',
                    },
                }}
            >
                <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Typography variant="h6">Notifications</Typography>
                    {unreadCount > 0 && (
                        <Typography 
                            variant="body2" 
                            color="primary" 
                            sx={{ cursor: 'pointer' }}
                            onClick={markAllAsRead}
                        >
                            Mark all as read
                        </Typography>
                    )}
                </Box>
                <Divider />
                {notifications.length === 0 ? (
                    <MenuItem disabled>No notifications</MenuItem>
                ) : (
                    notifications.map((notif) => (
                        <MenuItem 
                            key={notif.id} 
                            sx={{ 
                                display: 'flex', 
                                justifyContent: 'space-between', 
                                backgroundColor: notif.read ? 'inherit' : '#f0f8ff',
                                borderBottom: '1px solid #eee'
                            }}
                        >
                            <Box sx={{ display: 'flex', flexDirection: 'column', width: '85%' }} onClick={() => !notif.read && markAsRead(notif.id)}>
                                <Typography variant="body2" sx={{ fontWeight: notif.read ? 'normal' : 'bold', whiteSpace: 'normal' }}>
                                    {notif.message}
                                </Typography>
                                <Typography variant="caption" color="textSecondary">
                                    {new Date(notif.createdAt).toLocaleString()}
                                </Typography>
                            </Box>
                            <IconButton size="small" onClick={(e) => { e.stopPropagation(); deleteNotification(notif.id); }}>
                                <DeleteIcon fontSize="small" />
                            </IconButton>
                        </MenuItem>
                    ))
                )}
            </Menu>
        </Box>
    );
};

export default NotificationBell;
