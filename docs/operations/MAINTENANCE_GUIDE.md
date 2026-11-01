# System Maintenance & Housekeeping Guide

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Scheduled Maintenance Procedures

### Weekly Database Housekeeping
To prevent PostgreSQL database bloat and optimize query plan execution:
```bash
# Execute VACUUM ANALYZE across all four domain databases
for db in user_db product_db order_db notification_db; do
    docker exec ecommerce-platform-postgres-1 psql -U postgres -d "$db" -c "VACUUM ANALYZE;"
done
```

### Docker Disk & Volume Housekeeping
Remove unused dangling container layers and build caches:
```bash
docker system prune -f --volumes=false
```
*Note: Never use `--volumes=true` on production systems to avoid deleting persistent database storage.*

---

## 2. SSL/TLS Certificate Renewal

When using automated Let's Encrypt certificates via Certbot:
```bash
certbot renew --dry-run
```
Upon certificate renewal, reload the NGINX reverse proxy or API Gateway to load new keys.
