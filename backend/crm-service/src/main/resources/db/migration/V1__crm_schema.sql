CREATE TABLE crm_companies (
  id BIGSERIAL PRIMARY KEY, name VARCHAR(180) NOT NULL, domain VARCHAR(180), industry VARCHAR(120), company_size VARCHAR(80),
  owner VARCHAR(150), notes TEXT, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_crm_companies_domain ON crm_companies(lower(domain)) WHERE domain IS NOT NULL AND domain <> '';

CREATE TABLE crm_contacts (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT UNIQUE, first_name VARCHAR(100), last_name VARCHAR(100), display_name VARCHAR(220) NOT NULL,
  email VARCHAR(180), phone VARCHAR(50), lifecycle_stage VARCHAR(40) NOT NULL DEFAULT 'LEAD', owner VARCHAR(150), company_id BIGINT REFERENCES crm_companies(id),
  tags TEXT NOT NULL DEFAULT '[]', source VARCHAR(80), notes TEXT, archived BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_crm_contacts_search ON crm_contacts(lower(display_name), lower(email));
CREATE INDEX idx_crm_contacts_stage_owner ON crm_contacts(lifecycle_stage, owner);
CREATE TABLE crm_contact_company (
  contact_id BIGINT NOT NULL REFERENCES crm_contacts(id) ON DELETE CASCADE,
  company_id BIGINT NOT NULL REFERENCES crm_companies(id) ON DELETE CASCADE,
  is_primary BOOLEAN NOT NULL DEFAULT FALSE,
  linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(contact_id, company_id)
);

CREATE TABLE crm_pipeline_stages (
  id BIGSERIAL PRIMARY KEY, pipeline_key VARCHAR(80) NOT NULL DEFAULT 'default', name VARCHAR(80) NOT NULL,
  sort_order INT NOT NULL, probability NUMERIC(5,2) NOT NULL DEFAULT 0, is_closed BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE(pipeline_key, name)
);
CREATE TABLE crm_opportunities (
  id BIGSERIAL PRIMARY KEY, name VARCHAR(200) NOT NULL, contact_id BIGINT REFERENCES crm_contacts(id), company_id BIGINT REFERENCES crm_companies(id),
  pipeline_key VARCHAR(80) NOT NULL DEFAULT 'default', stage VARCHAR(80) NOT NULL, amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK(amount >= 0),
  currency VARCHAR(3) NOT NULL DEFAULT 'USD', probability NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK(probability BETWEEN 0 AND 100),
  expected_close_date DATE, owner VARCHAR(150), status VARCHAR(30) NOT NULL DEFAULT 'OPEN', notes TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_crm_opportunities_pipeline ON crm_opportunities(pipeline_key, stage, status);

CREATE TABLE crm_activities (
  id BIGSERIAL PRIMARY KEY, contact_id BIGINT REFERENCES crm_contacts(id), opportunity_id BIGINT REFERENCES crm_opportunities(id),
  activity_type VARCHAR(40) NOT NULL, subject VARCHAR(240) NOT NULL, body TEXT, occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(150), source VARCHAR(40) NOT NULL DEFAULT 'MANUAL', event_key VARCHAR(240) UNIQUE, metadata TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_crm_activities_timeline ON crm_activities(contact_id, occurred_at DESC);

CREATE TABLE crm_tasks (
  id BIGSERIAL PRIMARY KEY, title VARCHAR(240) NOT NULL, description TEXT, contact_id BIGINT REFERENCES crm_contacts(id),
  opportunity_id BIGINT REFERENCES crm_opportunities(id), owner VARCHAR(150), due_at TIMESTAMP, priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  completed BOOLEAN NOT NULL DEFAULT FALSE, completed_at TIMESTAMP, created_by VARCHAR(150), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_crm_tasks_due ON crm_tasks(completed, due_at);

CREATE TABLE crm_segments (
  id BIGSERIAL PRIMARY KEY, name VARCHAR(160) NOT NULL UNIQUE, description TEXT, criteria TEXT NOT NULL DEFAULT '{}',
  created_by VARCHAR(150), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE crm_segment_members (
  segment_id BIGINT NOT NULL REFERENCES crm_segments(id) ON DELETE CASCADE, contact_id BIGINT NOT NULL REFERENCES crm_contacts(id) ON DELETE CASCADE,
  added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(segment_id, contact_id)
);

CREATE TABLE crm_custom_fields (
  id BIGSERIAL PRIMARY KEY, subject_type VARCHAR(30) NOT NULL CHECK(subject_type IN ('CONTACT','OPPORTUNITY','COMPANY')),
  field_key VARCHAR(80) NOT NULL, label VARCHAR(120) NOT NULL, field_type VARCHAR(30) NOT NULL DEFAULT 'TEXT', is_required BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(subject_type, field_key)
);
CREATE TABLE crm_custom_field_values (
  field_id BIGINT NOT NULL REFERENCES crm_custom_fields(id) ON DELETE CASCADE, subject_id BIGINT NOT NULL, value TEXT,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(field_id, subject_id)
);
CREATE TABLE crm_saved_views (
  id BIGSERIAL PRIMARY KEY, user_key VARCHAR(150) NOT NULL, name VARCHAR(120) NOT NULL, view_type VARCHAR(30) NOT NULL,
  filters TEXT NOT NULL DEFAULT '{}', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(user_key, name, view_type)
);
CREATE TABLE crm_audit_log (
  id BIGSERIAL PRIMARY KEY, entity_type VARCHAR(30) NOT NULL, entity_id BIGINT NOT NULL, action VARCHAR(60) NOT NULL,
  actor VARCHAR(150), before_data TEXT, after_data TEXT, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_crm_audit_entity ON crm_audit_log(entity_type, entity_id, created_at DESC);

INSERT INTO crm_pipeline_stages(pipeline_key,name,sort_order,probability,is_closed) VALUES
 ('default','Qualification',1,10,FALSE),('default','Discovery',2,25,FALSE),('default','Proposal',3,50,FALSE),
 ('default','Negotiation',4,75,FALSE),('default','Won',5,100,TRUE),('default','Lost',6,0,TRUE)
ON CONFLICT (pipeline_key,name) DO NOTHING;
