const configuredApiBase = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '');
const API_BASE = configuredApiBase || '/api';

/**
 * Custom fetch wrapper to automatically include credentials (session cookie)
 */
async function apiFetch(path, options = {}) {
  const url = `${API_BASE}${path}`;
  
  // Enforce session support
  options.credentials = 'include';
  
  if (!options.headers) {
    options.headers = {};
  }
  
  // Don't set Content-Type header if body is FormData (browser does it with boundary automatically)
  if (!(options.body instanceof FormData) && !options.headers['Content-Type']) {
    options.headers['Content-Type'] = 'application/json';
  }

  let response;
  try {
    response = await fetch(url, options);
  } catch (error) {
    const err = new Error(`Unable to reach the API at ${API_BASE}. Confirm the backend is running and the frontend API URL is correct.`);
    err.cause = error;
    throw err;
  }
  
  if (response.status === 401) {
    // Return custom error so frontend can redirect to Login page
    const err = new Error('Unauthorized');
    err.status = 401;
    throw err;
  }
  
  if (!response.ok) {
    const text = await response.text();
    let msg = 'API Request Failed';
    let details = null;
    try {
      const json = JSON.parse(text);
      details = json;
      msg = json.error || json.message || msg;
    } catch {
      msg = text || msg;
    }
    const err = new Error(msg);
    err.status = response.status;
    err.details = details;
    throw err;
  }

  // Handle blob responses (e.g. for downloadable files)
  const contentType = response.headers.get('Content-Type');
  const disposition = response.headers.get('Content-Disposition');
  if (disposition?.includes('attachment') || contentType?.includes('text/csv') || contentType?.includes('spreadsheet') || contentType?.includes('octet-stream')) {
    return response.blob();
  }

  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  // --- AUTH ENTITY ---
  login: (username, password) => 
    apiFetch('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    }),
  logout: () => 
    apiFetch('/auth/logout', {
      method: 'POST'
    }),
  checkStatus: () => 
    apiFetch('/auth/status', {
      method: 'GET'
    }),

  // --- CUSTOMER ENTITY ---
  getCustomers: () => 
    apiFetch('/customers', {
      method: 'GET'
    }),
  createCustomer: (customerName) => 
    apiFetch('/customers', {
      method: 'POST',
      body: JSON.stringify({ customerName })
    }),
  updateCustomer: (customerId, patch) =>
    apiFetch(`/customers/${customerId}`, {
      method: 'PUT',
      body: JSON.stringify(typeof patch === 'string' ? { customerName: patch } : patch)
    }),
  getCustomerSoftwareAccess: (customerId) =>
    apiFetch(`/customers/${customerId}/software-access`, {
      method: 'GET'
    }),
  updateCustomerSoftwareAccess: (customerId, assignments) =>
    apiFetch(`/customers/${customerId}/software-access`, {
      method: 'PUT',
      body: JSON.stringify({ assignments })
    }),
  deleteCustomer: (customerId) =>
    apiFetch(`/customers/${customerId}`, {
      method: 'DELETE'
    }),

  // --- SOFTWARE & TEMPLATES ENTITY ---
  getSoftware: () => 
    apiFetch('/software', {
      method: 'GET'
    }),
  createSoftware: (softwareName) => 
    apiFetch('/software', {
      method: 'POST',
      body: JSON.stringify({ softwareName })
    }),
  updateSoftware: (softwareId, patch) =>
    apiFetch(`/software/${softwareId}`, {
      method: 'PUT',
      body: JSON.stringify(patch)
    }),
  deleteSoftware: (softwareId) =>
    apiFetch(`/software/${softwareId}`, {
      method: 'DELETE'
    }),
  getTemplates: (customerId = null) => 
    apiFetch(customerId ? `/customers/${customerId}/templates` : '/templates', {
      method: 'GET'
    }),
  createTemplate: (templateName, fileFormat, softwareId, customerId = null, hasHeaderRow = true, description = '', options = {}) => 
    apiFetch(customerId ? `/customers/${customerId}/software/${softwareId}/templates` : `/software/${softwareId}/templates`, {
      method: 'POST',
      body: JSON.stringify({ name: templateName, fileFormat, hasHeaderRow, description, ...options })
    }),
  updateTemplate: (templateId, patch) =>
    apiFetch(`/templates/${templateId}`, {
      method: 'PUT',
      body: JSON.stringify(patch)
    }),
  deleteTemplate: (templateId) =>
    apiFetch(`/templates/${templateId}`, {
      method: 'DELETE'
    }),
  getTemplateSchema: () =>
    apiFetch('/templates/schema', {
      method: 'GET'
    }),
  autoGenerateTemplate: (templateId, file, format, hasHeader) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('format', format);
    formData.append('hasHeader', hasHeader);
    return apiFetch(`/templates/${templateId}/sample`, {
      method: 'POST',
      body: formData
    });
  },
  saveTemplateMappings: (templateId, mappings) => 
    apiFetch(`/templates/${templateId}/mapping`, {
      method: 'PUT',
      body: JSON.stringify(mappings)
    }),
  previewTemplateMappings: (templateId, mappings) =>
    apiFetch(`/templates/${templateId}/preview`, {
      method: 'POST',
      body: JSON.stringify(mappings)
    }),

  // --- UPLOADS & INGESTION ---
  ingestFile: (file, customerId, templateId, options = {}) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('customerId', customerId);
    formData.append('templateId', templateId);
    formData.append('queueMode', options.queueMode || 'REJECT_IF_BUSY');
    if (options.queueComment) formData.append('queueComment', options.queueComment);
    return apiFetch('/uploads/ingest', {
      method: 'POST',
      body: formData
    });
  },
  getUploadHistory: (filters = {}) => {
    const params = new URLSearchParams();
    if (typeof filters === 'string') params.set('customerId', filters);
    else Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '' && value !== 'ALL') params.set(key, value);
    });
    const query = params.toString();
    return apiFetch(`/uploads${query ? `?${query}` : ''}`, {
      method: 'GET'
    });
  },
  downloadErrorLog: (uploadId) => 
    apiFetch(`/uploads/${uploadId}/error-log`, {
      method: 'GET'
    }),
  downloadOriginalUpload: (uploadId) =>
    apiFetch(`/uploads/${uploadId}/sample`, {
      method: 'GET'
    }),
  activateUpload: (uploadId) =>
    apiFetch(`/uploads/${uploadId}/activate`, {
      method: 'POST'
    }),

  // --- VULNERABILITIES & REMEDIATION ---
  getActiveFindings: (customerId, filters = {}) => {
    const params = new URLSearchParams({ customerId });
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '' && value !== 'ALL') params.set(key, value);
    });
    return apiFetch(`/vulnerabilities?${params.toString()}`, {
      method: 'GET'
    });
  },
  getRemediations: (customerId) => 
    apiFetch(`/vulnerabilities/remediation?customerId=${customerId}`, {
      method: 'GET'
    }),
  getRemediationEvents: (customerId, logicalFindingHash) =>
    apiFetch(`/vulnerabilities/remediation/events?customerId=${customerId}&logicalFindingHash=${encodeURIComponent(logicalFindingHash)}`, {
      method: 'GET'
    }),
  updateRemediation: (customerId, logicalFindingHash, workflowStatus, notes) => 
    apiFetch('/vulnerabilities/remediation', {
      method: 'POST',
      body: JSON.stringify({ customerId, logicalFindingHash, workflowStatus, notes })
    }),
  createFinding: (finding) =>
    apiFetch('/vulnerabilities', {
      method: 'POST',
      body: JSON.stringify(finding)
    }),
  updateFinding: (findingId, finding) =>
    apiFetch(`/vulnerabilities/${findingId}`, {
      method: 'PUT',
      body: JSON.stringify(finding)
    }),
  deleteFinding: (findingId) =>
    apiFetch(`/vulnerabilities/${findingId}`, {
      method: 'DELETE'
    }),
  getSavedViews: (viewType = 'ACTIVE_FINDINGS') =>
    apiFetch(`/users/me/saved-views?viewType=${encodeURIComponent(viewType)}`, {
      method: 'GET'
    }),
  createSavedView: (view) =>
    apiFetch('/users/me/saved-views', {
      method: 'POST',
      body: JSON.stringify(view)
    }),
  updateSavedView: (viewId, view) =>
    apiFetch(`/users/me/saved-views/${viewId}`, {
      method: 'PUT',
      body: JSON.stringify(view)
    }),
  deleteSavedView: (viewId) =>
    apiFetch(`/users/me/saved-views/${viewId}`, {
      method: 'DELETE'
    }),
  setDefaultSavedView: (viewId) =>
    apiFetch(`/users/me/saved-views/${viewId}/default`, {
      method: 'POST'
    }),

  // --- USER ADMINISTRATION ---
  getUsers: () => 
    apiFetch('/users', {
      method: 'GET'
    }),
  createUser: (username, password, fullName, role, allowedCustomerIds = []) => 
    apiFetch('/users', {
      method: 'POST',
      body: JSON.stringify({ username, password, fullName, role, allowedCustomerIds })
    }),
  updateUserAccess: (userId, allowedCustomerIds) => 
    apiFetch(`/users/${userId}/access`, {
      method: 'PUT',
      body: JSON.stringify({ allowedCustomerIds })
    }),
  updateUserStatus: (userId, enabled) => 
    apiFetch(`/users/${userId}/status`, {
      method: 'PUT',
      body: JSON.stringify({ enabled })
    }),
  deleteUser: (userId) =>
    apiFetch(`/users/${userId}`, {
      method: 'DELETE'
    }),
  restoreUser: (userId) =>
    apiFetch(`/users/${userId}/restore`, {
      method: 'PUT'
    })
};
