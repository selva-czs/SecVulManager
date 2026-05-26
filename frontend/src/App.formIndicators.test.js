import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

const appSource = readFileSync(new URL('./App.jsx', import.meta.url), 'utf8');

describe('form visual indicators', () => {
  it('has shared support for required, optional, default, and filter labels', () => {
    assert.match(appSource, /function Field\(\{[^}]*optional = false/s);
    assert.match(appSource, /function Field\(\{[^}]*defaultHint = ''/s);
    assert.match(appSource, /function FilterField\(/);
    assert.match(appSource, /Required/);
    assert.match(appSource, /Optional/);
    assert.match(appSource, /Default:/);
  });

  it('marks primary self-service forms with visible requirement intent', () => {
    assert.match(appSource, /<Field label="Username" required>/);
    assert.match(appSource, /<Field label="Password" required>/);
    assert.match(appSource, /<Field label="Vendor Product Name" required>/);
    assert.match(appSource, /<Field label="Customer Name" required>/);
    assert.match(appSource, /<Field label="Temporary Password" required>/);
    assert.match(appSource, /<Field label="Customer" required>/);
    assert.match(appSource, /<Field label="Enabled Software Template" required>/);
    assert.match(appSource, /<Field label="Scan File" required>/);
  });

  it('adds persistent labels to dense filter controls', () => {
    assert.match(appSource, /<FilterField label="Search findings"[^>]*>/);
    assert.match(appSource, /<FilterField label="Software"[^>]*>/);
    assert.match(appSource, /<FilterField label="Template"[^>]*>/);
    assert.match(appSource, /<FilterField label="Severity"[^>]*>/);
    assert.match(appSource, /<FilterField label="Workflow"[^>]*>/);
    assert.match(appSource, /<FilterField label="Search users"[^>]*>/);
  });

  it('supports saved vulnerability views, staged filters, and sorting controls', () => {
    assert.match(appSource, /const DEFAULT_ACTIVE_VIEW_STATE/);
    assert.match(appSource, /draftActiveViewState/);
    assert.match(appSource, /appliedActiveViewState/);
    assert.match(appSource, /Apply filters/);
    assert.match(appSource, /Clear all/);
    assert.match(appSource, /Save view/);
    assert.match(appSource, /Sort by/);
    assert.match(appSource, /Priority/);
  });

  it('shows remediation comments and state journey in the finding detail page', () => {
    assert.match(appSource, /State Journey/);
    assert.match(appSource, /FALSE_POSITIVE/);
    assert.match(appSource, /ACCEPTED_RISK/);
    assert.match(appSource, /getRemediationEvents/);
  });
});
