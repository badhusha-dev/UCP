package com.ecommerce.crm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CrmService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RestClient.Builder restClientBuilder;
    private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom().failureRateThreshold(50).minimumNumberOfCalls(5)
                    .slidingWindowSize(10).waitDurationInOpenState(Duration.ofSeconds(30)).build());

    @Value("${services.order.url:http://localhost:8083}") private String orderServiceUrl;
    @Value("${services.loyalty.url:http://localhost:8086}") private String loyaltyServiceUrl;

    public Map<String,Object> contacts(String term,String stage,String owner,int page,int size) {
        page=boundedPage(page);size=boundedSize(size); StringBuilder where=new StringBuilder(" WHERE archived=FALSE");List<Object> args=new ArrayList<>();
        if(term!=null&&!term.isBlank()){where.append(" AND (lower(display_name) LIKE ? OR lower(coalesce(email,'')) LIKE ? OR lower(coalesce(phone,'')) LIKE ?)");String q="%"+term.trim().toLowerCase(Locale.ROOT)+"%";args.add(q);args.add(q);args.add(q);}
        if(stage!=null&&!stage.isBlank()){where.append(" AND lifecycle_stage=?");args.add(stage.toUpperCase(Locale.ROOT));}
        if(owner!=null&&!owner.isBlank()){where.append(" AND owner=?");args.add(owner);}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM crm_contacts"+where,Long.class,args.toArray());
        args.add(size);args.add(page*size);
        List<Map<String,Object>> items=jdbc.queryForList("SELECT id,user_id AS \"userId\",first_name AS \"firstName\",last_name AS \"lastName\",display_name AS \"displayName\",email,phone,lifecycle_stage AS \"lifecycleStage\",owner,company_id AS \"companyId\",tags,source,notes,created_at AS \"createdAt\",updated_at AS \"updatedAt\" FROM crm_contacts"+where+" ORDER BY updated_at DESC LIMIT ? OFFSET ?",args.toArray());
        return pageResult(items,page,size,total==null?0:total);
    }

    @Transactional
    public Map<String,Object> createContact(Map<String,Object> request,String actor) {
        String display=displayName(request);String email=nullable(request.get("email"));Long userId=longValue(request.get("userId"));
        List<String> tags=stringList(request.get("tags"));
        Long companyId=longValue(request.get("companyId"));if(companyId!=null)requireExists("crm_companies",companyId);
        Long id=jdbc.queryForObject("INSERT INTO crm_contacts(user_id,first_name,last_name,display_name,email,phone,lifecycle_stage,owner,company_id,tags,source,notes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class,userId,nullable(request.get("firstName")),nullable(request.get("lastName")),display,email,nullable(request.get("phone")),text(request.getOrDefault("lifecycleStage",userId==null?"LEAD":"CUSTOMER")).toUpperCase(Locale.ROOT),nullable(request.get("owner")),companyId,json(tags),nullable(request.get("source")),nullable(request.get("notes")));
        if(companyId!=null)jdbc.update("INSERT INTO crm_contact_company(contact_id,company_id,is_primary) VALUES(?,?,TRUE) ON CONFLICT(contact_id,company_id) DO UPDATE SET is_primary=TRUE",id,companyId);
        Map<String,Object> saved=contact(id);audit("CONTACT",id,"CREATE",actor,null,saved);return saved;
    }

    public Map<String,Object> contact(long id) {
        return jdbc.query("SELECT id,user_id AS \"userId\",first_name AS \"firstName\",last_name AS \"lastName\",display_name AS \"displayName\",email,phone,lifecycle_stage AS \"lifecycleStage\",owner,company_id AS \"companyId\",tags,source,notes,archived,created_at AS \"createdAt\",updated_at AS \"updatedAt\" FROM crm_contacts WHERE id=? AND archived=FALSE",(rs,n)->row(rs),id)
                .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Contact not found."));
    }

    @Transactional
    public Map<String,Object> updateContact(long id,Map<String,Object> patch,String actor) {
        Map<String,Object> before=contact(id);Map<String,Object> merged=new HashMap<>(before);merged.putAll(patch);
        String display=displayName(merged);Long company=longValue(merged.get("companyId"));if(company!=null)requireExists("crm_companies",company);
        jdbc.update("UPDATE crm_contacts SET first_name=?,last_name=?,display_name=?,email=?,phone=?,lifecycle_stage=?,owner=?,company_id=?,tags=?,source=?,notes=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                nullable(merged.get("firstName")),nullable(merged.get("lastName")),display,nullable(merged.get("email")),nullable(merged.get("phone")),text(merged.get("lifecycleStage")).toUpperCase(Locale.ROOT),nullable(merged.get("owner")),company,json(stringList(merged.get("tags"))),nullable(merged.get("source")),nullable(merged.get("notes")),id);
        jdbc.update("UPDATE crm_contact_company SET is_primary=FALSE WHERE contact_id=?",id);
        if(company!=null)jdbc.update("INSERT INTO crm_contact_company(contact_id,company_id,is_primary) VALUES(?,?,TRUE) ON CONFLICT(contact_id,company_id) DO UPDATE SET is_primary=TRUE",id,company);
        Map<String,Object> after=contact(id);audit("CONTACT",id,"UPDATE",actor,before,after);return after;
    }

    @Transactional
    public void archiveContact(long id,String actor) {Map<String,Object> before=contact(id);jdbc.update("UPDATE crm_contacts SET archived=TRUE,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);audit("CONTACT",id,"ARCHIVE",actor,before,Map.of("archived",true));}

    @Transactional
    public Map<String,Object> bulkContacts(List<Long> ids,String action,String value,String actor) {
        if(ids==null||ids.isEmpty()||ids.size()>200)badRequest("Select between 1 and 200 contacts.");
        String operation=text(action).toUpperCase(Locale.ROOT);String fieldValue=text(value).trim();int changed=0;
        if(!Set.of("ARCHIVE","SET_LIFECYCLE","ADD_TAG","REMOVE_TAG").contains(operation))badRequest("Unsupported bulk contact action.");
        if(operation.equals("SET_LIFECYCLE")&&!Set.of("LEAD","PROSPECT","CUSTOMER","CHURNED").contains(fieldValue.toUpperCase(Locale.ROOT)))badRequest("Unsupported lifecycle stage.");
        if((operation.equals("ADD_TAG")||operation.equals("REMOVE_TAG"))&&fieldValue.isBlank())badRequest("Tag value is required.");
        for(Long id:new LinkedHashSet<>(ids)){if(id==null)continue;Map<String,Object> before=contact(id);switch(operation){
            case "ARCHIVE" -> jdbc.update("UPDATE crm_contacts SET archived=TRUE,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
            case "SET_LIFECYCLE" -> jdbc.update("UPDATE crm_contacts SET lifecycle_stage=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",fieldValue.toUpperCase(Locale.ROOT),id);
            case "ADD_TAG","REMOVE_TAG" -> {List<String> tags=new ArrayList<>(stringList(before.get("tags")));if(operation.equals("ADD_TAG")&&!tags.contains(fieldValue))tags.add(fieldValue);if(operation.equals("REMOVE_TAG"))tags.removeIf(tag->tag.equalsIgnoreCase(fieldValue));jdbc.update("UPDATE crm_contacts SET tags=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",json(tags),id);}
        }Map<String,Object> after=operation.equals("ARCHIVE")?Map.of("archived",true):contact(id);audit("CONTACT",id,"BULK_"+operation,actor,before,after);changed++;}
        return Map.of("updated",changed,"action",operation);
    }

    public Map<String,Object> customer360(long id) {
        Map<String,Object> profile=contact(id);Long userId=longValue(profile.get("userId"));
        profile.put("activities",activities(id,null,0,15).get("items"));profile.put("tasks",tasks(id,null,false,0,20).get("items"));profile.put("opportunities",opportunities(null,id,0,20).get("items"));profile.put("customFields",customValues("CONTACT",id));
        profile.put("orders",List.of());profile.put("loyalty",Map.of("available",false,"message","No linked ecommerce customer ID."));
        if(userId!=null){profile.put("orders",safeGet(orderServiceUrl+"/api/orders/user/"+userId,userId));profile.put("loyalty",safeGet(loyaltyServiceUrl+"/api/loyalty/"+userId,userId));}
        return profile;
    }

    public Map<String,Object> companies(String term,int page,int size) {
        page=boundedPage(page);size=boundedSize(size);String q=term==null||term.isBlank()?null:"%"+term.trim().toLowerCase(Locale.ROOT)+"%";
        String where=" WHERE c.archived=FALSE";List<Object> args=new ArrayList<>();if(q!=null){where+=" AND (lower(c.name) LIKE ? OR lower(coalesce(c.domain,'')) LIKE ? OR lower(coalesce(c.industry,'')) LIKE ?)";args.add(q);args.add(q);args.add(q);}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM crm_companies c"+where,Long.class,args.toArray());args.add(size);args.add(page*size);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT c.id,c.name,c.domain,c.industry,c.company_size AS \"companySize\",c.owner,c.notes,c.archived,c.created_at AS \"createdAt\",c.updated_at AS \"updatedAt\",COUNT(DISTINCT p.id) AS \"contactCount\" FROM crm_companies c LEFT JOIN crm_contact_company x ON x.company_id=c.id LEFT JOIN crm_contacts p ON p.id=x.contact_id AND p.archived=FALSE"+where+" GROUP BY c.id ORDER BY c.name LIMIT ? OFFSET ?",args.toArray());
        return pageResult(rows,page,size,total==null?0:total);
    }

    @Transactional
    public Map<String,Object> createCompany(Map<String,Object> m,String actor) {
        Long id=jdbc.queryForObject("INSERT INTO crm_companies(name,domain,industry,company_size,owner,notes) VALUES(?,?,?,?,?,?) RETURNING id",Long.class,required(m,"name"),nullable(m.get("domain")),nullable(m.get("industry")),nullable(m.get("companySize")),nullable(m.get("owner")),nullable(m.get("notes")));
        Map<String,Object> row=company(id);audit("COMPANY",id,"CREATE",actor,null,row);return row;
    }

    public Map<String,Object> getCompany(long id) { return company(id); }

    @Transactional
    public Map<String,Object> updateCompany(long id,Map<String,Object> patch,String actor) {
        Map<String,Object> before=company(id);if(Boolean.TRUE.equals(before.get("archived")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Archived companies cannot be edited.");
        Map<String,Object> merged=new HashMap<>(before);merged.putAll(patch);
        jdbc.update("UPDATE crm_companies SET name=?,domain=?,industry=?,company_size=?,owner=?,notes=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND archived=FALSE",
                required(merged,"name"),nullable(merged.get("domain")),nullable(merged.get("industry")),nullable(merged.get("companySize")),nullable(merged.get("owner")),nullable(merged.get("notes")),id);
        Map<String,Object> after=company(id);audit("COMPANY",id,"UPDATE",actor,before,after);return after;
    }

    @Transactional
    public void archiveCompany(long id,String actor) {
        Map<String,Object> before=company(id);if(Boolean.TRUE.equals(before.get("archived")))return;
        jdbc.update("UPDATE crm_companies SET archived=TRUE,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
        audit("COMPANY",id,"ARCHIVE",actor,before,Map.of("archived",true));
    }

    public List<Map<String,Object>> companyContacts(long companyId) {
        requireExists("crm_companies",companyId);
        return jdbc.queryForList("SELECT c.id,c.user_id AS \"userId\",c.display_name AS \"displayName\",c.email,c.lifecycle_stage AS \"lifecycleStage\",c.owner,x.is_primary AS \"primary\" FROM crm_contact_company x JOIN crm_contacts c ON c.id=x.contact_id WHERE x.company_id=? AND c.archived=FALSE ORDER BY x.is_primary DESC,c.display_name",companyId);
    }

    @Transactional
    public void linkCompanyContact(long companyId,long contactId,boolean primary) {
        requireExists("crm_companies",companyId);requireExists("crm_contacts",contactId);
        if(primary)jdbc.update("UPDATE crm_contact_company SET is_primary=FALSE WHERE contact_id=?",contactId);
        jdbc.update("INSERT INTO crm_contact_company(contact_id,company_id,is_primary) VALUES(?,?,?) ON CONFLICT(contact_id,company_id) DO UPDATE SET is_primary=EXCLUDED.is_primary",contactId,companyId,primary);
        if(primary)jdbc.update("UPDATE crm_contacts SET company_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",companyId,contactId);
    }

    public Map<String,Object> opportunities(String stage,Long contactId,int page,int size) {
        page=boundedPage(page);size=boundedSize(size);StringBuilder where=new StringBuilder(" WHERE o.status='OPEN'");List<Object> args=new ArrayList<>();
        if(stage!=null&&!stage.isBlank()){where.append(" AND o.stage=?");args.add(stage);}
        if(contactId!=null){where.append(" AND o.contact_id=?");args.add(contactId);}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM crm_opportunities o"+where,Long.class,args.toArray());args.add(size);args.add(page*size);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT o.id,o.name,o.contact_id AS \"contactId\",c.display_name AS \"contactName\",o.company_id AS \"companyId\",o.pipeline_key AS \"pipelineKey\",o.stage,o.amount,o.currency,o.probability,o.expected_close_date AS \"expectedCloseDate\",o.owner,o.status,o.notes,o.created_at AS \"createdAt\",o.updated_at AS \"updatedAt\" FROM crm_opportunities o LEFT JOIN crm_contacts c ON c.id=o.contact_id"+where+" ORDER BY o.expected_close_date NULLS LAST,o.updated_at DESC LIMIT ? OFFSET ?",args.toArray());
        return pageResult(rows,page,size,total==null?0:total);
    }

    @Transactional
    public Map<String,Object> createOpportunity(Map<String,Object> m,String actor) {
        Long contact=longValue(m.get("contactId"));if(contact!=null)requireExists("crm_contacts",contact);Long company=longValue(m.get("companyId"));if(company!=null)requireExists("crm_companies",company);
        String stage=text(m.getOrDefault("stage","Qualification"));BigDecimal probability=decimal(m.get("probability"),new BigDecimal("10"));if(probability.compareTo(BigDecimal.ZERO)<0||probability.compareTo(new BigDecimal("100"))>0)badRequest("probability must be between 0 and 100.");
        Long id=jdbc.queryForObject("INSERT INTO crm_opportunities(name,contact_id,company_id,pipeline_key,stage,amount,currency,probability,expected_close_date,owner,notes) VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,required(m,"name"),contact,company,text(m.getOrDefault("pipelineKey","default")),stage,decimal(m.get("amount"),BigDecimal.ZERO),validCurrency(text(m.getOrDefault("currency","USD"))),probability,date(m.get("expectedCloseDate")),nullable(m.get("owner")),nullable(m.get("notes")));
        Map<String,Object> saved=opportunity(id);audit("OPPORTUNITY",id,"CREATE",actor,null,saved);return saved;
    }

    @Transactional
    public Map<String,Object> updateOpportunity(long id,Map<String,Object> patch,String actor) {
        Map<String,Object> before=opportunity(id);if(!"OPEN".equals(before.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only open opportunities can be edited.");
        Map<String,Object> merged=new HashMap<>(before);merged.putAll(patch);
        Long contact=longValue(merged.get("contactId"));if(contact!=null)requireExists("crm_contacts",contact);
        Long company=longValue(merged.get("companyId"));if(company!=null)requireExists("crm_companies",company);
        BigDecimal amount=decimal(merged.get("amount"),BigDecimal.ZERO);if(amount.signum()<0)badRequest("amount cannot be negative.");
        BigDecimal probability=decimal(merged.get("probability"),BigDecimal.ZERO);if(probability.compareTo(BigDecimal.ZERO)<0||probability.compareTo(new BigDecimal("100"))>0)badRequest("probability must be between 0 and 100.");
        jdbc.update("UPDATE crm_opportunities SET name=?,contact_id=?,company_id=?,amount=?,currency=?,probability=?,expected_close_date=?,owner=?,notes=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                required(merged,"name"),contact,company,amount,validCurrency(text(merged.getOrDefault("currency","USD"))),probability,date(merged.get("expectedCloseDate")),nullable(merged.get("owner")),nullable(merged.get("notes")),id);
        Map<String,Object> after=opportunity(id);audit("OPPORTUNITY",id,"UPDATE",actor,before,after);return after;
    }

    @Transactional
    public Map<String,Object> archiveOpportunity(long id,String actor) {
        Map<String,Object> before=opportunity(id);if(!"OPEN".equals(before.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only open opportunities can be archived.");
        jdbc.update("UPDATE crm_opportunities SET status='ARCHIVED',updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
        Map<String,Object> after=opportunity(id);audit("OPPORTUNITY",id,"ARCHIVE",actor,before,after);return after;
    }

    @Transactional
    public Map<String,Object> moveOpportunity(long id,Map<String,Object> request,String actor) {
        Map<String,Object> before=opportunity(id);if(!"OPEN".equals(before.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only open opportunities can change stage.");String stage=required(request,"stage");String pipeline=text(before.get("pipelineKey"));
        if(!exists("SELECT EXISTS(SELECT 1 FROM crm_pipeline_stages WHERE pipeline_key=? AND name=?)",pipeline,stage))badRequest("Stage is not configured for this pipeline.");
        BigDecimal probability=decimal(request.get("probability"),decimal(before.get("probability"),BigDecimal.ZERO));
        String status=stage.equalsIgnoreCase("Won")?"WON":stage.equalsIgnoreCase("Lost")?"LOST":"OPEN";
        jdbc.update("UPDATE crm_opportunities SET stage=?,probability=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",stage,probability,status,id);
        Map<String,Object> after=opportunity(id);audit("OPPORTUNITY",id,"STAGE_CHANGE",actor,before,after);
        Map<String,Object> activity=new HashMap<>();activity.put("contactId",before.get("contactId"));activity.put("opportunityId",id);activity.put("type","STATUS_CHANGE");activity.put("subject","Opportunity moved to "+stage);activity.put("body",status);createActivity(activity,actor,"MANUAL");
        return after;
    }

    public Map<String,Object> opportunityDetails(long id) {
        Map<String,Object> details=opportunity(id);
        details.put("activities",activities(null,id,0,25).get("items"));
        details.put("customFields",customValues("OPPORTUNITY",id));
        return details;
    }

    public Map<String,Object> activities(Long contactId,Long opportunityId,int page,int size) {
        page=boundedPage(page);size=boundedSize(size);StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> args=new ArrayList<>();
        if(contactId!=null){where.append(" AND contact_id=?");args.add(contactId);}if(opportunityId!=null){where.append(" AND opportunity_id=?");args.add(opportunityId);}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM crm_activities"+where,Long.class,args.toArray());args.add(size);args.add(page*size);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,contact_id AS \"contactId\",opportunity_id AS \"opportunityId\",activity_type AS type,subject,body,occurred_at AS \"occurredAt\",created_by AS \"createdBy\",source,metadata FROM crm_activities"+where+" ORDER BY occurred_at DESC LIMIT ? OFFSET ?",args.toArray());return pageResult(rows,page,size,total==null?0:total);
    }

    @Transactional
    public Map<String,Object> createActivity(Map<String,Object> request,String actor,String source) {
        Long contact=longValue(request.get("contactId"));Long opp=longValue(request.get("opportunityId"));if(contact!=null)requireExists("crm_contacts",contact);if(opp!=null)requireExists("crm_opportunities",opp);
        String type=text(request.getOrDefault("type","NOTE")).toUpperCase(Locale.ROOT);if(!Set.of("NOTE","CALL","EMAIL","MEETING","ORDER_REF","STATUS_CHANGE","LOYALTY_REF","TASK").contains(type))badRequest("Unsupported activity type.");
        Long id=jdbc.queryForObject("INSERT INTO crm_activities(contact_id,opportunity_id,activity_type,subject,body,occurred_at,created_by,source,metadata) VALUES(?,?,?,?,?,COALESCE(?,CURRENT_TIMESTAMP),?,?,?) RETURNING id",Long.class,contact,opp,type,required(request,"subject"),nullable(request.get("body")),dateTime(request.get("occurredAt")),actor,source,jsonObject(request.get("metadata")));
        return jdbc.queryForMap("SELECT id,contact_id AS \"contactId\",opportunity_id AS \"opportunityId\",activity_type AS type,subject,body,occurred_at AS \"occurredAt\",created_by AS \"createdBy\",source,metadata FROM crm_activities WHERE id=?",id);
    }

    public Map<String,Object> tasks(Long contactId,String owner,boolean completed,int page,int size) {
        page=boundedPage(page);size=boundedSize(size);StringBuilder where=new StringBuilder(" WHERE completed=? AND archived=FALSE");List<Object> args=new ArrayList<>();args.add(completed);
        if(contactId!=null){where.append(" AND contact_id=?");args.add(contactId);}if(owner!=null&&!owner.isBlank()){where.append(" AND owner=?");args.add(owner);}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM crm_tasks"+where,Long.class,args.toArray());args.add(size);args.add(page*size);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,title,description,contact_id AS \"contactId\",opportunity_id AS \"opportunityId\",owner,due_at AS \"dueAt\",priority,completed,completed_at AS \"completedAt\",archived,created_by AS \"createdBy\",created_at AS \"createdAt\" FROM crm_tasks"+where+" ORDER BY completed ASC,due_at NULLS LAST LIMIT ? OFFSET ?",args.toArray());return pageResult(rows,page,size,total==null?0:total);
    }

    @Transactional
    public Map<String,Object> createTask(Map<String,Object> request,String actor) {
        Long contact=longValue(request.get("contactId"));if(contact!=null)requireExists("crm_contacts",contact);Long opp=longValue(request.get("opportunityId"));if(opp!=null)requireExists("crm_opportunities",opp);
        Long id=jdbc.queryForObject("INSERT INTO crm_tasks(title,description,contact_id,opportunity_id,owner,due_at,priority,created_by) VALUES(?,?,?,?,?,?,?,?) RETURNING id",Long.class,required(request,"title"),nullable(request.get("description")),contact,opp,nullable(request.get("owner")),dateTime(request.get("dueAt")),text(request.getOrDefault("priority","NORMAL")).toUpperCase(Locale.ROOT),actor);
        Map<String,Object> saved=task(id);audit("TASK",id,"CREATE",actor,null,saved);
        if(contact!=null)createActivity(Map.of("contactId",contact,"type","TASK","subject","Task created: "+request.get("title")),actor,"MANUAL");return saved;
    }

    @Transactional
    public Map<String,Object> updateTask(long id,Map<String,Object> patch,String actor) {
        Map<String,Object> before=task(id);if(Boolean.TRUE.equals(before.get("completed"))||Boolean.TRUE.equals(before.get("archived")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only active, incomplete tasks can be edited.");
        Map<String,Object> merged=new HashMap<>(before);merged.putAll(patch);Long contact=longValue(merged.get("contactId"));if(contact!=null)requireExists("crm_contacts",contact);Long opportunity=longValue(merged.get("opportunityId"));if(opportunity!=null)requireExists("crm_opportunities",opportunity);
        String priority=text(merged.getOrDefault("priority","NORMAL")).toUpperCase(Locale.ROOT);if(!Set.of("LOW","NORMAL","HIGH").contains(priority))badRequest("priority must be LOW, NORMAL, or HIGH.");
        jdbc.update("UPDATE crm_tasks SET title=?,description=?,contact_id=?,opportunity_id=?,owner=?,due_at=?,priority=? WHERE id=? AND archived=FALSE AND completed=FALSE",
                required(merged,"title"),nullable(merged.get("description")),contact,opportunity,nullable(merged.get("owner")),dateTime(merged.get("dueAt")),priority,id);
        Map<String,Object> after=task(id);audit("TASK",id,"UPDATE",actor,before,after);return after;
    }

    @Transactional
    public void archiveTask(long id,String actor) {
        Map<String,Object> before=task(id);if(Boolean.TRUE.equals(before.get("archived")))return;
        jdbc.update("UPDATE crm_tasks SET archived=TRUE WHERE id=?",id);audit("TASK",id,"ARCHIVE",actor,before,Map.of("archived",true));
    }

    @Transactional
    public Map<String,Object> completeTask(long id,String actor) {Map<String,Object> old=task(id);if(Boolean.TRUE.equals(old.get("completed")))return old;jdbc.update("UPDATE crm_tasks SET completed=TRUE,completed_at=CURRENT_TIMESTAMP WHERE id=?",id);Map<String,Object> saved=task(id);audit("TASK",id,"COMPLETE",actor,old,saved);return saved;}

    public List<Map<String,Object>> stages() {return jdbc.queryForList("SELECT id,pipeline_key AS \"pipelineKey\",name,sort_order AS \"sortOrder\",probability,is_closed AS \"closed\" FROM crm_pipeline_stages ORDER BY pipeline_key,sort_order");}

    public Map<String,Object> dashboard() {
        Map<String,Object> totals=jdbc.queryForMap("SELECT COUNT(*) FILTER(WHERE status='OPEN') AS \"openOpportunities\",COALESCE(SUM(amount) FILTER(WHERE status='OPEN'),0) AS \"pipelineValue\",COUNT(*) FILTER(WHERE status='WON') AS won,COUNT(*) FILTER(WHERE status='LOST') AS lost FROM crm_opportunities");
        Map<String,Object> tasks=jdbc.queryForMap("SELECT COUNT(*) FILTER(WHERE completed=FALSE) AS open,COUNT(*) FILTER(WHERE completed=FALSE AND due_at<CURRENT_TIMESTAMP) AS overdue FROM crm_tasks");
        Map<String,Object> contacts=jdbc.queryForMap("SELECT COUNT(*) FILTER(WHERE archived=FALSE) AS total,COUNT(*) FILTER(WHERE archived=FALSE AND lifecycle_stage='CUSTOMER') AS customers FROM crm_contacts");
        List<Map<String,Object>> funnel=jdbc.queryForList("SELECT s.name AS stage,s.sort_order AS \"sortOrder\",COUNT(o.id) AS count,COALESCE(SUM(o.amount),0) AS value FROM crm_pipeline_stages s LEFT JOIN crm_opportunities o ON o.pipeline_key=s.pipeline_key AND o.stage=s.name AND o.status='OPEN' WHERE s.pipeline_key='default' GROUP BY s.id ORDER BY s.sort_order");
        List<Map<String,Object>> topContacts=jdbc.queryForList("SELECT c.id,c.display_name AS \"displayName\",c.email,COUNT(o.id) AS opportunities,COALESCE(SUM(o.amount),0) AS \"pipelineValue\" FROM crm_contacts c JOIN crm_opportunities o ON o.contact_id=c.id WHERE c.archived=FALSE AND o.status IN ('OPEN','WON') GROUP BY c.id ORDER BY SUM(o.amount) DESC,c.display_name LIMIT 5");
        Map<String,Object> response=new LinkedHashMap<>(totals);response.put("tasks",tasks);response.put("contacts",contacts);response.put("pipeline",funnel);response.put("topContacts",topContacts);
        long decided=number(totals.get("won"))+number(totals.get("lost"));response.put("conversionRate",decided==0?0.0:100.0*number(totals.get("won"))/decided);
        response.put("retentionRate",null);response.put("retentionNote","Connect repeat-order events to calculate retention; no rate is inferred from incomplete data.");return response;
    }

    public Map<String,Object> segments(int page,int size) {page=boundedPage(page);size=boundedSize(size);Long total=jdbc.queryForObject("SELECT COUNT(*) FROM crm_segments WHERE active=TRUE",Long.class);List<Map<String,Object>> rows=jdbc.queryForList("SELECT s.id,s.name,s.description,s.criteria,s.active,s.created_by AS \"createdBy\",s.created_at AS \"createdAt\",COUNT(m.contact_id) AS \"memberCount\" FROM crm_segments s LEFT JOIN crm_segment_members m ON m.segment_id=s.id WHERE s.active=TRUE GROUP BY s.id ORDER BY s.updated_at DESC LIMIT ? OFFSET ?",size,page*size);return pageResult(rows,page,size,total==null?0:total);}
    @Transactional public Map<String,Object> createSegment(Map<String,Object> m,String actor) {Long id=jdbc.queryForObject("INSERT INTO crm_segments(name,description,criteria,created_by) VALUES(?,?,?,?) RETURNING id",Long.class,required(m,"name"),nullable(m.get("description")),jsonObject(m.get("criteria")),actor);Map<String,Object> saved=segment(id);audit("SEGMENT",id,"CREATE",actor,null,saved);return saved;}
    @Transactional public Map<String,Object> updateSegment(long id,Map<String,Object> patch,String actor) {Map<String,Object> before=segment(id);if(Boolean.FALSE.equals(before.get("active")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Archived segments cannot be edited.");Map<String,Object> merged=new HashMap<>(before);merged.putAll(patch);jdbc.update("UPDATE crm_segments SET name=?,description=?,criteria=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND active=TRUE",required(merged,"name"),nullable(merged.get("description")),jsonObject(merged.get("criteria")),id);Map<String,Object> after=segment(id);audit("SEGMENT",id,"UPDATE",actor,before,after);return after;}
    @Transactional public void archiveSegment(long id,String actor) {Map<String,Object> before=segment(id);if(Boolean.FALSE.equals(before.get("active")))return;jdbc.update("UPDATE crm_segments SET active=FALSE,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);audit("SEGMENT",id,"ARCHIVE",actor,before,Map.of("active",false));}
    public Map<String,Object> segmentMembers(long id,int page,int size) {Map<String,Object> segment=segment(id);page=boundedPage(page);size=boundedSize(size);String criteria=text(segment.get("criteria"));Map<String,Object> filter=parseObject(criteria);StringBuilder where=new StringBuilder(" WHERE c.archived=FALSE");List<Object> args=new ArrayList<>();if(filter.get("lifecycleStage")!=null){where.append(" AND c.lifecycle_stage=?");args.add(text(filter.get("lifecycleStage")).toUpperCase(Locale.ROOT));}if(filter.get("owner")!=null){where.append(" AND c.owner=?");args.add(text(filter.get("owner")));}if(filter.get("tag")!=null){where.append(" AND c.tags LIKE ?");args.add("%\""+text(filter.get("tag"))+"\"%");}Long total=jdbc.queryForObject("SELECT COUNT(*) FROM crm_contacts c"+where,Long.class,args.toArray());args.add(size);args.add(page*size);List<Map<String,Object>> members=jdbc.queryForList("SELECT c.id,c.user_id AS \"userId\",c.display_name AS \"displayName\",c.email,c.lifecycle_stage AS \"lifecycleStage\",c.owner,c.tags FROM crm_contacts c"+where+" ORDER BY c.display_name LIMIT ? OFFSET ?",args.toArray());return pageResult(members,page,size,total==null?0:total);}

    public List<Map<String,Object>> audit(long contactId,int page,int size) {boundedPage(page);boundedSize(size);return jdbc.queryForList("SELECT id,entity_type AS \"entityType\",entity_id AS \"entityId\",action,actor,before_data AS \"before\",after_data AS \"after\",created_at AS \"createdAt\" FROM crm_audit_log WHERE entity_type='CONTACT' AND entity_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?",contactId,size,page*size);}
    public List<Map<String,Object>> customFields(String subjectType) {if(subjectType==null||subjectType.isBlank())return jdbc.queryForList("SELECT id,subject_type AS \"subjectType\",field_key AS \"fieldKey\",label,field_type AS \"fieldType\",is_required AS required,is_active AS active FROM crm_custom_fields WHERE is_active=TRUE ORDER BY subject_type,label");return jdbc.queryForList("SELECT id,subject_type AS \"subjectType\",field_key AS \"fieldKey\",label,field_type AS \"fieldType\",is_required AS required,is_active AS active FROM crm_custom_fields WHERE is_active=TRUE AND subject_type=? ORDER BY label",subjectType.toUpperCase(Locale.ROOT));}
    @Transactional public Map<String,Object> createCustomField(Map<String,Object> m) {Long id=jdbc.queryForObject("INSERT INTO crm_custom_fields(subject_type,field_key,label,field_type,is_required) VALUES(?,?,?,?,?) RETURNING id",Long.class,required(m,"subjectType").toUpperCase(Locale.ROOT),required(m,"fieldKey").toLowerCase(Locale.ROOT),required(m,"label"),text(m.getOrDefault("fieldType","TEXT")).toUpperCase(Locale.ROOT),Boolean.TRUE.equals(m.get("required")));return jdbc.queryForMap("SELECT id,subject_type AS \"subjectType\",field_key AS \"fieldKey\",label,field_type AS \"fieldType\",is_required AS required,is_active AS active FROM crm_custom_fields WHERE id=?",id);}
    @Transactional public void saveCustomValues(String subjectType,long subjectId,Map<String,Object> values) {String type=subjectType.toUpperCase(Locale.ROOT);for(var entry:values.entrySet()){Long field=jdbc.query("SELECT id FROM crm_custom_fields WHERE subject_type=? AND field_key=? AND is_active=TRUE",(rs,n)->rs.getLong(1),type,entry.getKey()).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown custom field: "+entry.getKey()));jdbc.update("INSERT INTO crm_custom_field_values(field_id,subject_id,value,updated_at) VALUES(?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(field_id,subject_id) DO UPDATE SET value=EXCLUDED.value,updated_at=CURRENT_TIMESTAMP",field,subjectId,String.valueOf(entry.getValue()));}}
    public Map<String,Object> customValues(String subjectType,long subjectId) {Map<String,Object> values=new LinkedHashMap<>();jdbc.queryForList("SELECT f.field_key,v.value FROM crm_custom_fields f JOIN crm_custom_field_values v ON v.field_id=f.id WHERE f.subject_type=? AND v.subject_id=?",subjectType.toUpperCase(Locale.ROOT),subjectId).forEach(row->values.put(String.valueOf(row.get("field_key")),row.get("value")));return values;}
    public List<Map<String,Object>> savedViews(String user) {return jdbc.queryForList("SELECT id,user_key AS \"userKey\",name,view_type AS \"viewType\",filters,created_at AS \"createdAt\" FROM crm_saved_views WHERE user_key=? ORDER BY name",user);}
    @Transactional public Map<String,Object> saveView(Map<String,Object> m,String user) {Long id=jdbc.queryForObject("INSERT INTO crm_saved_views(user_key,name,view_type,filters) VALUES(?,?,?,?) ON CONFLICT(user_key,name,view_type) DO UPDATE SET filters=EXCLUDED.filters RETURNING id",Long.class,user,required(m,"name"),required(m,"viewType").toUpperCase(Locale.ROOT),jsonObject(m.get("filters")));return jdbc.queryForMap("SELECT id,user_key AS \"userKey\",name,view_type AS \"viewType\",filters,created_at AS \"createdAt\" FROM crm_saved_views WHERE id=?",id);}

    public byte[] exportContacts() {List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,user_id,display_name,email,phone,lifecycle_stage,owner,tags,source FROM crm_contacts WHERE archived=FALSE ORDER BY id");ByteArrayOutputStream out=new ByteArrayOutputStream();try(PrintWriter w=new PrintWriter(out,true,StandardCharsets.UTF_8)){w.println("id,user_id,display_name,email,phone,lifecycle_stage,owner,tags,source");for(Map<String,Object> row:rows)w.println(row.values().stream().map(v->csv(v==null?"":String.valueOf(v))).reduce((a,b)->a+","+b).orElse(""));}return out.toByteArray();}
    @Transactional public Map<String,Object> importContacts(MultipartFile file,String actor) {if(file==null||file.isEmpty()||file.getSize()>5*1024*1024)badRequest("Choose a non-empty CSV file up to 5 MB.");List<String> errors=new ArrayList<>();int processed=0;String[] lines;try{lines=new String(file.getBytes(),StandardCharsets.UTF_8).split("\\R");}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unable to read CSV file.");}if(lines.length<2)badRequest("CSV needs a header and data rows.");List<String> header=parseCsv(lines[0]);for(int i=1;i<lines.length;i++){try{List<String> row=parseCsv(lines[i]);Map<String,Object> m=new HashMap<>();for(int j=0;j<header.size()&&j<row.size();j++)m.put(header.get(j).trim(),row.get(j).trim());createContact(m,actor);processed++;}catch(Exception e){errors.add("Row "+(i+1)+": "+safe(e));}}return Map.of("totalRows",lines.length-1,"processedRows",processed,"errorRows",errors.size(),"errors",errors);}

    public void captureEvent(String topic,String key,int partition,long offset,String payload) {
        try {JsonNode event=mapper.readTree(payload);long userId=event.path("userId").asLong(event.path("user_id").asLong(0));if(userId<=0&&topic.startsWith("user"))userId=event.path("id").asLong(0);if(userId<=0)return;String eventType=event.path("eventType").asText(topic);String eventKey=topic+":"+partition+":"+(key==null?"":key)+":"+offset;
            Long contactId=jdbc.queryForObject("INSERT INTO crm_contacts(user_id,display_name,lifecycle_stage,source) VALUES(?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET updated_at=CURRENT_TIMESTAMP RETURNING id",Long.class,userId,"Customer "+userId,"CUSTOMER","EVENT");
            String subject=eventType+ (event.has("orderId")?" · Order #"+event.path("orderId").asText():"");String body=event.has("status")?"Status: "+event.path("status").asText():event.toString();
            String activityType=topic.startsWith("order")?"ORDER_REF":topic.startsWith("loyalty")?"LOYALTY_REF":"PROFILE_UPDATE";
            jdbc.update("INSERT INTO crm_activities(contact_id,activity_type,subject,body,source,event_key,metadata) VALUES(?,?,?,?,?,?,?) ON CONFLICT(event_key) DO NOTHING",contactId,activityType,subject,body,"KAFKA",eventKey,event.toString());
        } catch(Exception ignored) { /* A malformed upstream event must not poison the consumer group. */ }
    }

    private Map<String,Object> safeGet(String baseUrl,long userId) {
        String breakerName=baseUrl.startsWith(orderServiceUrl)?"orders":"loyalty";
        CircuitBreaker breaker=circuitBreakers.circuitBreaker("crm-"+breakerName);
        try {
            SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(1500);factory.setReadTimeout(2000);
            Object data=breaker.executeSupplier(()->restClientBuilder.requestFactory(factory).build().get().uri(baseUrl)
                    .header("X-User-Id",String.valueOf(userId)).header("X-User-Role","MANAGER").retrieve().body(Object.class));
            Map<String,Object> response=new LinkedHashMap<>();response.put("available",true);response.put("data",data);return response;
        } catch(Exception e) {return Map.of("available",false,"message","Upstream service is temporarily unavailable.");}
    }
    private Map<String,Object> company(long id){return jdbc.query("SELECT id,name,domain,industry,company_size AS \"companySize\",owner,notes,archived,created_at AS \"createdAt\",updated_at AS \"updatedAt\" FROM crm_companies WHERE id=?",(rs,n)->row(rs),id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Company not found."));}
    private Map<String,Object> opportunity(long id){return jdbc.query("SELECT id,name,contact_id AS \"contactId\",company_id AS \"companyId\",pipeline_key AS \"pipelineKey\",stage,amount,currency,probability,expected_close_date AS \"expectedCloseDate\",owner,status,notes,created_at AS \"createdAt\",updated_at AS \"updatedAt\" FROM crm_opportunities WHERE id=?",(rs,n)->row(rs),id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Opportunity not found."));}
    private Map<String,Object> task(long id){return jdbc.query("SELECT id,title,description,contact_id AS \"contactId\",opportunity_id AS \"opportunityId\",owner,due_at AS \"dueAt\",priority,completed,completed_at AS \"completedAt\",archived,created_by AS \"createdBy\",created_at AS \"createdAt\" FROM crm_tasks WHERE id=?",(rs,n)->row(rs),id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Task not found."));}
    private Map<String,Object> segment(long id){return jdbc.query("SELECT id,name,description,criteria,active,created_by AS \"createdBy\",created_at AS \"createdAt\" FROM crm_segments WHERE id=?",(rs,n)->row(rs),id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Segment not found."));}
    private Map<String,Object> row(java.sql.ResultSet rs)throws java.sql.SQLException{Map<String,Object> m=new LinkedHashMap<>();var md=rs.getMetaData();for(int i=1;i<=md.getColumnCount();i++)m.put(md.getColumnLabel(i),rs.getObject(i));return m;}
    private void audit(String type,long id,String action,String actor,Object before,Object after){jdbc.update("INSERT INTO crm_audit_log(entity_type,entity_id,action,actor,before_data,after_data) VALUES(?,?,?,?,?,?)",type,id,action,actor,serialize(before),serialize(after));}
    private void requireExists(String table,long id){if(!Set.of("crm_contacts","crm_companies","crm_opportunities").contains(table))throw new IllegalArgumentException("Invalid entity table.");if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE id=?)",Boolean.class,id)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Linked CRM record not found.");}
    private boolean exists(String sql,Object... args){return Boolean.TRUE.equals(jdbc.queryForObject(sql,Boolean.class,args));}
    private long number(Object value){return value instanceof Number n?n.longValue():0;}
    private Map<String,Object> pageResult(List<Map<String,Object>> rows,int page,int size,long total){return Map.of("items",rows,"page",page,"size",size,"total",total,"totalPages",(int)Math.ceil((double)total/size));}
    private int boundedPage(int p){if(p<0)badRequest("page must not be negative.");return p;} private int boundedSize(int s){if(s<1||s>100)badRequest("size must be between 1 and 100.");return s;}
    private static String displayName(Map<String,Object> m){String name=text(m.get("displayName")).trim();if(!name.isBlank())return name;name=(text(m.get("firstName"))+" "+text(m.get("lastName"))).trim();if(name.isBlank())badRequest("displayName or firstName is required.");return name;}
    private static String required(Map<String,Object> m,String k){String s=text(m.get(k)).trim();if(s.isBlank())badRequest(k+" is required.");return s;} private static String text(Object v){return v==null?"":String.valueOf(v);} private static String nullable(Object v){String s=text(v).trim();return s.isBlank()?null:s;}
    private static Long longValue(Object v){if(v==null||String.valueOf(v).isBlank())return null;try{return Long.valueOf(String.valueOf(v));}catch(Exception e){badRequest("Expected a numeric identifier.");return null;}}
    private static BigDecimal decimal(Object v,BigDecimal fallback){if(v==null||String.valueOf(v).isBlank())return fallback;try{return new BigDecimal(String.valueOf(v));}catch(Exception e){badRequest("Expected a numeric value.");return fallback;}}
    private static LocalDate date(Object v){if(v==null||String.valueOf(v).isBlank())return null;try{return LocalDate.parse(String.valueOf(v));}catch(Exception e){badRequest("Date must use YYYY-MM-DD.");return null;}}
    private static LocalDateTime dateTime(Object v){if(v==null||String.valueOf(v).isBlank())return null;try{return LocalDateTime.parse(String.valueOf(v));}catch(Exception e){badRequest("Date-time must use ISO local date-time format.");return null;}}
    private static String validCurrency(String c){if(c==null||!c.toUpperCase(Locale.ROOT).matches("[A-Z]{3}"))badRequest("Currency must be a three-letter ISO code.");return c.toUpperCase(Locale.ROOT);}
    private List<String> stringList(Object value){if(value==null)return List.of();if(value instanceof List<?> l)return l.stream().map(String::valueOf).map(String::trim).filter(s->!s.isBlank()).distinct().limit(20).toList();return Arrays.stream(String.valueOf(value).split(",")).map(String::trim).filter(s->!s.isBlank()).distinct().limit(20).toList();}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){return "[]";}} private String jsonObject(Object value){if(value==null)return "{}";return json(value);}
    private String serialize(Object value){if(value==null)return null;return json(value);}
    private Map<String,Object> parseObject(String raw){try{return mapper.readValue(raw,Map.class);}catch(Exception e){return Map.of();}}
    private List<String> parseCsv(String line){List<String> out=new ArrayList<>();StringBuilder b=new StringBuilder();boolean quote=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(quote&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}else quote=!quote;}else if(c==','&&!quote){out.add(b.toString());b.setLength(0);}else b.append(c);}out.add(b.toString());return out;}
    private String csv(String s){return "\""+s.replace("\"","\"\"")+"\"";}private String safe(Exception e){String m=e.getMessage();return m==null?"Invalid data.":m.replaceAll("[\\r\\n]"," ").substring(0,Math.min(180,m.length()));}
    private static void badRequest(String s){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
}
