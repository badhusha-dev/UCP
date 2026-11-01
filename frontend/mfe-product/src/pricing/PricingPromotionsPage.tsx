import { useState } from 'react';
import { Alert, Box, List, ListItemButton, ListItemText, Paper, Typography } from '@mui/material';
import { useSelector } from 'react-redux';
import type { RootState } from '@ecommerce/shared-library';
import { api } from './pricingApi';
import { Loading, useLoad } from './common';
import { hasPermission, visibleSections } from './pricingModel';
import type { SectionId } from './pricingModel';
import { BulkSection, DashboardSection, PricesSection, ScheduledSection } from './sections/PriceSections';
import { ApprovalSection, CalendarSection, CampaignSection, CouponSection, PerformanceSection } from './sections/PromotionSections';
import { AuditSection, RulesSection } from './sections/AdminSections';

/** "Pricing & Promotions" area of the E-commerce Admin application. All data comes from the pricing API. */
export default function PricingPromotionsPage() {
  const user = useSelector((state: RootState) => state.auth.user) as { role?: string; permissions?: string[] } | null;
  const can = (permission: string) => hasPermission(user?.role, user?.permissions, permission);
  const sections = visibleSections(user?.role, user?.permissions);
  const [selected, setSelected] = useState<SectionId | null>(null);
  const settings = useLoad(() => api.settings(), []);
  const current = selected ?? sections[0]?.id;
  const props = { can, settings: settings.data };

  if (sections.length === 0) {
    return <Alert severity="warning" sx={{ m: 3 }}>You do not have access to Pricing &amp; Promotions. Ask a tenant administrator for the pricing or promotion permissions.</Alert>;
  }

  const body = (() => {
    switch (current) {
      case 'dashboard': return <DashboardSection />;
      case 'prices': return <PricesSection {...props} />;
      case 'bulk': return <BulkSection {...props} />;
      case 'scheduled': return <ScheduledSection {...props} />;
      case 'campaigns': return <CampaignSection kind="campaigns" {...props} />;
      case 'automatic': return <CampaignSection kind="automatic" {...props} />;
      case 'bundles': return <CampaignSection kind="bundles" {...props} />;
      case 'bxgy': return <CampaignSection kind="bxgy" {...props} />;
      case 'segments': return <CampaignSection kind="segments" {...props} />;
      case 'coupons': return <CouponSection {...props} />;
      case 'calendar': return <CalendarSection />;
      case 'approvals': return <ApprovalSection {...props} />;
      case 'performance': return <PerformanceSection />;
      case 'rules': return <RulesSection {...props} />;
      case 'audit': return <AuditSection />;
      default: return null;
    }
  })();

  return (
    <Box sx={{ display: 'flex', gap: 2, p: { xs: 1, md: 3 }, flexDirection: { xs: 'column', md: 'row' } }}>
      <Paper variant="outlined" sx={{ minWidth: 240, alignSelf: 'flex-start' }}>
        <Typography variant="overline" sx={{ px: 2, pt: 1, display: 'block' }}>Pricing &amp; Promotions</Typography>
        <List dense>
          {sections.map((s) => (
            <ListItemButton key={s.id} selected={s.id === current} onClick={() => setSelected(s.id)}><ListItemText primary={s.label} /></ListItemButton>
          ))}
        </List>
      </Paper>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="h5" gutterBottom>{sections.find((s) => s.id === current)?.label}</Typography>
        <Loading loading={false} error={settings.error} />
        {body}
      </Box>
    </Box>
  );
}
