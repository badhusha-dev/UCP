package com.ecommerce.crm.controller;

import com.ecommerce.crm.service.CrmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crm")
@RequiredArgsConstructor
public class CrmController {
    private final CrmService service;

    @GetMapping("/contacts") public Map<String,Object> contacts(@RequestParam(required=false) String search,@RequestParam(required=false) String stage,@RequestParam(required=false) String owner,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.contacts(search,stage,owner,page,size);}
    @PostMapping("/contacts") public ResponseEntity<Map<String,Object>> createContact(@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return ResponseEntity.status(HttpStatus.CREATED).body(service.createContact(body,actor));}
    @PatchMapping("/contacts/bulk") public Map<String,Object> bulkContacts(@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody BulkContactsRequest request){return service.bulkContacts(request.ids(),request.action(),request.value(),actor);}
    @GetMapping("/contacts/{id}") public Map<String,Object> contact(@PathVariable long id){return service.contact(id);}
    @PatchMapping("/contacts/{id}") public Map<String,Object> updateContact(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return service.updateContact(id,body,actor);}
    @DeleteMapping("/contacts/{id}") public ResponseEntity<Void> archiveContact(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor){service.archiveContact(id,actor);return ResponseEntity.noContent().build();}
    @GetMapping("/contacts/{id}/360") public Map<String,Object> customer360(@PathVariable long id){return service.customer360(id);}
    @GetMapping("/contacts/{id}/audit") public List<Map<String,Object>> contactAudit(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.audit(id,page,size);}
    @GetMapping("/contacts/{id}/custom-fields") public Map<String,Object> contactCustomValues(@PathVariable long id){return service.customValues("CONTACT",id);}
    @PatchMapping("/contacts/{id}/custom-fields") public ResponseEntity<Void> updateContactCustomValues(@PathVariable long id,@RequestBody Map<String,Object> values){service.saveCustomValues("CONTACT",id,values);return ResponseEntity.noContent().build();}

    @GetMapping("/companies") public Map<String,Object> companies(@RequestParam(required=false) String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.companies(search,page,size);}
    @PostMapping("/companies") public ResponseEntity<Map<String,Object>> createCompany(@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return ResponseEntity.status(HttpStatus.CREATED).body(service.createCompany(body,actor));}
    @GetMapping("/companies/{id}") public Map<String,Object> company(@PathVariable long id){return service.getCompany(id);}
    @PutMapping("/companies/{id}") public Map<String,Object> updateCompany(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return service.updateCompany(id,body,actor);}
    @DeleteMapping("/companies/{id}") public ResponseEntity<Void> archiveCompany(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor){service.archiveCompany(id,actor);return ResponseEntity.noContent().build();}
    @GetMapping("/companies/{id}/contacts") public List<Map<String,Object>> companyContacts(@PathVariable long id){return service.companyContacts(id);}
    @PostMapping("/companies/{id}/contacts/{contactId}") public ResponseEntity<Void> linkCompanyContact(@PathVariable long id,@PathVariable long contactId,@RequestParam(defaultValue="false") boolean primary){service.linkCompanyContact(id,contactId,primary);return ResponseEntity.noContent().build();}

    @GetMapping("/opportunities") public Map<String,Object> opportunities(@RequestParam(required=false) String stage,@RequestParam(required=false) Long contactId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.opportunities(stage,contactId,page,size);}
    @GetMapping("/opportunities/{id}") public Map<String,Object> opportunity(@PathVariable long id){return service.opportunityDetails(id);}
    @PostMapping("/opportunities") public ResponseEntity<Map<String,Object>> createOpportunity(@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return ResponseEntity.status(HttpStatus.CREATED).body(service.createOpportunity(body,actor));}
    @PutMapping("/opportunities/{id}") public Map<String,Object> updateOpportunity(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return service.updateOpportunity(id,body,actor);}
    @DeleteMapping("/opportunities/{id}") public ResponseEntity<Void> archiveOpportunity(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor){service.archiveOpportunity(id,actor);return ResponseEntity.noContent().build();}
    @PatchMapping("/opportunities/{id}/stage") public Map<String,Object> moveOpportunity(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return service.moveOpportunity(id,body,actor);}
    @GetMapping("/opportunities/{id}/custom-fields") public Map<String,Object> opportunityCustomValues(@PathVariable long id){return service.customValues("OPPORTUNITY",id);}
    @PatchMapping("/opportunities/{id}/custom-fields") public ResponseEntity<Void> updateOpportunityCustomValues(@PathVariable long id,@RequestBody Map<String,Object> values){service.saveCustomValues("OPPORTUNITY",id,values);return ResponseEntity.noContent().build();}

