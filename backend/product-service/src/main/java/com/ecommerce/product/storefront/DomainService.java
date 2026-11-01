package com.ecommerce.product.storefront;

import com.ecommerce.product.pricing.PricingSupport;
import com.ecommerce.product.security.CatalogPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

import static com.ecommerce.product.pricing.PricingSupport.*;

/**
 * Custom domains. A domain is VERIFIED only after a real DNS TXT lookup returns the challenge, and SSL is ACTIVE only after a real TLS
 * handshake with hostname verification succeeds and the certificate is within its validity period. Nothing is ever assumed.
 */
@Service
public class DomainService {
    public interface DnsVerifier { List<String> txtRecords(String name) throws Exception; }
    public record TlsResult(boolean valid, OffsetDateTime expiresAt, String message) {}
    public interface TlsProbe { TlsResult probe(String host); }

    @Component
    public static class JndiDnsVerifier implements DnsVerifier {
        @Override public List<String> txtRecords(String name) throws Exception {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "2");
            InitialDirContext ctx = new InitialDirContext(env);
            try {
                Attributes attrs = ctx.getAttributes(name, new String[]{"TXT"});
                Attribute txt = attrs.get("TXT");
                List<String> out = new ArrayList<>();
                if (txt != null) for (int i = 0; i < txt.size(); i++) out.add(String.valueOf(txt.get(i)).replace("\"", "").trim());
                return out;
            } finally { ctx.close(); }
        }
    }

    @Component
    public static class SocketTlsProbe implements TlsProbe {
        @Override public TlsResult probe(String host) {
            try (Socket plain = new Socket()) {
                plain.connect(new InetSocketAddress(host, 443), 4000);
                plain.setSoTimeout(4000);
                try (SSLSocket s = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, host, 443, true)) {
                    SSLParameters p = s.getSSLParameters();
                    p.setEndpointIdentificationAlgorithm("HTTPS"); // enforces hostname verification
                    p.setServerNames(List.of(new javax.net.ssl.SNIHostName(host)));
                    s.setSSLParameters(p);
                    s.startHandshake();
                    X509Certificate cert = (X509Certificate) s.getSession().getPeerCertificates()[0];
                    cert.checkValidity();
                    return new TlsResult(true, cert.getNotAfter().toInstant().atOffset(ZoneOffset.UTC), "Certificate valid for " + host);
                }
            } catch (Exception e) {
                return new TlsResult(false, null, "TLS check failed: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage()));
            }
        }
    }

    private static final Pattern LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final PricingSupport support;
    private final StorefrontService storefronts;
    private final DnsVerifier dns;
    private final TlsProbe tls;

    @Value("${storefront.base-domain:storefront.localhost}") private String baseDomain;
    @Value("${storefront.edge-host:}") private String edgeHost;

    public DomainService(JdbcTemplate jdbc, PricingSupport support, StorefrontService storefronts, DnsVerifier dns, TlsProbe tls) {
        this.jdbc = jdbc; this.support = support; this.storefronts = storefronts; this.dns = dns; this.tls = tls;
    }

    public List<Map<String, Object>> list(CatalogPrincipal actor, long storefrontId) {
        Map<String, Object> sf = storefronts.get(actor, storefrontId);
        List<Map<String, Object>> rows = new ArrayList<>(support.rows("SELECT * FROM storefront_domains WHERE tenant_id=? AND storefront_id=? ORDER BY id", actor.tenantId(), storefrontId));
        for (Map<String, Object> r : rows) r.put("instructions", instructions((String) r.get("hostname"), (String) r.get("verificationToken")));
        Map<String, Object> platform = new LinkedHashMap<>();
        platform.put("hostname", sf.get("slug") + "." + baseDomain);
        platform.put("type", "PLATFORM_SUBDOMAIN");
        platform.put("note", "Served by the platform from the storefront slug. Its DNS/TLS is operated by the platform (wildcard record on " + baseDomain + ") and is not checked here.");
        rows.add(0, platform);
        return rows;
    }

    Map<String, Object> instructions(String host, String token) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("step1", Map.of("type", "TXT", "name", "_ucp-challenge." + host, "value", "ucp-verify=" + token, "purpose", "Proves you control the domain."));
        m.put("step2", edgeHost == null || edgeHost.isBlank()
                ? Map.of("type", "CNAME", "name", host, "value", "(not configured)", "purpose", "Point the domain at the platform edge. The operator must set storefront.edge-host.")
                : Map.of("type", "CNAME", "name", host, "value", edgeHost, "purpose", "Points traffic at the platform edge (apex domains: use an ALIAS/ANAME record)."));
        m.put("step3", "After DNS propagates, press Verify. Then press Check SSL once the certificate is issued at the edge.");
        return m;
    }

    @Transactional
    public Map<String, Object> add(CatalogPrincipal actor, long storefrontId, String hostname) {
        storefronts.get(actor, storefrontId);
        String host = normalise(hostname);
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        StringBuilder token = new StringBuilder();
        for (byte b : raw) token.append(String.format("%02x", b));
        try {
            jdbc.update("INSERT INTO storefront_domains(tenant_id, storefront_id, hostname, verification_token, created_by) VALUES (?,?,?,?,?)", actor.tenantId(), storefrontId, host, token.toString(), actor.userId());
        } catch (DuplicateKeyException e) { throw conflict("That domain is already registered."); }
        storefronts.audit(actor, storefrontId, "DOMAIN", host, "ADD", null);
        return view(actor, host);
    }

    String normalise(String hostname) {
        String h = hostname == null ? "" : hostname.trim().toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.length() > 253 || h.isEmpty()) throw bad("Enter a valid hostname such as shop.example.com.");
        String[] labels = h.split("\\.", -1);
        if (labels.length < 2) throw bad("A domain needs at least two labels (e.g. shop.example.com).");
        for (String l : labels) if (!LABEL.matcher(l).matches()) throw bad("'" + h + "' is not a valid hostname.");
        if (labels[labels.length - 1].matches("[0-9]+")) throw bad("IP addresses are not allowed; use a hostname.");
        if (h.equals(baseDomain) || h.endsWith("." + baseDomain)) throw bad("Hostnames under " + baseDomain + " are managed by the platform.");
        if (h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.equals("localhost")) throw bad("Local and internal hostnames are not allowed.");
        return h;
    }

    private Map<String, Object> row(CatalogPrincipal actor, long id) {
        return support.one("SELECT * FROM storefront_domains WHERE id=? AND tenant_id=?", "Domain", id, actor.tenantId());
    }

    private Map<String, Object> view(CatalogPrincipal actor, String host) {
        Map<String, Object> r = support.one("SELECT * FROM storefront_domains WHERE hostname=? AND tenant_id=?", "Domain", host, actor.tenantId());
        r.put("instructions", instructions((String) r.get("hostname"), (String) r.get("verificationToken")));
        return r;
    }

    @Transactional
    public Map<String, Object> verify(CatalogPrincipal actor, long id) {
        Map<String, Object> d = row(actor, id);
        String host = (String) d.get("hostname");
        String expected = "ucp-verify=" + d.get("verificationToken");
        boolean ok = false;
        String message;
        try {
            List<String> records = dns.txtRecords("_ucp-challenge." + host);
            ok = records.stream().anyMatch(expected::equals);
            message = ok ? "Challenge record found." : records.isEmpty() ? "No TXT record found at _ucp-challenge." + host + " yet (DNS can take time to propagate)." : "A TXT record exists but its value does not match.";
        } catch (Exception e) {
            message = "DNS lookup failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        if (ok) jdbc.update("UPDATE storefront_domains SET verification_status='VERIFIED', verified_at=COALESCE(verified_at, CURRENT_TIMESTAMP), last_check_at=CURRENT_TIMESTAMP, last_check_message=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", message, id, actor.tenantId());
        else jdbc.update("UPDATE storefront_domains SET verification_status=CASE WHEN verification_status='VERIFIED' THEN 'VERIFIED' ELSE 'FAILED' END, last_check_at=CURRENT_TIMESTAMP, last_check_message=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", message, id, actor.tenantId());
        storefronts.audit(actor, ((Number) d.get("storefrontId")).longValue(), "DOMAIN", host, "VERIFY", ok ? "verified" : message);
        return view(actor, host);
    }

    @Transactional
    public Map<String, Object> checkSsl(CatalogPrincipal actor, long id) {
        Map<String, Object> d = row(actor, id);
        String host = (String) d.get("hostname");
        if (!"VERIFIED".equals(d.get("verificationStatus"))) throw conflict("Verify domain ownership before checking SSL.");
        TlsResult r = tls.probe(host);
        jdbc.update("UPDATE storefront_domains SET ssl_status=?, ssl_checked_at=CURRENT_TIMESTAMP, ssl_expires_at=?, ssl_message=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                r.valid() ? "ACTIVE" : "FAILED", r.expiresAt(), r.message() == null ? null : r.message().substring(0, Math.min(255, r.message().length())), id, actor.tenantId());
        storefronts.audit(actor, ((Number) d.get("storefrontId")).longValue(), "DOMAIN", host, "CHECK_SSL", r.valid() ? "active" : r.message());
        return view(actor, host);
    }

    @Transactional
    public Map<String, Object> makePrimary(CatalogPrincipal actor, long id) {
        Map<String, Object> d = row(actor, id);
        if (!"VERIFIED".equals(d.get("verificationStatus"))) throw conflict("Only a verified domain can be the primary domain.");
        long sf = ((Number) d.get("storefrontId")).longValue();
        jdbc.queryForList("SELECT id FROM storefronts WHERE id=? AND tenant_id=? FOR UPDATE", sf, actor.tenantId());
        jdbc.update("UPDATE storefront_domains SET is_primary=FALSE, updated_at=CURRENT_TIMESTAMP WHERE storefront_id=? AND tenant_id=?", sf, actor.tenantId());
        jdbc.update("UPDATE storefront_domains SET is_primary=TRUE, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", id, actor.tenantId());
        storefronts.audit(actor, sf, "DOMAIN", d.get("hostname"), "SET_PRIMARY", null);
        return view(actor, (String) d.get("hostname"));
    }

    @Transactional
    public void remove(CatalogPrincipal actor, long id) {
        Map<String, Object> d = row(actor, id);
        jdbc.update("DELETE FROM storefront_domains WHERE id=? AND tenant_id=?", id, actor.tenantId());
        storefronts.audit(actor, ((Number) d.get("storefrontId")).longValue(), "DOMAIN", d.get("hostname"), "REMOVE", null);
    }
}