    @GetMapping("/activities") public Map<String,Object> activities(@RequestParam(required=false) Long contactId,@RequestParam(required=false) Long opportunityId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.activities(contactId,opportunityId,page,size);}
    @PostMapping("/activities") public ResponseEntity<Map<String,Object>> createActivity(@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return ResponseEntity.status(HttpStatus.CREATED).body(service.createActivity(body,actor,"MANUAL"));}
    @GetMapping("/tasks") public Map<String,Object> tasks(@RequestParam(required=false) Long contactId,@RequestParam(required=false) String owner,@RequestParam(defaultValue="false") boolean completed,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.tasks(contactId,owner,completed,page,size);}
    @PostMapping("/tasks") public ResponseEntity<Map<String,Object>> createTask(@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return ResponseEntity.status(HttpStatus.CREATED).body(service.createTask(body,actor));}
    @PutMapping("/tasks/{id}") public Map<String,Object> updateTask(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return service.updateTask(id,body,actor);}
    @DeleteMapping("/tasks/{id}") public ResponseEntity<Void> archiveTask(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor){service.archiveTask(id,actor);return ResponseEntity.noContent().build();}
    @PatchMapping("/tasks/{id}/complete") public Map<String,Object> completeTask(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor){return service.completeTask(id,actor);}

    @GetMapping("/segments") public Map<String,Object> segments(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.segments(page,size);}
    @PostMapping("/segments") public ResponseEntity<Map<String,Object>> createSegment(@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return ResponseEntity.status(HttpStatus.CREATED).body(service.createSegment(body,actor));}
    @PutMapping("/segments/{id}") public Map<String,Object> updateSegment(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor,@RequestBody Map<String,Object> body){return service.updateSegment(id,body,actor);}
    @DeleteMapping("/segments/{id}") public ResponseEntity<Void> archiveSegment(@PathVariable long id,@RequestHeader(value="X-User-Email",required=false) String actor){service.archiveSegment(id,actor);return ResponseEntity.noContent().build();}
    @GetMapping("/segments/{id}/members") public Map<String,Object> segmentMembers(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.segmentMembers(id,page,size);}
    @GetMapping("/pipeline/stages") public List<Map<String,Object>> stages(){return service.stages();}
    @GetMapping("/dashboard") public Map<String,Object> dashboard(){return service.dashboard();}

    @GetMapping("/custom-fields") public List<Map<String,Object>> customFields(@RequestParam(required=false) String subjectType){return service.customFields(subjectType);}
    @PostMapping("/custom-fields") public ResponseEntity<Map<String,Object>> createCustomField(@RequestBody Map<String,Object> body){return ResponseEntity.status(HttpStatus.CREATED).body(service.createCustomField(body));}
    @GetMapping("/saved-views") public List<Map<String,Object>> savedViews(@RequestHeader(value="X-User-Id",required=false) String userId,@RequestHeader(value="X-User-Email",required=false) String email){return service.savedViews(userKey(userId,email));}
    @PostMapping("/saved-views") public ResponseEntity<Map<String,Object>> saveView(@RequestHeader(value="X-User-Id",required=false) String userId,@RequestHeader(value="X-User-Email",required=false) String email,@RequestBody Map<String,Object> body){return ResponseEntity.status(HttpStatus.CREATED).body(service.saveView(body,userKey(userId,email)));}

    @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<Map<String,Object>> importContacts(@RequestParam("file") MultipartFile file,@RequestHeader(value="X-User-Email",required=false) String actor){return ResponseEntity.status(HttpStatus.CREATED).body(service.importContacts(file,actor));}
    @GetMapping(value="/export/contacts",produces="text/csv") public ResponseEntity<byte[]> exportContacts(){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"crm_contacts.csv\"").contentType(MediaType.parseMediaType("text/csv; charset=UTF-8")).body(service.exportContacts());}

    private String userKey(String userId,String email){return userId!=null&&!userId.isBlank()?"user:"+userId:"email:"+(email==null?"unknown":email);}
    public record BulkContactsRequest(List<Long> ids,String action,String value) {}
}
