import { Fragment, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertCircle,
  BarChart2,
  CheckCircle,
  ChevronDown,
  Download,
  Eye,
  FileSpreadsheet,
  Layers,
  LogOut,
  Menu,
  Moon,
  Pin,
  PinOff,
  Plus,
  RefreshCw,
  Search,
  Shield,
  Sun,
  Trash2,
  Upload,
  UserPlus,
  Users,
  X,
} from 'lucide-react';
import { api } from './api';

const TARGET_FIELDS = [
  { value: 'issue_title', label: 'Issue Title', type: 'STRING', required: true },
  { value: 'severity', label: 'Severity', type: 'STRING' },
  { value: 'cvss_score', label: 'CVSS Score', type: 'NUMERIC' },
  { value: 'cvss_vector', label: 'CVSS Vector', type: 'STRING' },
  { value: 'cve_id', label: 'CVE ID', type: 'STRING' },
  { value: 'oid', label: 'OID', type: 'STRING' },
  { value: 'summary', label: 'Summary', type: 'STRING' },
  { value: 'impact', label: 'Impact', type: 'STRING' },
  { value: 'solution', label: 'Solution', type: 'STRING' },
  { value: 'vulnerability_insight', label: 'Insight', type: 'STRING' },
  { value: 'vulnerability_detection_result', label: 'Detection Result', type: 'STRING' },
  { value: 'vulnerability_detection_method', label: 'Detection Method', type: 'STRING' },
  { value: 'affected_devices', label: 'Affected Devices', type: 'STRING' },
  { value: 'number_of_devices', label: 'Number of Devices', type: 'INTEGER' },
  { value: 'references_info', label: 'References', type: 'STRING' },
  { value: 'known_exploited', label: 'Known Exploited', type: 'BOOLEAN' },
  { value: 'known_ransomware_campaign', label: 'Known Ransomware Campaign', type: 'BOOLEAN' },
  { value: 'last_detected_at', label: 'Last Detected At', type: 'DATE' },
];

const TRANSFORMS = ['TRIM', 'TO_UPPER', 'TO_LOWER', 'REMOVESPACES'];
const CONVERSION_TYPES = ['NONE', 'TO_STRING', 'TO_NUMBER', 'TO_DATE', 'TO_BOOLEAN'];
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const WORKFLOW_STATUSES = ['OPEN', 'IN_PROGRESS', 'FALSE_POSITIVE', 'RESOLVED'];
const FORMATS = ['CSV', 'TSV', 'PSV', 'XLS', 'XLSX'];
const TEMPLATE_SAMPLE_TEST_ENABLED = false;

const emptyFinding = {
  issueTitle: '',
  severity: 'MEDIUM',
  cvssScore: '',
  cveId: '',
  affectedDevices: '',
  summary: '',
  solution: '',
  numberOfDevices: 1,
  knownExploited: false,
  knownRansomwareCampaign: false,
};

function Field({ label, children, required = false, error = '' }) {
  return (
    <label className="block">
      <span className={`mb-2 block text-[10px] font-bold uppercase tracking-wider ${error ? 'text-red-300' : 'text-slate-400'}`}>
        {label}{required && <span className="ml-1 text-red-300">*</span>}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs font-semibold text-red-300">{error}</span>}
    </label>
  );
}

function TextInput(props) {
  return (
    <input
      {...props}
      className={`w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white outline-none transition focus:border-brand-blue ${props.className || ''}`}
    />
  );
}

function SelectInput(props) {
  return (
    <select
      {...props}
      className={`w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white outline-none transition focus:border-brand-blue ${props.className || ''}`}
    />
  );
}

function TextArea(props) {
  return (
    <textarea
      {...props}
      className={`w-full resize-none rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white outline-none transition focus:border-brand-blue ${props.className || ''}`}
    />
  );
}

function Button({ children, variant = 'primary', className = '', ...props }) {
  const variants = {
    primary: 'btn-primary bg-brand-blue text-slate-950 hover:opacity-90',
    secondary: 'btn-secondary border border-brand-blue/35 bg-brand-blue/10 text-brand-blue hover:bg-brand-blue hover:text-slate-950',
    tertiary: 'btn-tertiary border border-slate-800 bg-slate-950 text-slate-300 hover:border-brand-blue hover:text-white',
    gradient: 'btn-gradient bg-z1n-blue-pink text-white hover:opacity-90',
    ghost: 'btn-tertiary border border-slate-800 bg-slate-950 text-slate-300 hover:border-brand-blue hover:text-white',
    danger: 'btn-danger border border-red-500/30 bg-red-500/10 text-red-300 hover:bg-red-500 hover:text-white',
    warn: 'btn-warn border border-amber-500/30 bg-amber-500/10 text-amber-300 hover:bg-amber-500 hover:text-slate-950',
  };
  return (
    <button
      {...props}
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`}
    >
      {children}
    </button>
  );
}

function ActionButton({ icon, label, title, variant = 'ghost', onClick }) {
  return (
    <Button type="button" variant={variant} onClick={onClick} className="whitespace-nowrap" title={title || label}>
      {icon}
      {label}
    </Button>
  );
}

function StatusPill({ children, tone = 'slate' }) {
  const tones = {
    green: 'border-green-500/25 bg-green-500/10 text-green-300',
    red: 'border-red-500/25 bg-red-500/10 text-red-300',
    amber: 'border-amber-500/25 bg-amber-500/10 text-amber-300',
    blue: 'border-brand-blue/25 bg-brand-blue/10 text-brand-blue',
    purple: 'border-purple-500/25 bg-purple-500/10 text-purple-300',
    slate: 'border-slate-700 bg-slate-900 text-slate-300',
  };
  return <span className={`rounded-md border px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${tones[tone]}`}>{children}</span>;
}

function StatusToggle({ enabled, onToggle, disabled = false }) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onToggle}
      className="inline-flex min-w-32 items-center gap-2 rounded-full px-1 py-1 text-xs font-semibold text-slate-300 transition disabled:cursor-not-allowed disabled:opacity-50"
      title={enabled ? 'Disable' : 'Enable'}
    >
      <span className={`relative h-6 w-11 rounded-full border transition ${enabled ? 'border-green-400/40 bg-green-500/25' : 'border-slate-700 bg-slate-800'}`}>
        <span className={`absolute top-0.5 h-5 w-5 rounded-full transition ${enabled ? 'left-5 bg-green-300' : 'left-0.5 bg-slate-500'}`} />
      </span>
      <span className={enabled ? 'text-green-300' : 'text-slate-400'}>{enabled ? 'Enabled' : 'Disabled'}</span>
    </button>
  );
}

function confirmStatusChange(confirmAction, name, enabled) {
  return confirmAction({
    title: enabled ? 'Disable Record' : 'Enable Record',
    message: enabled
      ? `Set "${name}" to Disabled? It will stay in Active but cannot be used.`
      : `Set "${name}" to Enabled? It will be available for use.`,
    confirmLabel: enabled ? 'Disable' : 'Enable',
    variant: enabled ? 'danger' : 'primary',
  });
}

function confirmArchive(confirmAction, name) {
  return confirmAction({
    title: 'Archive Record',
    message: `Archive "${name}"? It will move to Archive and can be restored later.`,
    confirmLabel: 'Archive',
    variant: 'danger',
  });
}

function confirmRestore(confirmAction, name) {
  return confirmAction({
    title: 'Restore Record',
    message: `Restore "${name}"? It will return to Active as Disabled.`,
    confirmLabel: 'Restore',
    variant: 'primary',
  });
}

function InteractionPage({ title, subtitle, icon, onBack, actions, children }) {
  return (
    <div className="space-y-5">
      <div className="sticky top-0 z-20 -mx-6 -mt-6 border-b border-slate-800 bg-slate-950/95 px-6 py-4 backdrop-blur">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            {icon}
            <div className="min-w-0">
              <h3 className="m-0 truncate text-lg font-bold text-white">{title}</h3>
              {subtitle && <p className="m-0 mt-1 text-xs text-slate-500">{subtitle}</p>}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {actions}
            <Button variant="ghost" onClick={onBack}><ChevronDown className="h-4 w-4 rotate-90" /> Back</Button>
          </div>
        </div>
      </div>
      {children}
    </div>
  );
}

function ConfirmDialog({ title, message, details = [], confirmLabel, variant, onCancel, onConfirm }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
      <section className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 p-5 shadow-2xl">
        <div className="mb-5 flex items-start gap-3">
          <div className={`mt-0.5 rounded-lg p-2 ${variant === 'danger' ? 'bg-red-500/10 text-red-300' : 'bg-brand-blue/10 text-brand-blue'}`}>
            <AlertCircle className="h-5 w-5" />
          </div>
          <div>
            <h3 className="m-0 text-base font-bold text-white">{title}</h3>
            {message && <p className="m-0 mt-2 text-sm leading-6 text-slate-300">{message}</p>}
            {details.length > 0 && (
              <p className="m-0 mt-2 text-xs leading-5 text-slate-500">{details.join(' · ')}</p>
            )}
          </div>
        </div>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onCancel}>Cancel</Button>
          <Button variant={variant === 'danger' ? 'danger' : 'gradient'} onClick={onConfirm}>{confirmLabel}</Button>
        </div>
      </section>
    </div>
  );
}

function MetricCard({ label, value, tone = 'blue' }) {
  const color = tone === 'red' ? 'text-red-300' : tone === 'amber' ? 'text-amber-300' : tone === 'green' ? 'text-green-300' : 'text-brand-blue';
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
      <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500">{label}</div>
      <div className={`mt-1 text-2xl font-extrabold ${color}`}>{value}</div>
    </div>
  );
}

export default function App() {
  const [loading, setLoading] = useState(true);
  const [user, setUser] = useState(null);
  const [login, setLogin] = useState({ username: '', password: '' });
  const [authError, setAuthError] = useState('');

  const [section, setSection] = useState('vuln-dashboard');
  const [sectionViewKey, setSectionViewKey] = useState(0);
  const [selectedCustomerId, setSelectedCustomerId] = useState('ALL');
  const [customers, setCustomers] = useState([]);
  const [softwares, setSoftwares] = useState([]);
  const [templates, setTemplates] = useState([]);
  const [uploadHistory, setUploadHistory] = useState([]);
  const [usersList, setUsersList] = useState([]);
  const [findingsByCustomer, setFindingsByCustomer] = useState({});
  const [remediationsByCustomer, setRemediationsByCustomer] = useState({});

  const [searchQuery, setSearchQuery] = useState('');
  const [severityFilter, setSeverityFilter] = useState('ALL');
  const [remediationFilter, setRemediationFilter] = useState('ALL');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');

  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [findingModal, setFindingModal] = useState(null);
  const [findingDetailModal, setFindingDetailModal] = useState(null);
  const [uploadDetailModal, setUploadDetailModal] = useState(null);
  const [templateModal, setTemplateModal] = useState(null);
  const [userAccessModal, setUserAccessModal] = useState(null);
  const [workspaceDirty, setWorkspaceDirty] = useState({});
  const [confirmDialog, setConfirmDialog] = useState(null);
  const confirmResolverRef = useRef(null);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [sidebarPinned, setSidebarPinned] = useState(true);
  const [theme, setTheme] = useState('dark');

  const isAdmin = user?.role === 'SUPER_ADMIN';
  const canManageConfig = user?.role === 'SUPER_ADMIN' || user?.role === 'SECURITY_OPERATOR';
  const allowedCustomers = customers.filter((customer) => !customer.archived);

  useEffect(() => {
    api.checkStatus()
      .then((res) => {
        setUser(res);
        if (res.allowedCustomers?.length === 1) setSelectedCustomerId(res.allowedCustomers[0].id);
      })
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  async function refreshAll() {
    setError('');
    const [custs, sw, temps, hist] = await Promise.all([
      api.getCustomers(),
      api.getSoftware(),
      api.getTemplates(),
      api.getUploadHistory(),
    ]);
    setCustomers(custs || []);
    setSoftwares(sw || []);
    setTemplates(temps || []);
    setUploadHistory(hist || []);
    if (selectedCustomerId === 'ALL' && custs?.length === 1) setSelectedCustomerId(custs[0].id);
    if (user?.role === 'SUPER_ADMIN') {
      setUsersList(await api.getUsers());
    }
    await refreshFindings(custs || []);
  }

  async function refreshFindings(customerList = customers) {
    const targetCustomers = selectedCustomerId === 'ALL'
      ? customerList
      : customerList.filter((c) => c.id === selectedCustomerId);
    const entries = await Promise.all(targetCustomers.map(async (customer) => {
      try {
        const [findings, rems] = await Promise.all([
          api.getActiveFindings(customer.id),
          api.getRemediations(customer.id),
        ]);
        return [customer.id, findings || [], rems || []];
      } catch {
        return [customer.id, [], []];
      }
    }));
    setFindingsByCustomer((prev) => {
      const next = { ...prev };
      entries.forEach(([id, findings]) => { next[id] = findings; });
      return next;
    });
    setRemediationsByCustomer((prev) => {
      const next = { ...prev };
      entries.forEach(([id, , rems]) => {
        next[id] = Object.fromEntries(rems.map((r) => [r.logicalFindingHash, r]));
      });
      return next;
    });
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (user) refreshAll();
    // Initial registry load is intentionally tied to auth state.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  useEffect(() => {
    if (user && customers.length) refreshFindings(customers);
    // Customer scope changes should reload the active findings for that scope.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCustomerId]);

  const selectedCustomers = useMemo(() => (
    selectedCustomerId === 'ALL' ? allowedCustomers : allowedCustomers.filter((c) => c.id === selectedCustomerId)
  ), [allowedCustomers, selectedCustomerId]);

  const selectedFindings = useMemo(() => {
    const rows = selectedCustomers.flatMap((customer) => (findingsByCustomer[customer.id] || []).map((finding) => ({ ...finding, customer })));
    const q = searchQuery.toLowerCase();
    return rows.filter((f) => {
      const hash = findingHash(f);
      const rem = remediationsByCustomer[f.customer.id]?.[hash];
      const workflowStatus = rem?.workflowStatus || 'OPEN';
      const text = [f.issueTitle, f.cveId, f.affectedDevices, f.summary].filter(Boolean).join(' ').toLowerCase();
      return (!q || text.includes(q))
        && (severityFilter === 'ALL' || f.severity === severityFilter)
        && (remediationFilter === 'ALL' || workflowStatus === remediationFilter);
    });
  }, [selectedCustomers, findingsByCustomer, remediationsByCustomer, searchQuery, severityFilter, remediationFilter]);

  const selectedHistory = useMemo(() => uploadHistory.filter((run) => (
    selectedCustomerId === 'ALL' || run.customer?.id === selectedCustomerId
  )), [uploadHistory, selectedCustomerId]);
  const interactionOpen = templateModal || uploadModalOpen || findingModal || findingDetailModal || uploadDetailModal || userAccessModal;

  const handleLogin = async (event) => {
    event.preventDefault();
    setAuthError('');
    try {
      const res = await api.login(login.username, login.password);
      setUser(res);
      if (res.allowedCustomers?.length === 1) setSelectedCustomerId(res.allowedCustomers[0].id);
      setLogin({ username: '', password: '' });
    } catch (err) {
      setAuthError(err.message || 'Authentication failed');
    }
  };

  const handleLogout = async () => {
    await api.logout().catch(() => {});
    setUser(null);
    setSection('vuln-dashboard');
  };

  const announce = (message) => {
    setNotice(message);
    setError('');
  };

  const fail = (err) => {
    setError(err.message || String(err));
    setNotice('');
  };

  const confirmAction = ({ title = 'Confirm Action', message, details = [], confirmLabel = 'Confirm', variant = 'primary' }) => new Promise((resolve) => {
    confirmResolverRef.current = resolve;
    setConfirmDialog({ title, message, details, confirmLabel, variant });
  });

  const setWorkspaceDirtyFlag = (key, dirty) => {
    setWorkspaceDirty((current) => {
      if (current[key] === dirty) return current;
      return { ...current, [key]: dirty };
    });
  };

  const dirtyWorkspaceLabels = {
    template: 'Template mapping changes',
    upload: 'Upload form selection',
    customerSoftwareAccess: 'Customer software access changes',
    userAccess: 'User customer access changes',
    findingForm: 'Finding form changes',
  };

  const activeDirtyLabels = () => Object.entries(workspaceDirty)
    .filter(([, dirty]) => dirty)
    .map(([key]) => dirtyWorkspaceLabels[key] || 'Unsaved changes');

  const confirmDiscardWorkspaceChanges = async () => {
    const labels = activeDirtyLabels();
    if (!labels.length) return true;
    return confirmAction({
      title: 'Leave This Screen?',
      message: 'Unsaved changes on this screen will be lost.',
      details: labels,
      confirmLabel: 'Leave Screen',
      variant: 'danger',
    });
  };

  const closeOpenWorkspace = () => {
    setTemplateModal(null);
    setUploadModalOpen(false);
    setFindingModal(null);
    setFindingDetailModal(null);
    setUploadDetailModal(null);
    setUserAccessModal(null);
    setWorkspaceDirty({});
  };

  const guardedCloseWorkspace = async () => {
    const confirmed = await confirmDiscardWorkspaceChanges();
    if (!confirmed) return false;
    closeOpenWorkspace();
    return true;
  };

  const navigateToSection = async (targetSection) => {
    const confirmed = await confirmDiscardWorkspaceChanges();
    if (!confirmed) return;
    closeOpenWorkspace();
    setSection(targetSection);
    setSectionViewKey((current) => current + 1);
    setNotice('');
    setError('');
  };

  const changeCustomerScope = async (nextCustomerId) => {
    const confirmed = await confirmDiscardWorkspaceChanges();
    if (!confirmed) return;
    closeOpenWorkspace();
    setSelectedCustomerId(nextCustomerId);
    setSectionViewKey((current) => current + 1);
    setNotice('');
    setError('');
  };

  const resolveConfirm = (confirmed) => {
    confirmResolverRef.current?.(confirmed);
    confirmResolverRef.current = null;
    setConfirmDialog(null);
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950 text-white">
        <RefreshCw className="mr-3 h-6 w-6 animate-spin text-brand-blue" />
        Initializing SecVulManager
      </div>
    );
  }

  if (!user) {
    return (
      <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-950 p-4 text-white">
        <div className="absolute left-10 top-10 h-72 w-72 rounded-full bg-brand-blue/20 blur-3xl" />
        <div className="absolute bottom-10 right-10 h-72 w-72 rounded-full bg-brand-pink/20 blur-3xl" />
        <form onSubmit={handleLogin} className="glass relative w-full max-w-md rounded-2xl border border-slate-800 p-8 shadow-2xl">
          <div className="mb-8 flex flex-col items-center text-center">
            <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-z1n-blue-pink">
              <Shield className="h-8 w-8" />
            </div>
            <h1 className="m-0 text-3xl font-extrabold">SecVulManager</h1>
            <p className="mt-2 text-sm text-slate-400">Multi-customer vulnerability operations console</p>
          </div>
          {authError && <AlertBox tone="red">{authError}</AlertBox>}
          <div className="space-y-4">
            <Field label="Username">
              <TextInput value={login.username} onChange={(e) => setLogin({ ...login, username: e.target.value })} placeholder="admin" />
            </Field>
            <Field label="Password">
              <TextInput type="password" value={login.password} onChange={(e) => setLogin({ ...login, password: e.target.value })} placeholder="admin_pass" />
            </Field>
            <Button type="submit" variant="gradient" className="w-full py-3">
              Authenticate Operator
            </Button>
            <button
              type="button"
              onClick={() => setLogin({ username: 'admin', password: 'admin_pass' })}
              className="w-full rounded-lg border border-slate-800 bg-slate-950/80 px-3 py-2 text-left text-xs text-slate-400 hover:border-brand-blue hover:text-white"
            >
              Quick test: admin / admin_pass
            </button>
          </div>
        </form>
      </div>
    );
  }

  return (
    <div className={`app-shell theme-${theme} flex h-screen overflow-hidden bg-slate-950 text-white`}>
      <aside
        className={`sticky top-0 flex h-screen flex-shrink-0 flex-col overflow-hidden border-r border-slate-800 bg-slate-950 transition-all ${sidebarCollapsed ? 'w-20' : 'w-72'}`}
        onMouseEnter={() => !sidebarPinned && setSidebarCollapsed(false)}
        onMouseLeave={() => !sidebarPinned && setSidebarCollapsed(true)}
      >
        <div className="flex-shrink-0 border-b border-slate-800 p-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-z1n-blue-pink">
              <Shield className="h-5 w-5" />
            </div>
            {!sidebarCollapsed && <div>
              <h1 className="m-0 text-lg font-extrabold">SecVulManager</h1>
              <p className="m-0 text-[10px] font-bold uppercase tracking-widest text-slate-500">Operations Suite</p>
            </div>}
          </div>

          {!sidebarCollapsed && <div className="mt-4 rounded-xl border border-slate-800 bg-slate-900/70 p-3">
            <div className="truncate text-sm font-semibold">{user.fullName}</div>
            <div className="mt-1 truncate text-[10px] font-bold uppercase tracking-wider text-brand-blue">{user.role}</div>
          </div>
          }

          <div className={`mt-4 grid gap-2 ${sidebarCollapsed ? 'grid-cols-1' : 'grid-cols-2'}`}>
            <SidebarUtilityButton
              title={sidebarCollapsed ? 'Expand menu' : 'Collapse menu'}
              collapsed={sidebarCollapsed}
              icon={<Menu className="h-4 w-4" />}
              label={sidebarCollapsed ? '' : 'Menu'}
              onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            />
            <SidebarUtilityButton
              title={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
              collapsed={sidebarCollapsed}
              icon={theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
              label={theme === 'dark' ? 'Light' : 'Dark'}
              onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            />
            <div className={sidebarCollapsed ? '' : 'col-span-2'}>
              <SidebarUtilityButton
                title={sidebarPinned ? 'Unpin menu for hover expansion' : 'Pin menu open'}
                collapsed={sidebarCollapsed}
                icon={sidebarPinned ? <Pin className="h-4 w-4" /> : <PinOff className="h-4 w-4" />}
                label={sidebarPinned ? 'Pinned menu' : 'Pin menu'}
                onClick={() => {
                  setSidebarPinned(!sidebarPinned);
                  setSidebarCollapsed(false);
                }}
                fullWidth
              />
            </div>
          </div>
        </div>
        <nav className="min-h-0 flex-1 space-y-1 overflow-y-auto px-4 py-4">
          {!sidebarCollapsed && <div className="px-4 pt-2 text-[10px] font-bold uppercase tracking-wider text-slate-600">Vulnerability</div>}
          <NavButton collapsed={sidebarCollapsed} active={section === 'vuln-dashboard'} onClick={() => navigateToSection('vuln-dashboard')} icon={<BarChart2 className="h-4 w-4" />} label="Dashboard" indent={!sidebarCollapsed} />
          <NavButton collapsed={sidebarCollapsed} active={section === 'vuln-management'} onClick={() => navigateToSection('vuln-management')} icon={<Shield className="h-4 w-4" />} label="Management" indent={!sidebarCollapsed} />
          <NavButton collapsed={sidebarCollapsed} active={section === 'software'} onClick={() => navigateToSection('software')} icon={<Layers className="h-4 w-4" />} label="Security Software Manager" />
          <NavButton collapsed={sidebarCollapsed} active={section === 'customers'} onClick={() => navigateToSection('customers')} icon={<Shield className="h-4 w-4" />} label="Customer Management" />
          {isAdmin && <NavButton collapsed={sidebarCollapsed} active={section === 'users'} onClick={() => navigateToSection('users')} icon={<Users className="h-4 w-4" />} label="User Management" />}
        </nav>
        <div className="flex-shrink-0 border-t border-slate-800 bg-slate-950 p-4">
          <button
            type="button"
            title="Logout"
            onClick={handleLogout}
            className={`flex w-full items-center justify-center gap-2 rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-3 text-xs font-bold text-red-200 transition hover:bg-red-500 hover:text-white ${sidebarCollapsed ? 'px-2' : ''}`}
          >
            <LogOut className="h-4 w-4" /> {!sidebarCollapsed && 'Logout'}
          </button>
        </div>
      </aside>

      <main className="min-w-0 flex-1 overflow-y-auto">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-800 bg-slate-900/95 px-6 backdrop-blur">
          <div>
            <h2 className="m-0 text-lg font-bold">{templateModal ? 'Template Mapping Workspace' : uploadModalOpen ? 'Upload Scan File' : findingDetailModal ? 'Finding Details' : uploadDetailModal ? 'Upload Run Details' : userAccessModal ? 'Customer Access' : pageTitle(section)}</h2>
            <p className="m-0 text-xs text-slate-500">{selectedCustomers.length} customer scope</p>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Customer Scope</span>
            <SelectInput value={selectedCustomerId} onChange={(e) => changeCustomerScope(e.target.value)} className="w-60">
              <option value="ALL">All assigned customers</option>
              {allowedCustomers.map((customer) => <option key={customer.id} value={customer.id}>{customer.customerName}</option>)}
            </SelectInput>
          </div>
        </header>

        <div className="p-6">
          {notice && <AlertBox tone="green">{notice}</AlertBox>}
          {error && <AlertBox tone="red">{error}</AlertBox>}
          {templateModal ? (
            <TemplateModal
              initial={templateModal}
              softwares={softwares}
              customers={allowedCustomers}
              onClose={guardedCloseWorkspace}
              onDone={async () => {
                setWorkspaceDirty({});
                setTemplateModal(null);
                await refreshAll();
                announce('Template saved.');
              }}
              fail={fail}
              onDirtyChange={(dirty) => setWorkspaceDirtyFlag('template', dirty)}
            />
          ) : uploadModalOpen ? (
            <UploadModal
              customers={allowedCustomers}
              selectedCustomerId={selectedCustomerId}
              templates={templates}
              onClose={guardedCloseWorkspace}
              onDone={async () => {
                setWorkspaceDirty({});
                setUploadModalOpen(false);
                await refreshAll();
                announce('Upload ingestion completed.');
              }}
              fail={fail}
              onDirtyChange={(dirty) => setWorkspaceDirtyFlag('upload', dirty)}
            />
          ) : findingModal ? (
            <FindingModal
              finding={findingModal}
              customers={allowedCustomers}
              selectedCustomerId={selectedCustomerId}
              onClose={() => setFindingModal(null)}
              onDone={async () => {
                setFindingModal(null);
                await refreshFindings(customers);
                announce('Finding saved.');
              }}
              fail={fail}
            />
          ) : findingDetailModal ? (
            <FindingDetailModal
              finding={findingDetailModal}
              remediation={remediationsByCustomer[findingDetailModal.customer.id]?.[findingHash(findingDetailModal)]}
              onClose={() => setFindingDetailModal(null)}
              onRemediation={async (finding, workflowStatus, notes) => {
                try {
                  await api.updateRemediation(finding.customer.id, findingHash(finding), workflowStatus, notes);
                  await refreshFindings(customers);
                  setFindingDetailModal(null);
                  announce('Finding workflow updated.');
                } catch (err) { fail(err); }
              }}
            />
          ) : uploadDetailModal ? (
            <UploadDetailModal
              run={uploadDetailModal}
              onClose={() => setUploadDetailModal(null)}
              onDownloadOriginal={async (run) => {
                try {
                  const blob = await api.downloadOriginalUpload(run.id);
                  downloadBlob(blob, originalUploadDownloadName(run));
                } catch (err) { fail(err); }
              }}
              onDownloadErrors={async (run) => {
                try {
                  const blob = await api.downloadErrorLog(run.id);
                  downloadBlob(blob, failedRowsDownloadName(run, blob));
                } catch (err) { fail(err); }
              }}
            />
          ) : userAccessModal ? (
            <UserAccessModal
              user={userAccessModal}
              customers={allowedCustomers}
              onClose={guardedCloseWorkspace}
              onDone={async () => {
                setWorkspaceDirty({});
                setUserAccessModal(null);
                await refreshAll();
                announce('User access updated.');
              }}
              fail={fail}
              onDirtyChange={(dirty) => setWorkspaceDirtyFlag('userAccess', dirty)}
            />
          ) : (section === 'vuln-dashboard' || section === 'vuln-management') && (
            <VulnerabilityManagement
              key={`${section}-${sectionViewKey}`}
              mode={section === 'vuln-dashboard' ? 'dashboard' : 'management'}
              customers={selectedCustomers}
              allCustomers={allowedCustomers}
              selectedCustomerId={selectedCustomerId}
              setSelectedCustomerId={setSelectedCustomerId}
              findings={selectedFindings}
              remediationsByCustomer={remediationsByCustomer}
              templates={templates}
              history={selectedHistory}
              searchQuery={searchQuery}
              setSearchQuery={setSearchQuery}
              severityFilter={severityFilter}
              setSeverityFilter={setSeverityFilter}
              remediationFilter={remediationFilter}
              setRemediationFilter={setRemediationFilter}
              onOpenUpload={() => setUploadModalOpen(true)}
              onOpenDetails={setFindingDetailModal}
              onOpenUploadDetails={setUploadDetailModal}
              onRemediation={async (finding, workflowStatus, notes) => {
                try {
                  await api.updateRemediation(finding.customer.id, findingHash(finding), workflowStatus, notes);
                  await refreshFindings(customers);
                } catch (err) { fail(err); }
              }}
              onDownloadErrors={async (run) => {
                try {
                  const blob = await api.downloadErrorLog(run.id);
                  downloadBlob(blob, failedRowsDownloadName(run, blob));
                } catch (err) { fail(err); }
              }}
              onDownloadOriginal={async (run) => {
                try {
                  const blob = await api.downloadOriginalUpload(run.id);
                  downloadBlob(blob, originalUploadDownloadName(run));
                } catch (err) { fail(err); }
              }}
            />
          )}
          {!interactionOpen && section === 'software' && (
            <SoftwareManager
              key={`software-${sectionViewKey}`}
              canManage={canManageConfig}
              softwares={softwares}
              templates={templates}
              customers={allowedCustomers}
              onRefresh={refreshAll}
              onTemplate={(template = null, defaults = {}) => setTemplateModal(template ? { ...template, editMode: true } : { ...defaults })}
              announce={announce}
              fail={fail}
              confirmAction={confirmAction}
              onDirtyChange={(dirty) => setWorkspaceDirtyFlag('customerSoftwareAccess', dirty)}
            />
          )}
          {!interactionOpen && section === 'customers' && (
            <CustomerManagement
              key={`customers-${sectionViewKey}`}
              isAdmin={isAdmin}
              customers={allowedCustomers}
              softwares={softwares}
              templates={templates}
              onRefresh={refreshAll}
              onTemplate={(template = null, defaults = {}) => setTemplateModal(template ? { ...template, editMode: true } : { ...defaults })}
              announce={announce}
              fail={fail}
              confirmAction={confirmAction}
            />
          )}
          {!interactionOpen && section === 'users' && isAdmin && (
            <UserManagement
              key={`users-${sectionViewKey}`}
              users={usersList}
              customers={allowedCustomers}
              onRefresh={refreshAll}
              onEditAccess={setUserAccessModal}
              announce={announce}
              fail={fail}
              confirmAction={confirmAction}
            />
          )}
        </div>
      </main>

      {confirmDialog && <ConfirmDialog {...confirmDialog} onCancel={() => resolveConfirm(false)} onConfirm={() => resolveConfirm(true)} />}
    </div>
  );
}

function NavButton({ active, icon, label, onClick, collapsed = false, indent = false }) {
  return (
    <button
      title={collapsed ? label : undefined}
      onClick={onClick}
      className={`flex w-full items-center gap-3 rounded-lg px-4 py-3 text-left text-sm font-semibold transition ${collapsed ? 'justify-center px-2' : ''} ${
        active ? 'border-l-2 border-brand-blue bg-slate-900 text-brand-blue' : 'text-slate-400 hover:bg-slate-900 hover:text-white'
      } ${indent ? 'pl-8' : ''}`}
    >
      {icon}
      {!collapsed && <span>{label}</span>}
    </button>
  );
}

function SidebarUtilityButton({ title, icon, label, onClick, collapsed = false, fullWidth = false }) {
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      className={`flex min-h-10 items-center justify-center gap-2 rounded-lg border border-slate-800 bg-slate-900 px-3 py-2 text-xs font-bold text-slate-200 transition hover:border-brand-blue hover:bg-slate-800 hover:text-white ${fullWidth ? 'w-full' : ''} ${collapsed ? 'px-2' : ''}`}
    >
      {icon}
      {!collapsed && label && <span className="truncate">{label}</span>}
    </button>
  );
}

function AlertBox({ children, tone }) {
  const classes = tone === 'green'
    ? 'border-green-500/30 bg-green-500/10 text-green-300'
    : 'border-red-500/30 bg-red-500/10 text-red-300';
  return (
    <div className={`mb-4 flex items-start gap-2 rounded-lg border p-3 text-sm ${classes}`}>
      {tone === 'green' ? <CheckCircle className="mt-0.5 h-4 w-4" /> : <AlertCircle className="mt-0.5 h-4 w-4" />}
      <span>{children}</span>
    </div>
  );
}

function VulnerabilityManagement(props) {
  const criticals = props.findings.filter((f) => f.severity === 'CRITICAL').length;
  const highs = props.findings.filter((f) => f.severity === 'HIGH').length;
  const open = props.findings.filter((f) => {
    const rem = props.remediationsByCustomer[f.customer.id]?.[findingHash(f)];
    return !rem || rem.workflowStatus === 'OPEN';
  }).length;
  const severityRank = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
  const recentFindings = [...props.findings]
    .sort((a, b) => (severityRank[b.severity] || 0) - (severityRank[a.severity] || 0))
    .slice(0, 5);

  return (
    <div className="space-y-5">
      {props.mode === 'management' && (
        <div className="flex flex-wrap items-center justify-end gap-2">
          <Button variant="gradient" onClick={props.onOpenUpload}>
            <Upload className="h-4 w-4" /> Upload Scan File
          </Button>
        </div>
      )}

      {props.mode === 'dashboard' && (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <MetricCard label="Active Findings" value={props.findings.length} />
            <MetricCard label="Critical" value={criticals} tone="red" />
            <MetricCard label="High" value={highs} tone="amber" />
            <MetricCard label="Open Workflow" value={open} tone="green" />
          </div>
          <section className="rounded-xl border border-slate-800 bg-slate-900/70 p-5">
            <div className="mb-4 flex items-center justify-between gap-3">
              <h3 className="m-0 text-base font-bold">Highest Priority Findings</h3>
              <StatusPill tone="blue">{props.customers.length} customers scoped</StatusPill>
            </div>
            <FindingsTable findings={recentFindings} remediationsByCustomer={props.remediationsByCustomer} onOpenDetails={props.onOpenDetails} compact />
          </section>
        </>
      )}

      {props.mode === 'management' && (
        <>
          <div className="flex flex-col gap-3 rounded-xl border border-slate-800 bg-slate-900/70 p-4 lg:flex-row lg:items-center">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
              <TextInput value={props.searchQuery} onChange={(e) => props.setSearchQuery(e.target.value)} placeholder="Search title, CVE, device, or summary" className="pl-9" />
            </div>
            <SelectInput value={props.severityFilter} onChange={(e) => props.setSeverityFilter(e.target.value)} className="lg:w-48">
              <option value="ALL">All severities</option>
              {SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
            </SelectInput>
            <SelectInput value={props.remediationFilter} onChange={(e) => props.setRemediationFilter(e.target.value)} className="lg:w-56">
              <option value="ALL">All workflow statuses</option>
              {WORKFLOW_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
            </SelectInput>
          </div>
          <FindingsTable {...props} />
          <UploadHistoryTable history={props.history} onOpenRun={props.onOpenUploadDetails} onDownloadErrors={props.onDownloadErrors} onDownloadOriginal={props.onDownloadOriginal} />
        </>
      )}
    </div>
  );
}

function FindingsTable({ findings, remediationsByCustomer, onOpenDetails, compact = false }) {
  return (
    <div className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900/70">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[980px] text-left text-sm">
          <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
            <tr>
              <th className="px-4 py-3">Severity</th>
              <th className="px-4 py-3">Issue</th>
              <th className="px-4 py-3">Customer</th>
              <th className="px-4 py-3">CVSS</th>
              <th className="px-4 py-3">Devices</th>
              <th className="px-4 py-3">Workflow</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {findings.length === 0 && <tr><td colSpan="7" className="px-4 py-10 text-center text-slate-500">No active findings match the current filters.</td></tr>}
            {findings.map((finding) => {
              const hash = findingHash(finding);
              const rem = remediationsByCustomer[finding.customer.id]?.[hash];
              const status = rem?.workflowStatus || 'OPEN';
              return (
                <tr key={finding.id} className="cursor-pointer hover:bg-slate-950/50" onClick={() => onOpenDetails(finding)}>
                    <td className="px-4 py-3"><SeverityPill severity={finding.severity} /></td>
                    <td className="px-4 py-3">
                      <div className="max-w-md truncate font-semibold text-white">{finding.issueTitle}</div>
                      <div className="mt-1 flex gap-2 text-xs text-slate-500">
                        {finding.cveId && <span className="font-mono text-brand-blue">{finding.cveId}</span>}
                        {!compact && <span className="truncate">{finding.summary || 'No summary'}</span>}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-slate-300">{finding.customer.customerName}</td>
                    <td className="px-4 py-3 font-mono">{formatScore(finding.cvssScore)}</td>
                    <td className="px-4 py-3 text-slate-400">{finding.numberOfDevices || 0}</td>
                    <td className="px-4 py-3"><WorkflowPill status={status} /></td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-1">
                        <IconButton title="View details" onClick={(event) => {
                          event.stopPropagation();
                          onOpenDetails(finding);
                        }}>
                          <ChevronDown className="h-4 w-4 -rotate-90" />
                        </IconButton>
                      </div>
                    </td>
                  </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function UploadHistoryTable({ history, onOpenRun, onDownloadErrors, onDownloadOriginal }) {
  return (
    <div className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900/70">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[900px] text-left text-sm">
          <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
            <tr>
              <th className="px-4 py-3">Upload ID</th>
              <th className="px-4 py-3">Customer</th>
              <th className="px-4 py-3">Uploaded By</th>
              <th className="px-4 py-3">Timestamp</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Records</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {history.length === 0 && <tr><td colSpan="7" className="px-4 py-10 text-center text-slate-500">No upload history for the selected scope.</td></tr>}
            {history.map((run) => (
              <tr key={run.id} className="cursor-pointer hover:bg-slate-950/50" onClick={() => onOpenRun(run)}>
                <td className="px-4 py-3 font-mono text-xs text-slate-400">{run.id}</td>
                <td className="px-4 py-3">{run.customer?.customerName}</td>
                <td className="px-4 py-3 text-slate-400">{run.uploadedBy}</td>
                <td className="px-4 py-3 text-slate-400">{formatDate(run.uploadedAt)}</td>
                <td className="px-4 py-3"><UploadStatusPill status={run.status} /></td>
                <td className="px-4 py-3 text-slate-400">{run.totalRecords} total / {run.failedRecords} failed</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap justify-end gap-2">
                    <Button variant="tertiary" onClick={(event) => {
                      event.stopPropagation();
                      onDownloadOriginal(run);
                    }}>
                      <Download className="h-4 w-4" /> Uploaded File
                    </Button>
                    {run.failedRecords > 0 ? (
                    <Button variant="warn" onClick={(event) => {
                      event.stopPropagation();
                      onDownloadErrors(run);
                    }}>
                      <Download className="h-4 w-4" /> Download Failed Records
                    </Button>
                    ) : <span className="self-center text-xs text-slate-600">Clean</span>}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function FindingDetailModal({ finding, remediation, onClose, onRemediation }) {
  const [notes, setNotes] = useState(remediation?.notes || '');
  const status = remediation?.workflowStatus || 'OPEN';
  return (
    <InteractionPage title="Finding Details" subtitle={finding.issueTitle} icon={<Shield className="h-5 w-5 text-brand-blue" />} onBack={onClose}>
      <div className="grid gap-5 lg:grid-cols-[1fr_320px]">
        <section className="space-y-4">
          <div>
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <SeverityPill severity={finding.severity} />
              <StatusPill tone="blue">{finding.customer.customerName}</StatusPill>
              {finding.cveId && <StatusPill tone="slate">{finding.cveId}</StatusPill>}
            </div>
            <h3 className="m-0 text-xl font-bold text-white">{finding.issueTitle}</h3>
          </div>
          <div className="grid gap-3 md:grid-cols-3">
            <MetricCard label="CVSS" value={formatScore(finding.cvssScore)} tone={finding.severity === 'CRITICAL' ? 'red' : 'blue'} />
            <MetricCard label="Devices" value={finding.numberOfDevices || 0} tone="amber" />
            <MetricCard label="Last Detected" value={finding.lastDetectedAt ? formatDate(finding.lastDetectedAt) : 'N/A'} tone="green" />
          </div>
          <DetailBlock label="Summary" value={finding.summary} />
          <DetailBlock label="Insight" value={finding.vulnerabilityInsight} />
          <DetailBlock label="Impact" value={finding.impact} />
          <DetailBlock label="Solution" value={finding.solution} />
          <DetailBlock label="Affected Devices" value={finding.affectedDevices} />
          <DetailBlock label="Detection Result" value={finding.vulnerabilityDetectionResult} />
          <DetailBlock label="References" value={finding.referencesInfo} />
        </section>
        <section className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
          <div className="mb-3 text-xs font-bold uppercase tracking-wider text-brand-blue">Remediation Workflow</div>
          <div className="grid grid-cols-2 gap-2">
            {WORKFLOW_STATUSES.map((workflowStatus) => (
              <Button key={workflowStatus} variant={status === workflowStatus ? 'primary' : 'ghost'} onClick={() => onRemediation(finding, workflowStatus, notes)}>
                {workflowStatus.replace('_', ' ')}
              </Button>
            ))}
          </div>
          <Field label="Operational Notes">
            <TextArea rows={7} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Add workflow notes" className="mt-3" />
          </Field>
          <Button variant="gradient" className="mt-3 w-full" onClick={() => onRemediation(finding, status, notes)}>Save Notes</Button>
        </section>
      </div>
    </InteractionPage>
  );
}

function UploadDetailModal({ run, onClose, onDownloadErrors, onDownloadOriginal }) {
  return (
    <InteractionPage title="Upload Run Details" subtitle={run.fileName || run.id} icon={<Upload className="h-5 w-5 text-brand-blue" />} onBack={onClose}>
      <div className="grid gap-4 md:grid-cols-3">
        <MetricCard label="Total Records" value={run.totalRecords || 0} />
        <MetricCard label="Failed Records" value={run.failedRecords || 0} tone={run.failedRecords > 0 ? 'red' : 'green'} />
        <MetricCard label="Status" value={(run.status || 'PROCESSING').replace('_', ' ')} tone={run.status === 'FAILED' ? 'red' : 'blue'} />
      </div>
      <div className="mt-5 grid gap-4 md:grid-cols-2">
        <DetailBlock label="File" value={run.fileName} />
        <DetailBlock label="Customer" value={run.customer?.customerName} />
        <DetailBlock label="Template" value={run.template?.name} />
        <DetailBlock label="Uploaded By" value={run.uploadedBy} />
        <DetailBlock label="Uploaded At" value={formatDate(run.uploadedAt)} />
        <DetailBlock label="Active Snapshot" value={run.activeSnapshot ? 'Yes' : 'No'} />
      </div>
      <div className="mt-5">
        <DetailBlock label="Error Summary" value={run.errorSummary} />
      </div>
      <div className="mt-5 flex flex-wrap gap-2">
        <Button variant="tertiary" onClick={() => onDownloadOriginal(run)}>
          <Download className="h-4 w-4" /> Download Uploaded File
        </Button>
        {run.failedRecords > 0 && (
          <Button variant="warn" onClick={() => onDownloadErrors(run)}>
            <Download className="h-4 w-4" /> Download Failed Records
          </Button>
        )}
      </div>
    </InteractionPage>
  );
}

function SoftwareManager({ canManage, softwares, templates, customers, onRefresh, onTemplate, announce, fail, confirmAction }) {
  const [query, setQuery] = useState('');
  const [lifecycleTab, setLifecycleTab] = useState('ACTIVE');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [detail, setDetail] = useState(null);
  const [addVendorOpen, setAddVendorOpen] = useState(false);
  const counts = getLifecycleCounts(softwares);
  const visibleSoftware = softwares.filter((software) => {
    const templateCount = templates.filter((template) => template.software?.id === software.id).length;
    const haystack = `${software.softwareName} ${templateCount}`.toLowerCase();
    return (!query || haystack.includes(query.toLowerCase()))
      && matchesLifecycle(software, lifecycleTab)
      && (lifecycleTab === 'ARCHIVE' || matchesStatus(software, statusFilter));
  });

  if (detail) {
    return (
      <SoftwareDetailModal
        software={detail}
        templates={templates.filter((template) => template.software?.id === detail.id)}
        customers={customers}
        canManage={canManage}
        onClose={() => setDetail(null)}
        onRefresh={onRefresh}
        onTemplate={onTemplate}
        announce={announce}
        fail={fail}
        confirmAction={confirmAction}
      />
    );
  }

  return (
    <div className="space-y-5">
      <section className="rounded-xl border border-slate-800 bg-slate-900/70 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="m-0 text-base font-bold">Security Software Vendors</h3>
            <p className="m-0 text-xs text-slate-500">Search, filter, and open a vendor to manage its templates.</p>
          </div>
          <StatusPill tone={canManage ? 'blue' : 'slate'}>{canManage ? 'Manage' : 'View'}</StatusPill>
        </div>
        {canManage && (
          <div className="mt-4">
            <Button type="button" variant="gradient" onClick={() => setAddVendorOpen(true)}>
              <Plus className="h-4 w-4" /> Add Vendor
            </Button>
          </div>
        )}
      </section>

      <section className="rounded-xl border border-slate-800 bg-slate-900/70 p-5">
        <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center">
          <LifecycleTabs value={lifecycleTab} onChange={setLifecycleTab} counts={counts} />
          {lifecycleTab === 'ACTIVE' && <StatusFilter value={statusFilter} onChange={setStatusFilter} counts={counts} />}
          <div className="relative flex-1">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
            <TextInput value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search vendors" className="pl-9" />
          </div>
        </div>
        <div className="overflow-hidden rounded-xl border border-slate-800">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
              <tr>
                <th className="px-4 py-3">Vendor</th>
                <th className="px-4 py-3">Assigned Software</th>
                <th className="px-4 py-3">Active Templates</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {visibleSoftware.length === 0 && <tr><td colSpan="5" className="px-4 py-10 text-center text-slate-500">No software vendors match the filters.</td></tr>}
              {visibleSoftware.map((software) => {
                const vendorTemplates = templates.filter((template) => template.software?.id === software.id);
                const activeTemplates = vendorTemplates.filter((template) => template.enabled);
                return (
                  <tr key={software.id} className="cursor-pointer hover:bg-slate-950/50" onClick={() => setDetail(software)}>
                    <td className="px-4 py-3 font-semibold">{software.softwareName}</td>
                    <td className="px-4 py-3 text-slate-400">{vendorTemplates.length}</td>
                    <td className="px-4 py-3 text-slate-400">{activeTemplates.length}</td>
                    <td className="px-4 py-3">
                      {software.archived ? (
                        <div className="text-xs text-slate-500">Archived {formatDate(software.archivedAt)}</div>
                      ) : (
                        <StatusToggle
                          enabled={software.enabled}
                          disabled={!canManage}
                          onToggle={(event) => toggleSoftware(event, software, onRefresh, announce, fail, confirmAction)}
                        />
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap justify-end gap-2">
                        <ActionButton icon={<Eye className="h-4 w-4" />} label="Open" title="Open vendor details" onClick={(event) => {
                          event.stopPropagation();
                          setDetail(software);
                        }} />
                        {canManage && software.archived && (
                          <ActionButton
                            icon={<RefreshCw className="h-4 w-4" />}
                            label="Restore"
                            title="Restore vendor"
                            onClick={async (event) => {
                              event.stopPropagation();
                              const confirmed = await confirmRestore(confirmAction, software.softwareName);
                              if (!confirmed) return;
                              try {
                                await api.updateSoftware(software.id, { archived: false });
                                await onRefresh();
                                announce('Software restored as disabled.');
                              } catch (err) { fail(err); }
                            }}
                          />
                        )}
                        {canManage && !software.archived && (
                          <ActionButton
                            icon={<Trash2 className="h-4 w-4" />}
                            label="Archive"
                            title="Archive vendor"
                            variant="danger"
                            onClick={(event) => deleteSoftware(event, software, onRefresh, announce, fail, confirmAction)}
                          />
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
      {addVendorOpen && (
        <AddVendorDialog
          onClose={() => setAddVendorOpen(false)}
          onSubmit={async (softwareName) => {
            try {
              await api.createSoftware(softwareName);
              setAddVendorOpen(false);
              await onRefresh();
              announce('Software registered.');
            } catch (err) { fail(err); }
          }}
        />
      )}
    </div>
  );
}

function AddVendorDialog({ onClose, onSubmit }) {
  const [softwareName, setSoftwareName] = useState('');
  const trimmedName = softwareName.trim();
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur-sm">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (trimmedName) onSubmit(trimmedName);
        }}
        className="w-full max-w-lg rounded-xl border border-slate-800 bg-slate-900 p-5 shadow-2xl"
      >
        <div className="mb-5 flex items-start justify-between gap-3">
          <div className="flex items-start gap-3">
            <Layers className="mt-0.5 h-5 w-5 text-brand-blue" />
            <div>
              <h3 className="m-0 text-base font-bold text-white">Add Security Software Vendor</h3>
              <p className="m-0 mt-2 text-sm leading-6 text-slate-400">Create a vendor record first, then add templates from the vendor detail page.</p>
            </div>
          </div>
          <button type="button" onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white">
            <X className="h-4 w-4" />
          </button>
        </div>
        <Field label="Vendor Product Name">
          <TextInput autoFocus value={softwareName} onChange={(e) => setSoftwareName(e.target.value)} placeholder="Example: Nessus, Rapidfire, Kaseya" />
        </Field>
        <div className="mt-5 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="gradient" disabled={!trimmedName}>
            <Plus className="h-4 w-4" /> Add Vendor
          </Button>
        </div>
      </form>
    </div>
  );
}

function SoftwareDetailModal({ software, templates, customers, canManage, onClose, onRefresh, onTemplate, announce, fail, confirmAction }) {
  const visibleTemplates = templates.filter((template) => !template.archived);
  const activeTemplates = visibleTemplates.filter((template) => template.enabled);
  return (
    <InteractionPage title="Security Software Details" subtitle={software.softwareName} icon={<Layers className="h-5 w-5 text-brand-blue" />} onBack={onClose}>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="mb-2"><StatusPill tone={software.archived ? 'amber' : software.enabled ? 'green' : 'slate'}>{software.archived ? 'Archived' : software.enabled ? 'Enabled' : 'Disabled'}</StatusPill></div>
          <h3 className="m-0 text-xl font-bold">{software.softwareName}</h3>
          <p className="m-0 mt-1 text-sm text-slate-500">Vendor template library and activation controls.</p>
        </div>
        <div className="flex gap-2">
          {canManage && (
            <>
              {software.archived ? (
                <Button variant="secondary" onClick={async () => {
                  const confirmed = await confirmRestore(confirmAction, software.softwareName);
                  if (!confirmed) return;
                  try {
                    await api.updateSoftware(software.id, { archived: false });
                    await onRefresh();
                    onClose();
                    announce('Software restored as disabled.');
                  } catch (err) { fail(err); }
                }}><RefreshCw className="h-4 w-4" /> Restore</Button>
              ) : (
                <>
                  <StatusToggle enabled={software.enabled} onToggle={(event) => toggleSoftware(event, software, onRefresh, announce, fail, confirmAction)} />
                  <Button variant="danger" onClick={(event) => deleteSoftware(event, software, onRefresh, announce, fail, confirmAction)}><Trash2 className="h-4 w-4" /> Archive</Button>
                </>
              )}
            </>
          )}
        </div>
      </div>
      <div className="mb-5 grid gap-3 md:grid-cols-3">
        <MetricCard label="Templates" value={visibleTemplates.length} />
        <MetricCard label="Enabled" value={activeTemplates.length} tone="green" />
        <MetricCard label="Disabled" value={visibleTemplates.length - activeTemplates.length} tone="amber" />
      </div>
      <div className="mb-4 flex items-center justify-between gap-3">
        <h4 className="m-0 text-sm font-bold">Templates</h4>
        {canManage && (
          <Button variant="gradient" onClick={() => onTemplate(null, { softwareId: software.id, customerId: '', hasHeaderRow: true, fileFormat: 'CSV' })}>
            <Plus className="h-4 w-4" /> Add Template
          </Button>
        )}
      </div>
      <TemplateTable templates={templates} customers={customers} onTemplate={onTemplate} canManage={canManage} onRefresh={onRefresh} announce={announce} fail={fail} confirmAction={confirmAction} />
    </InteractionPage>
  );
}

function CustomerManagement({ isAdmin, customers, softwares, templates, onRefresh, onTemplate, announce, fail, confirmAction, onDirtyChange }) {
  const [addCustomerOpen, setAddCustomerOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [lifecycleTab, setLifecycleTab] = useState('ACTIVE');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [detail, setDetail] = useState(null);
  const isCustomerEnabled = (customer) => customer.enabled !== false;
  const counts = getLifecycleCounts(customers);
  const visibleCustomers = customers.filter((customer) => (
    customer.customerName.toLowerCase().includes(query.toLowerCase())
    && matchesLifecycle(customer, lifecycleTab)
    && (lifecycleTab === 'ARCHIVE' || matchesStatus(customer, statusFilter))
  ));

  if (detail) {
    return (
      <CustomerDetailModal
        customer={detail}
        isAdmin={isAdmin}
        softwares={softwares}
        templates={templates}
        onClose={() => setDetail(null)}
        onRefresh={onRefresh}
        onTemplate={onTemplate}
        announce={announce}
        fail={fail}
        confirmAction={confirmAction}
        onDirtyChange={onDirtyChange}
      />
    );
  }

  return (
    <div className="space-y-5">
      <section className="rounded-xl border border-slate-800 bg-slate-900/70 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="m-0 text-base font-bold">Customer Directory</h3>
            <p className="m-0 text-xs text-slate-500">Open a customer to manage its software template assignments.</p>
          </div>
          {isAdmin && (
            <Button type="button" variant="gradient" onClick={() => setAddCustomerOpen(true)}>
              <Plus className="h-4 w-4" /> Add Customer
            </Button>
          )}
        </div>
      </section>

      <section className="rounded-xl border border-slate-800 bg-slate-900/70 p-5">
        <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center">
          <LifecycleTabs value={lifecycleTab} onChange={setLifecycleTab} counts={counts} />
          {lifecycleTab === 'ACTIVE' && <StatusFilter value={statusFilter} onChange={setStatusFilter} counts={counts} />}
          <div className="relative flex-1">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
            <TextInput value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search customers" className="pl-9" />
          </div>
        </div>
        <div className="overflow-hidden rounded-xl border border-slate-800">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
              <tr>
                <th className="px-4 py-3">Customer</th>
                <th className="px-4 py-3">Created</th>
                <th className="px-4 py-3">Templates</th>
                <th className="px-4 py-3">Enabled Templates</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {visibleCustomers.length === 0 && <tr><td colSpan="6" className="px-4 py-10 text-center text-slate-500">No customers match the filters.</td></tr>}
              {visibleCustomers.map((customer) => {
                const enabled = isCustomerEnabled(customer);
                const assignedSoftwareCount = customer.assignedSoftwareCount ?? 0;
                const enabledAssignedSoftwareCount = customer.enabledAssignedSoftwareCount ?? 0;
                const activeTemplateCount = customer.activeTemplateCount ?? templates.filter((template) => (
                  template.enabled
                  && !template.archived
                  && (!template.customer || template.customer?.id === customer.id)
                )).length;
                return (
                  <tr key={customer.id} className="cursor-pointer hover:bg-slate-950/50" onClick={() => setDetail(customer)}>
                    <td className="px-4 py-3 font-semibold">{customer.customerName}</td>
                    <td className="px-4 py-3 text-slate-400">{formatDate(customer.createdAt)}</td>
                    <td className="px-4 py-3 text-slate-400">{enabledAssignedSoftwareCount} enabled / {assignedSoftwareCount} assigned</td>
                    <td className="px-4 py-3 text-slate-400">{activeTemplateCount}</td>
                    <td className="px-4 py-3">
                      {customer.archived ? (
                        <div className="text-xs text-slate-500">Archived {formatDate(customer.archivedAt)}</div>
                      ) : (
                        <StatusToggle
                          enabled={enabled}
                          disabled={!isAdmin}
                          onToggle={async (event) => {
                            event.stopPropagation();
                            const confirmed = await confirmStatusChange(confirmAction, customer.customerName, enabled);
                            if (!confirmed) return;
                            try {
                              await api.updateCustomer(customer.id, { enabled: !enabled });
                              await onRefresh();
                              announce('Customer status updated.');
                            } catch (err) { fail(err); }
                          }}
                        />
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap justify-end gap-2">
                        <ActionButton icon={<Eye className="h-4 w-4" />} label="Open" title="Open customer details" onClick={(event) => {
                          event.stopPropagation();
                          setDetail(customer);
                        }} />
                        {isAdmin && customer.archived && (
                          <ActionButton
                            icon={<RefreshCw className="h-4 w-4" />}
                            label="Restore"
                            title="Restore customer"
                            onClick={async (event) => {
                              event.stopPropagation();
                              const confirmed = await confirmRestore(confirmAction, customer.customerName);
                              if (!confirmed) return;
                              try {
                                await api.updateCustomer(customer.id, { archived: false });
                                await onRefresh();
                                announce('Customer restored as disabled.');
                              } catch (err) { fail(err); }
                            }}
                          />
                        )}
                        {isAdmin && !customer.archived && (
                          <ActionButton
                            icon={<Trash2 className="h-4 w-4" />}
                            label="Archive"
                            title="Archive customer"
                            variant="danger"
                            onClick={async (event) => {
                              event.stopPropagation();
                              const confirmed = await confirmArchive(confirmAction, customer.customerName);
                              if (!confirmed) return;
                              try {
                                await api.deleteCustomer(customer.id);
                                await onRefresh();
                                announce('Customer archived.');
                              } catch (err) { fail(err); }
                            }}
                          />
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
      {addCustomerOpen && (
        <AddCustomerDialog
          onClose={() => setAddCustomerOpen(false)}
          onSubmit={async (customerName) => {
            try {
              await api.createCustomer(customerName);
              setAddCustomerOpen(false);
              await onRefresh();
              announce('Customer onboarded.');
            } catch (err) { fail(err); }
          }}
        />
      )}
    </div>
  );
}

function AddCustomerDialog({ onClose, onSubmit }) {
  const [customerName, setCustomerName] = useState('');
  const trimmedName = customerName.trim();
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur-sm">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (trimmedName) onSubmit(trimmedName);
        }}
        className="w-full max-w-lg rounded-xl border border-slate-800 bg-slate-900 p-5 shadow-2xl"
      >
        <div className="mb-5 flex items-start justify-between gap-3">
          <div className="flex items-start gap-3">
            <Shield className="mt-0.5 h-5 w-5 text-brand-blue" />
            <div>
              <h3 className="m-0 text-base font-bold text-white">Add Customer</h3>
              <p className="m-0 mt-2 text-sm leading-6 text-slate-400">Create the customer record, then manage software templates from the customer detail page.</p>
            </div>
          </div>
          <button type="button" onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white">
            <X className="h-4 w-4" />
          </button>
        </div>
        <Field label="Customer Name">
          <TextInput autoFocus value={customerName} onChange={(e) => setCustomerName(e.target.value)} placeholder="Example: Acme Bank" />
        </Field>
        <div className="mt-5 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="gradient" disabled={!trimmedName}>
            <Plus className="h-4 w-4" /> Add Customer
          </Button>
        </div>
      </form>
    </div>
  );
}

function CustomerDetailModal({ customer, isAdmin, softwares, templates, onClose, onRefresh, onTemplate, announce, fail, confirmAction, onDirtyChange }) {
  const [editingName, setEditingName] = useState(customer.customerName);
  const [savedCustomerName, setSavedCustomerName] = useState(customer.customerName);
  const [softwareAccessRows, setSoftwareAccessRows] = useState([]);
  const [savedAccessKey, setSavedAccessKey] = useState('');
  const [accessQuery, setAccessQuery] = useState('');
  const [accessLoading, setAccessLoading] = useState(true);
  const [accessSaving, setAccessSaving] = useState(false);
  const [accessError, setAccessError] = useState('');
  const activeSoftwares = softwares.filter((software) => !software.archived);
  const assignedSoftwareIds = new Set(softwareAccessRows.filter((row) => row.assigned).map((row) => row.software.id));
  const assignedSoftwareRows = softwareAccessRows.filter((row) => row.assigned);
  const enabledAssignedRows = softwareAccessRows.filter((row) => row.assigned && row.enabled);
  const assignedWithoutTemplates = softwareAccessRows.filter((row) => row.assigned && row.activeTemplateCount === 0);
  const filteredAccessRows = softwareAccessRows.filter((row) => (
    !accessQuery.trim() || row.software.softwareName.toLowerCase().includes(accessQuery.trim().toLowerCase())
  ));
  const customerTemplates = templates.filter((template) => (
    !template.archived
    && assignedSoftwareIds.has(template.software?.id)
    && (!template.customer || template.customer?.id === customer.id)
  ));
  const activeTemplates = customerTemplates.filter((template) => template.enabled);
  const customerEnabled = customer.enabled !== false;
  const canCreateCustomerTemplate = !customer.archived && enabledAssignedRows.length > 0;
  const currentAccessKey = serializeSoftwareAssignments(softwareAccessRows);
  const customerDetailDirty = editingName.trim() !== savedCustomerName || currentAccessKey !== savedAccessKey;

  useEffect(() => {
    let active = true;
    api.getCustomerSoftwareAccess(customer.id)
      .then((result) => {
        if (active) {
          const assignments = result.assignments || [];
          setSoftwareAccessRows(assignments);
          setSavedAccessKey(serializeSoftwareAssignments(assignments));
          setAccessError('');
        }
      })
      .catch((err) => {
        if (active) setAccessError(err.message || 'Could not load software assignments.');
      })
      .finally(() => {
        if (active) setAccessLoading(false);
      });
    return () => {
      active = false;
    };
  }, [customer.id]);

  useEffect(() => {
    onDirtyChange?.(customerDetailDirty);
  }, [customerDetailDirty, onDirtyChange]);

  const requestClose = async () => {
    if (customerDetailDirty) {
      const confirmed = await confirmAction({
        title: 'Leave This Screen?',
        message: 'Unsaved customer changes will be lost.',
        details: ['Customer name or software access changes have not been saved.'],
        confirmLabel: 'Leave Screen',
        variant: 'danger',
      });
      if (!confirmed) return;
    }
    onDirtyChange?.(false);
    onClose();
  };

  const updateAccessRow = (softwareId, patch) => {
    setSoftwareAccessRows((rows) => rows.map((row) => (
      row.software.id === softwareId ? { ...row, ...patch } : row
    )));
  };

  const saveSoftwareAssignments = async () => {
    setAccessSaving(true);
    setAccessError('');
    try {
      const assignments = softwareAccessRows
        .filter((row) => row.assigned)
        .map((row) => ({ softwareId: row.software.id, enabled: row.enabled }));
      const result = await api.updateCustomerSoftwareAccess(customer.id, assignments);
      const nextAssignments = result.assignments || [];
      setSoftwareAccessRows(nextAssignments);
      setSavedAccessKey(serializeSoftwareAssignments(nextAssignments));
      await onRefresh();
      announce('Software assignments updated.');
    } catch (err) {
      setAccessError(err.message || 'Could not update software assignments. No changes were saved.');
    } finally {
      setAccessSaving(false);
    }
  };

  const removeSoftwareAssignment = async (row) => {
    const templateCount = templates.filter((template) => template.customer?.id === customer.id && template.software?.id === row.software.id).length;
    const confirmed = await confirmAction({
      title: 'Remove Software Assignment',
      message: `Remove "${row.software.softwareName}" from "${customer.customerName}"? Existing templates remain saved but cannot be used until the software is assigned again.`,
      details: templateCount ? [`${templateCount} customer templates use this software.`] : [],
      confirmLabel: 'Remove',
      variant: 'danger',
    });
    if (confirmed) updateAccessRow(row.software.id, { assigned: false, enabled: false });
  };
  return (
    <InteractionPage title="Customer Details" subtitle={customer.customerName} icon={<Shield className="h-5 w-5 text-brand-blue" />} onBack={requestClose}>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="m-0 text-xl font-bold">{customer.customerName}</h3>
          <p className="m-0 mt-1 text-sm text-slate-500">Created {formatDate(customer.createdAt)}</p>
        </div>
        {isAdmin && (
          <div className="flex gap-2">
            {customer.archived ? (
              <Button variant="secondary" onClick={async () => {
                const confirmed = await confirmRestore(confirmAction, customer.customerName);
                if (!confirmed) return;
                try {
                  await api.updateCustomer(customer.id, { archived: false });
                  await onRefresh();
                  onClose();
                  announce('Customer restored as disabled.');
                } catch (err) { fail(err); }
              }}><RefreshCw className="h-4 w-4" /> Restore</Button>
            ) : (
              <>
                <StatusToggle enabled={customerEnabled} onToggle={async () => {
                  const confirmed = await confirmStatusChange(confirmAction, customer.customerName, customerEnabled);
                  if (!confirmed) return;
                  try {
                    await api.updateCustomer(customer.id, { enabled: !customerEnabled });
                    await onRefresh();
                    announce('Customer status updated.');
                  } catch (err) { fail(err); }
                }} />
                <Button variant="danger" onClick={async () => {
                  const confirmed = await confirmArchive(confirmAction, customer.customerName);
                  if (!confirmed) return;
                  try {
                    await api.deleteCustomer(customer.id);
                    await onRefresh();
                    onClose();
                    announce('Customer archived.');
                  } catch (err) { fail(err); }
                }}><Trash2 className="h-4 w-4" /> Archive</Button>
              </>
            )}
          </div>
        )}
      </div>
      <div className="mb-5 grid gap-3 md:grid-cols-3">
        <MetricCard label="Templates" value={customerTemplates.length} />
        <MetricCard label="Enabled" value={activeTemplates.length} tone="green" />
        <MetricCard label="Assigned Software" value={assignedSoftwareRows.length} tone="amber" />
      </div>
      {isAdmin && (
        <div className="mb-5 rounded-xl border border-slate-800 bg-slate-950/70 p-4">
          <Field label="Customer Name">
            <div className="flex gap-2">
              <TextInput value={editingName} onChange={(e) => setEditingName(e.target.value)} />
              <Button onClick={async () => {
                try {
                  await api.updateCustomer(customer.id, editingName);
                  setSavedCustomerName(editingName.trim());
                  await onRefresh();
                  announce('Customer updated.');
                } catch (err) { fail(err); }
              }}>Save</Button>
            </div>
          </Field>
        </div>
      )}
      <div className="mb-4 flex items-center justify-between gap-3">
        <div>
          <h4 className="m-0 text-sm font-bold">Customer Software Access</h4>
          <p className="m-0 mt-1 text-xs text-slate-500">Assign only the software this customer is allowed to use.</p>
        </div>
        <Button variant="gradient" disabled={!canCreateCustomerTemplate} onClick={() => onTemplate(null, { customerId: customer.id, softwareId: enabledAssignedRows[0]?.software.id || '', hasHeaderRow: true, fileFormat: 'CSV' })}>
          <Plus className="h-4 w-4" /> Add Customer Template
        </Button>
      </div>
      {accessLoading && <AlertBox tone="blue">Loading software assignments.</AlertBox>}
      {accessError && <AlertBox tone="red">{accessError}</AlertBox>}
      {!accessLoading && softwareAccessRows.length === 0 && <AlertBox tone="amber">No active software vendors are available. Add active software before assigning it to customers.</AlertBox>}
      {!accessLoading && assignedSoftwareRows.length === 0 && <AlertBox tone="amber">No software assigned to this customer. Assign software before creating templates or uploading scan files.</AlertBox>}
      {!accessLoading && assignedSoftwareRows.length > 0 && enabledAssignedRows.length === 0 && <AlertBox tone="amber">All assigned software is disabled. Enable at least one software assignment before creating templates or uploading files.</AlertBox>}
      {!accessLoading && assignedWithoutTemplates.length > 0 && <AlertBox tone="amber">{assignedWithoutTemplates.length} assigned software vendor{assignedWithoutTemplates.length === 1 ? '' : 's'} have no active templates.</AlertBox>}
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <section className="rounded-xl border border-slate-800 bg-slate-900/70 p-4">
          <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <h3 className="m-0 text-sm font-bold">Software List</h3>
              <p className="m-0 mt-1 text-xs text-slate-500">{assignedSoftwareRows.length} of {activeSoftwares.length} software vendors assigned</p>
            </div>
            <div className="relative md:w-72">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
              <TextInput value={accessQuery} onChange={(e) => setAccessQuery(e.target.value)} placeholder="Search software" className="pl-9" />
            </div>
          </div>
          <div className="overflow-hidden rounded-xl border border-slate-800">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3">Software</th>
                  <th className="px-4 py-3">Access</th>
                  <th className="px-4 py-3">Assignment Status</th>
                  <th className="px-4 py-3">Templates</th>
                  <th className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {filteredAccessRows.length === 0 && <tr><td colSpan="5" className="px-4 py-10 text-center text-slate-500">No software matches the search.</td></tr>}
                {filteredAccessRows.map((row) => (
                  <tr key={row.software.id} className="hover:bg-slate-950/50">
                    <td className="px-4 py-3 font-semibold">{row.software.softwareName}</td>
                    <td className="px-4 py-3"><StatusPill tone={row.assigned ? 'green' : 'slate'}>{row.assigned ? 'Assigned' : 'Not Assigned'}</StatusPill></td>
                    <td className="px-4 py-3">
                      {row.assigned ? (
                        <StatusToggle enabled={row.enabled} onToggle={(event) => {
                          event.stopPropagation();
                          updateAccessRow(row.software.id, { enabled: !row.enabled });
                        }} />
                      ) : (
                        <span className="text-xs text-slate-500">Assign first</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-slate-400">{row.activeTemplateCount || 0} active</td>
                    <td className="px-4 py-3 text-right">
                      <Button variant={row.assigned ? 'danger' : 'ghost'} disabled={customer.archived} onClick={() => {
                        if (row.assigned) removeSoftwareAssignment(row);
                        else updateAccessRow(row.software.id, { assigned: true, enabled: true });
                      }}>
                        {row.assigned ? 'Remove' : 'Add'}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
        <aside className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
          <h3 className="m-0 text-sm font-bold">Assigned Software</h3>
          <p className="m-0 mt-1 text-xs text-slate-500">Enabled: {enabledAssignedRows.length} of {assignedSoftwareRows.length}</p>
          <div className="mt-4 max-h-80 space-y-2 overflow-y-auto pr-1">
            {assignedSoftwareRows.length === 0 && <div className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm text-slate-500">No software assigned.</div>}
            {assignedSoftwareRows.map((row) => (
              <div key={row.software.id} className="flex items-center justify-between gap-2 rounded-lg border border-slate-800 bg-slate-900 px-3 py-2">
                <div className="min-w-0">
                  <div className="truncate text-sm font-semibold">{row.software.softwareName}</div>
                  <div className="text-xs text-slate-500">{row.activeTemplateCount || 0} active templates</div>
                </div>
                <Button variant="ghost" disabled={customer.archived} onClick={() => removeSoftwareAssignment(row)}>Remove</Button>
              </div>
            ))}
          </div>
          <Button variant="gradient" className="mt-4 w-full" disabled={customer.archived || accessSaving} onClick={saveSoftwareAssignments}>
            {accessSaving ? 'Saving...' : 'Save Software Access'}
          </Button>
        </aside>
      </div>
    </InteractionPage>
  );
}

function TemplateTable({ templates, onTemplate, canManage, onRefresh, announce, fail, confirmAction }) {
  const [lifecycleTab, setLifecycleTab] = useState('ACTIVE');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const counts = getLifecycleCounts(templates);
  const visibleTemplates = templates.filter((template) => (
    matchesLifecycle(template, lifecycleTab)
    && (lifecycleTab === 'ARCHIVE' || matchesStatus(template, statusFilter))
  ));
  return (
    <div className="space-y-3">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
        <LifecycleTabs value={lifecycleTab} onChange={setLifecycleTab} counts={counts} />
        {lifecycleTab === 'ACTIVE' && <StatusFilter value={statusFilter} onChange={setStatusFilter} counts={counts} />}
      </div>
      <div className="overflow-hidden rounded-xl border border-slate-800">
      <table className="w-full text-left text-sm">
        <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
          <tr>
            <th className="px-4 py-3">Template</th>
            <th className="px-4 py-3">Software</th>
            <th className="px-4 py-3">Scope</th>
            <th className="px-4 py-3">Format</th>
            <th className="px-4 py-3">Status</th>
            <th className="px-4 py-3 text-right">Actions</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800">
          {visibleTemplates.length === 0 && <tr><td colSpan="6" className="px-4 py-10 text-center text-slate-500">No templates match this status.</td></tr>}
          {visibleTemplates.map((template) => (
            <tr key={template.id} className="hover:bg-slate-950/50">
              <td className="px-4 py-3 font-semibold">{template.name}</td>
              <td className="px-4 py-3 text-slate-400">{template.software?.softwareName}</td>
              <td className="px-4 py-3">{template.customer ? template.customer.customerName : 'Global standard'}</td>
              <td className="px-4 py-3 text-slate-400">{template.fileFormat || 'CSV'}</td>
              <td className="px-4 py-3">
                {template.archived ? (
                  <div className="text-xs text-slate-500">Archived {formatDate(template.archivedAt)}</div>
                ) : (
                  <StatusToggle
                    enabled={template.enabled}
                    disabled={!canManage}
                    onToggle={async (event) => {
                      event.stopPropagation();
                      const confirmed = await confirmStatusChange(confirmAction, template.name, template.enabled);
                      if (!confirmed) return;
                      try {
                        await api.updateTemplate(template.id, { enabled: !template.enabled });
                        await onRefresh();
                        announce('Template status updated.');
                      } catch (err) { fail(err); }
                    }}
                  />
                )}
              </td>
              <td className="px-4 py-3">
                <div className="flex flex-wrap justify-end gap-2">
                  {!template.archived && <ActionButton icon={<Eye className="h-4 w-4" />} label="Open Mapper" title="Open template mapper" onClick={() => onTemplate(template)} />}
                  {canManage && template.archived && (
                    <ActionButton
                      icon={<RefreshCw className="h-4 w-4" />}
                      label="Restore"
                      title="Restore template"
                      onClick={async () => {
                        const confirmed = await confirmRestore(confirmAction, template.name);
                        if (!confirmed) return;
                        try {
                          await api.updateTemplate(template.id, { archived: false });
                          await onRefresh();
                          announce('Template restored as disabled.');
                        } catch (err) { fail(err); }
                      }}
                    />
                  )}
                  {canManage && !template.archived && (
                      <ActionButton
                        icon={<Trash2 className="h-4 w-4" />}
                        label="Archive"
                        title="Archive template"
                        variant="danger"
                        onClick={async () => {
                        const confirmed = await confirmArchive(confirmAction, template.name);
                        if (!confirmed) return;
                        try {
                          await api.deleteTemplate(template.id);
                          await onRefresh();
                          announce('Template archived.');
                        } catch (err) { fail(err); }
                      }}
                      />
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </div>
  );
}

function UserManagement({ users, customers, onRefresh, onEditAccess, announce, fail, confirmAction }) {
  const [addUserOpen, setAddUserOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [lifecycleTab, setLifecycleTab] = useState('ACTIVE');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [detail, setDetail] = useState(null);
  const counts = getLifecycleCounts(users);
  const visibleUsers = users.filter((u) => {
    const text = `${u.fullName} ${u.username} ${u.role} ${(u.allowedCustomers || []).map((c) => c.customerName).join(' ')}`.toLowerCase();
    return (!query || text.includes(query.toLowerCase()))
      && matchesLifecycle(u, lifecycleTab)
      && (lifecycleTab === 'ARCHIVE' || matchesStatus(u, statusFilter));
  });

  if (detail) {
    return (
      <UserDetailModal
        user={detail}
        onClose={() => setDetail(null)}
        onEditAccess={onEditAccess}
        onRefresh={onRefresh}
        announce={announce}
        fail={fail}
        confirmAction={confirmAction}
      />
    );
  }

  return (
    <div className="space-y-5">
      <section className="rounded-xl border border-slate-800 bg-slate-900/70 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="m-0 text-base font-bold">User Directory</h3>
            <p className="m-0 text-xs text-slate-500">Create operators and manage customer access.</p>
          </div>
          <Button type="button" variant="gradient" onClick={() => setAddUserOpen(true)}>
            <UserPlus className="h-4 w-4" /> Add User
          </Button>
        </div>
      </section>
      <section className="rounded-xl border border-slate-800 bg-slate-900/70 p-5">
        <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center">
          <h3 className="m-0 text-base font-bold lg:mr-auto">User Directory</h3>
          <LifecycleTabs value={lifecycleTab} onChange={setLifecycleTab} counts={counts} />
          {lifecycleTab === 'ACTIVE' && <StatusFilter value={statusFilter} onChange={setStatusFilter} counts={counts} />}
          <div className="relative flex-1">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
            <TextInput value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search users, roles, or customers" className="pl-9" />
          </div>
        </div>
        <div className="overflow-x-auto rounded-xl border border-slate-800">
          <table className="w-full min-w-[980px] text-left text-sm">
            <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
              <tr>
                <th className="px-4 py-3">User</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Customer Access</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {visibleUsers.length === 0 && <tr><td colSpan="5" className="px-4 py-10 text-center text-slate-500">No users match the filters.</td></tr>}
              {visibleUsers.map((u) => (
                <tr key={u.id} className="cursor-pointer hover:bg-slate-950/50" onClick={() => setDetail(u)}>
                  <td className="px-4 py-3"><div className="font-semibold">{u.fullName}</div><div className="font-mono text-xs text-slate-500">{u.username}</div></td>
                  <td className="px-4 py-3"><StatusPill tone={u.role === 'SUPER_ADMIN' ? 'red' : u.role === 'GLOBAL_OPERATOR' ? 'amber' : 'blue'}>{u.role}</StatusPill></td>
                  <td className="px-4 py-3">
                    <div className="flex items-start gap-2">
                      <CustomerAccessPreview user={u} />
                      {u.role === 'CUSTOMER_OPERATOR' && (
                        <Button variant="secondary" className="shrink-0" title="Configure customer access" onClick={(event) => {
                          event.stopPropagation();
                          onEditAccess(u);
                        }}>
                          <Users className="h-4 w-4" />
                          Configure
                        </Button>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    {u.archived ? (
                      <div className="text-xs text-slate-500">Archived {formatDate(u.archivedAt)}</div>
                    ) : (
                      <StatusToggle
                        enabled={u.enabled}
                        onToggle={async (event) => {
                          event.stopPropagation();
                          const confirmed = await confirmStatusChange(confirmAction, u.fullName, u.enabled);
                          if (!confirmed) return;
                          try {
                            await api.updateUserStatus(u.id, !u.enabled);
                            await onRefresh();
                            announce('User status updated.');
                          } catch (err) { fail(err); }
                        }}
                      />
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap justify-end gap-2">
                      <ActionButton icon={<Eye className="h-4 w-4" />} label="Open" title="Open user details" onClick={(event) => {
                        event.stopPropagation();
                        setDetail(u);
                      }} variant="tertiary" />
                      {u.archived ? (
                        <ActionButton
                          icon={<RefreshCw className="h-4 w-4" />}
                          label="Restore"
                          title="Restore user"
                          onClick={async (event) => {
                            event.stopPropagation();
                            const confirmed = await confirmRestore(confirmAction, u.fullName);
                            if (!confirmed) return;
                            try {
                              await api.restoreUser(u.id);
                              await onRefresh();
                              announce('User restored as disabled.');
                            } catch (err) { fail(err); }
                          }}
                        />
                      ) : (
                        <ActionButton
                          icon={<Trash2 className="h-4 w-4" />}
                          label="Archive"
                          title="Archive user"
                          variant="danger"
                          onClick={async (event) => {
                            event.stopPropagation();
                            const confirmed = await confirmArchive(confirmAction, u.fullName);
                            if (!confirmed) return;
                            try {
                              await api.deleteUser(u.id);
                              await onRefresh();
                              announce('User archived.');
                            } catch (err) { fail(err); }
                          }}
                        />
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      {addUserOpen && (
        <AddUserDialog
          customers={customers}
          onClose={() => setAddUserOpen(false)}
          onSubmit={async (form) => {
            try {
              await api.createUser(form.username, form.password, form.fullName, form.role, form.allowedCustomerIds);
              setAddUserOpen(false);
              await onRefresh();
              announce('User created.');
            } catch (err) { fail(err); }
          }}
        />
      )}
    </div>
  );
}

function AddUserDialog({ customers, onClose, onSubmit }) {
  const [form, setForm] = useState({ username: '', password: '', fullName: '', role: 'CUSTOMER_OPERATOR', allowedCustomerIds: [] });
  const canSubmit = form.username.trim() && form.password && form.fullName.trim() && form.role;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur-sm">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (canSubmit) onSubmit({
            ...form,
            username: form.username.trim(),
            fullName: form.fullName.trim(),
            allowedCustomerIds: form.role === 'CUSTOMER_OPERATOR' ? form.allowedCustomerIds : [],
          });
        }}
        className="w-full max-w-3xl rounded-xl border border-slate-800 bg-slate-900 p-5 shadow-2xl"
      >
        <div className="mb-5 flex items-start justify-between gap-3">
          <div className="flex items-start gap-3">
            <Users className="mt-0.5 h-5 w-5 text-brand-blue" />
            <div>
              <h3 className="m-0 text-base font-bold text-white">Add User</h3>
              <p className="m-0 mt-2 text-sm leading-6 text-slate-400">Create an operator account and assign customer access for customer operators.</p>
            </div>
          </div>
          <button type="button" onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white">
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <Field label="Username"><TextInput autoFocus value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></Field>
          <Field label="Temporary Password"><TextInput type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></Field>
          <Field label="Full Name"><TextInput value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></Field>
          <Field label="Role">
            <SelectInput value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value, allowedCustomerIds: e.target.value === 'CUSTOMER_OPERATOR' ? form.allowedCustomerIds : [] })}>
              <option value="CUSTOMER_OPERATOR">CUSTOMER_OPERATOR</option>
              <option value="GLOBAL_OPERATOR">GLOBAL_OPERATOR</option>
              <option value="SECURITY_OPERATOR">SECURITY_OPERATOR</option>
              <option value="SUPER_ADMIN">SUPER_ADMIN</option>
            </SelectInput>
          </Field>
          {form.role === 'CUSTOMER_OPERATOR' && (
            <div className="md:col-span-2">
              <CheckboxList
                label="Customer Access"
                items={customers}
                checkedIds={form.allowedCustomerIds}
                onChange={(allowedCustomerIds) => setForm({ ...form, allowedCustomerIds })}
              />
            </div>
          )}
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="gradient" disabled={!canSubmit}>
            <UserPlus className="h-4 w-4" /> Add User
          </Button>
        </div>
      </form>
    </div>
  );
}

function UserDetailModal({ user, onClose, onEditAccess, onRefresh, announce, fail, confirmAction }) {
  return (
    <InteractionPage title="User Details" subtitle={user.username} icon={<Users className="h-5 w-5 text-brand-blue" />} onBack={onClose}>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="m-0 text-xl font-bold">{user.fullName}</h3>
          <p className="m-0 mt-1 font-mono text-sm text-slate-500">{user.username}</p>
        </div>
        <div className="flex gap-2">
          <StatusPill tone={user.role === 'SUPER_ADMIN' ? 'red' : user.role === 'GLOBAL_OPERATOR' ? 'amber' : 'blue'}>{user.role}</StatusPill>
          <StatusPill tone={user.archived ? 'amber' : user.enabled ? 'green' : 'slate'}>{user.archived ? 'Archived' : user.enabled ? 'Enabled' : 'Disabled'}</StatusPill>
        </div>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <DetailBlock label="Role" value={user.role} />
        <DetailBlock label="Status" value={user.enabled ? 'Enabled' : 'Disabled'} />
        <div className="md:col-span-2">
          <DetailBlock label="Customer Access" value={user.role === 'CUSTOMER_OPERATOR' ? (user.allowedCustomers || []).map((c) => c.customerName).join(', ') || 'No customers assigned' : 'All customers'} />
        </div>
      </div>
      <div className="mt-5 flex gap-2">
        {user.role === 'CUSTOMER_OPERATOR' && !user.archived && <Button variant="ghost" onClick={() => onEditAccess(user)}>Manage Access</Button>}
        {user.archived ? (
          <Button variant="secondary" onClick={async () => {
            const confirmed = await confirmRestore(confirmAction, user.fullName);
            if (!confirmed) return;
            try {
              await api.restoreUser(user.id);
              await onRefresh();
              onClose();
              announce('User restored as disabled.');
            } catch (err) { fail(err); }
          }}><RefreshCw className="h-4 w-4" /> Restore User</Button>
        ) : (
          <>
            <StatusToggle enabled={user.enabled} onToggle={async () => {
              const confirmed = await confirmStatusChange(confirmAction, user.fullName, user.enabled);
              if (!confirmed) return;
              try {
                await api.updateUserStatus(user.id, !user.enabled);
                await onRefresh();
                onClose();
                announce('User status updated.');
              } catch (err) { fail(err); }
            }} />
            <Button variant="danger" onClick={async () => {
              const confirmed = await confirmArchive(confirmAction, user.fullName);
              if (!confirmed) return;
              try {
                await api.deleteUser(user.id);
                await onRefresh();
                onClose();
                announce('User archived.');
              } catch (err) { fail(err); }
            }}><Trash2 className="h-4 w-4" /> Archive User</Button>
          </>
        )}
      </div>
    </InteractionPage>
  );
}

function UploadModal({ customers, selectedCustomerId, templates, onClose, onDone, fail, onDirtyChange }) {
  const activeCustomers = customers.filter((customer) => !customer.archived);
  const defaultCustomer = selectedCustomerId !== 'ALL' ? selectedCustomerId : activeCustomers[0]?.id || '';
  const [form, setForm] = useState({ customerId: defaultCustomer, templateId: '', file: null, processing: false });
  const [softwareAccessRows, setSoftwareAccessRows] = useState([]);
  const enabledAssignedSoftwareIds = new Set(softwareAccessRows.filter((row) => row.assigned && row.enabled).map((row) => row.software.id));
  const assignedCount = softwareAccessRows.filter((row) => row.assigned).length;
  const enabledAssignedCount = softwareAccessRows.filter((row) => row.assigned && row.enabled).length;
  const availableTemplates = templates.filter((template) => (
    template.enabled
    && !template.archived
    && !template.software?.archived
    && enabledAssignedSoftwareIds.has(template.software?.id)
    && (!template.customer || (!template.customer.archived && template.customer.id === form.customerId))
  ));
  const uploadDirty = form.customerId !== defaultCustomer || Boolean(form.templateId) || Boolean(form.file) || form.processing;

  useEffect(() => {
    onDirtyChange?.(uploadDirty);
  }, [uploadDirty, onDirtyChange]);

  useEffect(() => {
    if (!form.customerId) {
      return;
    }
    let active = true;
    api.getCustomerSoftwareAccess(form.customerId)
      .then((result) => {
        if (active) setSoftwareAccessRows(result.assignments || []);
      })
      .catch(() => {
        if (active) setSoftwareAccessRows([]);
      });
    return () => {
      active = false;
    };
  }, [form.customerId]);

  return (
    <InteractionPage title="Upload Scan File" subtitle="Run ingestion with an enabled customer template." icon={<Upload className="h-5 w-5 text-brand-blue" />} onBack={onClose}>
      <form
        className="space-y-4"
        onSubmit={async (e) => {
          e.preventDefault();
          setForm((prev) => ({ ...prev, processing: true }));
          try {
            await api.ingestFile(form.file, form.customerId, form.templateId);
            await onDone();
          } catch (err) {
            setForm((prev) => ({ ...prev, processing: false }));
            fail(err);
          }
        }}
      >
        <Field label="Customer">
          <SelectInput value={form.customerId} onChange={(e) => setForm({ ...form, customerId: e.target.value, templateId: '' })} required>
            {activeCustomers.map((customer) => <option key={customer.id} value={customer.id}>{customer.customerName}</option>)}
          </SelectInput>
        </Field>
        <Field label="Enabled Software Template">
          <SelectInput value={form.templateId} onChange={(e) => setForm({ ...form, templateId: e.target.value })} required>
            <option value="">Select template</option>
            {availableTemplates.map((template) => <option key={template.id} value={template.id}>{template.name} · {template.software?.softwareName}</option>)}
          </SelectInput>
          {form.customerId && assignedCount === 0 && <div className="mt-2 text-xs text-amber-300">This customer has no assigned software. Assign software in Customer Management before uploading.</div>}
          {form.customerId && assignedCount > 0 && enabledAssignedCount === 0 && <div className="mt-2 text-xs text-amber-300">All assigned software is disabled. Enable at least one assignment before uploading.</div>}
          {form.customerId && enabledAssignedCount > 0 && availableTemplates.length === 0 && <div className="mt-2 text-xs text-amber-300">No active templates are available for the assigned software. Create or enable a template first.</div>}
        </Field>
        <Field label="Scan File">
          <input type="file" onChange={(e) => setForm({ ...form, file: e.target.files?.[0] || null })} className="w-full rounded-lg border border-dashed border-slate-700 bg-slate-950 p-4 text-sm text-slate-300" required />
        </Field>
        <Button type="submit" variant="gradient" disabled={form.processing || !form.file || !form.templateId} className="w-full">
          {form.processing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />} Run Ingestion
        </Button>
      </form>
    </InteractionPage>
  );
}

function FindingModal({ finding, customers, selectedCustomerId, onClose, onDone, fail }) {
  const isEdit = Boolean(finding.id);
  const defaultCustomer = finding.customer?.id || (selectedCustomerId !== 'ALL' ? selectedCustomerId : customers[0]?.id || '');
  const [form, setForm] = useState({ ...emptyFinding, ...finding, customerId: defaultCustomer });
  return (
    <InteractionPage title={isEdit ? 'Edit Finding' : 'Create Manual Finding'} subtitle="Manual finding management is kept out of the main list flow." icon={<Shield className="h-5 w-5 text-brand-blue" />} onBack={onClose}>
      <form
        className="grid gap-4 md:grid-cols-2"
        onSubmit={async (e) => {
          e.preventDefault();
          try {
            const payload = { ...form, cvssScore: form.cvssScore ? String(form.cvssScore) : null };
            if (isEdit) await api.updateFinding(finding.id, payload);
            else await api.createFinding(payload);
            await onDone();
          } catch (err) { fail(err); }
        }}
      >
        {!isEdit && (
          <Field label="Customer">
            <SelectInput value={form.customerId} onChange={(e) => setForm({ ...form, customerId: e.target.value })} required>
              {customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.customerName}</option>)}
            </SelectInput>
          </Field>
        )}
        <Field label="Severity">
          <SelectInput value={form.severity || 'MEDIUM'} onChange={(e) => setForm({ ...form, severity: e.target.value })}>
            {SEVERITIES.map((severity) => <option key={severity} value={severity}>{severity}</option>)}
          </SelectInput>
        </Field>
        <Field label="Issue Title"><TextInput value={form.issueTitle || ''} onChange={(e) => setForm({ ...form, issueTitle: e.target.value })} required /></Field>
        <Field label="CVE ID"><TextInput value={form.cveId || ''} onChange={(e) => setForm({ ...form, cveId: e.target.value })} /></Field>
        <Field label="CVSS Score"><TextInput type="number" step="0.1" min="0" max="10" value={form.cvssScore || ''} onChange={(e) => setForm({ ...form, cvssScore: e.target.value })} /></Field>
        <Field label="Number of Devices"><TextInput type="number" min="0" value={form.numberOfDevices || 0} onChange={(e) => setForm({ ...form, numberOfDevices: Number(e.target.value) })} /></Field>
        <Field label="Affected Devices"><TextInput value={form.affectedDevices || ''} onChange={(e) => setForm({ ...form, affectedDevices: e.target.value })} /></Field>
        <div className="md:col-span-2"><Field label="Summary"><TextArea rows={3} value={form.summary || ''} onChange={(e) => setForm({ ...form, summary: e.target.value })} /></Field></div>
        <div className="md:col-span-2"><Field label="Solution"><TextArea rows={3} value={form.solution || ''} onChange={(e) => setForm({ ...form, solution: e.target.value })} /></Field></div>
        <label className="flex items-center gap-2 text-sm text-slate-300"><input type="checkbox" checked={Boolean(form.knownExploited)} onChange={(e) => setForm({ ...form, knownExploited: e.target.checked })} /> Known exploited</label>
        <label className="flex items-center gap-2 text-sm text-slate-300"><input type="checkbox" checked={Boolean(form.knownRansomwareCampaign)} onChange={(e) => setForm({ ...form, knownRansomwareCampaign: e.target.checked })} /> Ransomware campaign</label>
        <div className="md:col-span-2"><Button type="submit" variant="gradient" className="w-full">Save Finding</Button></div>
      </form>
    </InteractionPage>
  );
}

function TemplateModal({ initial, softwares, customers, onClose, onDone, fail, onDirtyChange }) {
  const activeSoftwares = softwares.filter((software) => !software.archived);
  const [form, setForm] = useState({
    id: initial.id,
    name: initial.name || '',
    description: initial.description || '',
    softwareId: initial.software?.id || initial.softwareId || activeSoftwares[0]?.id || '',
    customerId: initial.customer?.id || initial.customerId || '',
    fileFormat: initial.fileFormat || 'CSV',
    hasHeaderRow: initial.hasHeaderRow ?? true,
    enabled: initial.enabled ?? true,
    sampleFile: null,
    mappings: parseMappings(initial.columnMappingJson),
    saved: Boolean(initial.id),
  });
  const [samplePreview, setSamplePreview] = useState({ columns: [], firstDataRow: [], parseNote: '' });
  const [mapperNotice, setMapperNotice] = useState('');
  const [mapperError, setMapperError] = useState('');
  const [saveReview, setSaveReview] = useState(null);
  const [attemptedAction, setAttemptedAction] = useState('');
  const [savingTemplate, setSavingTemplate] = useState(false);
  const [saveReviewError, setSaveReviewError] = useState('');
  const [targetFields, setTargetFields] = useState(TARGET_FIELDS);
  const [previewResult, setPreviewResult] = useState(null);
  const [previewing, setPreviewing] = useState(false);
  const [activeStep, setActiveStep] = useState('basics');
  const [customerSoftwareAccess, setCustomerSoftwareAccess] = useState([]);
  const enabledCustomerSoftwareIds = new Set(customerSoftwareAccess.filter((row) => row.assigned && row.enabled).map((row) => row.software.id));
  const selectableSoftwares = form.customerId
    ? activeSoftwares.filter((software) => enabledCustomerSoftwareIds.has(software.id) || software.id === form.softwareId)
    : activeSoftwares;
  const requiredMissing = targetFields.filter((field) => field.required && !form.mappings.some((mapping) => mapping.targetFieldName === field.value));
  const unmappedSource = form.mappings.filter((mapping) => !mapping.targetFieldName && mapping.sourceColumnIndex !== null);
  const mappedDestinationValues = form.mappings.map((mapping) => mapping.targetFieldName).filter(Boolean);
  const unmappedDestination = targetFields.filter((field) => !mappedDestinationValues.includes(field.value));
  const optionalDestinationMissing = unmappedDestination.filter((field) => !field.required);
  const conversionIssues = getMappingConversionIssues(form.mappings, targetFields);
  const templateDirty = isTemplateDirty(initial, form);
  const definitionErrors = {
    name: !hasText(form.name) ? 'Template name is mandatory.' : '',
    softwareId: !form.softwareId ? 'Software is mandatory.' : '',
    sampleFile: !form.sampleFile && !form.id ? 'Sample file is mandatory before extracting columns.' : '',
  };
  const showDefinitionErrors = Boolean(attemptedAction);
  const shownDefinitionErrors = {
    name: showDefinitionErrors ? definitionErrors.name : '',
    softwareId: showDefinitionErrors ? definitionErrors.softwareId : '',
    sampleFile: attemptedAction === 'extract' ? definitionErrors.sampleFile : '',
  };

  useEffect(() => {
    onDirtyChange?.(templateDirty);
  }, [templateDirty, onDirtyChange]);

  useEffect(() => {
    if (!templateDirty) return undefined;
    const beforeUnload = (event) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', beforeUnload);
    return () => window.removeEventListener('beforeunload', beforeUnload);
  }, [templateDirty]);

  useEffect(() => {
    let active = true;
    api.getTemplateSchema()
      .then((schema) => {
        if (active && Array.isArray(schema?.fields) && schema.fields.length) {
          setTargetFields(schema.fields);
          setForm((prev) => ({ ...prev, mappings: normalizeMappings(prev.mappings, schema.fields) }));
        }
      })
      .catch(() => {
        if (active) setMapperNotice('Using bundled destination schema because backend schema metadata could not be loaded.');
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!form.customerId) {
      return;
    }
    let active = true;
    api.getCustomerSoftwareAccess(form.customerId)
      .then((result) => {
        if (active) setCustomerSoftwareAccess(result.assignments || []);
      })
      .catch(() => {
        if (active) setCustomerSoftwareAccess([]);
      });
    return () => {
      active = false;
    };
  }, [form.customerId]);

  const requestClose = async () => {
    await onClose();
  };

  const validateDefinition = ({ requireSample = false } = {}) => {
    const errors = [];
    if (definitionErrors.name) errors.push(definitionErrors.name);
    if (definitionErrors.softwareId) errors.push(definitionErrors.softwareId);
    if (requireSample && definitionErrors.sampleFile) errors.push(definitionErrors.sampleFile);
    if (errors.length) {
      setMapperError(errors.join(' '));
      return false;
    }
    return true;
  };

  const saveDefinition = async ({ enabledOverride, formOverride } = {}) => {
    const source = formOverride || form;
    if (source.id) {
      await api.updateTemplate(source.id, {
        name: source.name.trim(),
        description: source.description,
        fileFormat: source.fileFormat,
        hasHeaderRow: source.hasHeaderRow,
        enabled: enabledOverride ?? source.enabled,
      });
      return source.id;
    }
    const template = await api.createTemplate(source.name.trim(), source.fileFormat, source.softwareId, source.customerId || null, source.hasHeaderRow, source.description);
    if ((enabledOverride ?? source.enabled) !== true) {
      await api.updateTemplate(template.id, { enabled: false });
    }
    setForm((prev) => ({ ...prev, id: template.id, saved: true }));
    return template.id;
  };

  const buildMappingDocument = ({ status, ignoreSourceAcknowledged = false, ignoreSourceComment = '' }) => ({
    metadata: {
      version: 1,
      status,
      savedAt: new Date().toISOString(),
      ignoredSourceColumns: unmappedSource.map((mapping) => mapping.sourceColumnName),
      ignoreSourceAcknowledged,
      ignoreSourceComment,
      requiredDestinationFieldsMissing: requiredMissing.map((field) => ({ name: field.value, label: field.label })),
      optionalDestinationFieldsMissing: optionalDestinationMissing.map((field) => ({ name: field.value, label: field.label })),
    },
    mappings: normalizeMappings(form.mappings, targetFields)
      .filter((mapping) => mapping.targetFieldName)
        .map((mapping) => toMappingPayload(mapping, targetFields)),
  });

  const runPreview = async () => {
    setAttemptedAction('preview');
    if (!validateDefinition()) return;
    if (!form.id && !form.sampleFile) {
      setMapperError('Upload and extract a sample file before testing mapping rules.');
      return;
    }
    setPreviewing(true);
    setPreviewResult(null);
    setMapperError('');
    try {
      const id = form.id ? await saveDefinition() : await saveDefinition({ enabledOverride: false });
      if (form.sampleFile) {
        const res = await api.autoGenerateTemplate(id, form.sampleFile, form.fileFormat, form.hasHeaderRow);
        const headers = res.headers || [];
        if (headers.length) {
          setSamplePreview((prev) => ({ ...prev, columns: headers, firstDataRow: res.firstDataRow || prev.firstDataRow }));
        }
      }
      const preview = await api.previewTemplateMappings(id, buildMappingDocument({ status: requiredMissing.length ? 'draft' : 'ready' }));
      setPreviewResult(preview);
      setMapperNotice(`Backend preview tested ${preview.testedRows || 0} sample row${preview.testedRows === 1 ? '' : 's'}.`);
    } catch (err) {
      setMapperNotice('');
      setMapperError(err.message || String(err));
      fail(err);
    } finally {
      setPreviewing(false);
    }
  };

  const extractColumns = async ({ fileOverride = null, formOverride = null } = {}) => {
    const source = formOverride || form;
    const sampleFile = fileOverride || source.sampleFile;
    try {
      setAttemptedAction('extract');
      const errors = [];
      if (!hasText(source.name)) errors.push('Template name is mandatory.');
      if (!source.softwareId) errors.push('Software is mandatory.');
      if (!sampleFile) errors.push('Sample file is mandatory before extracting columns.');
      if (errors.length) {
        setMapperError(errors.join(' '));
        return false;
      }
      setMapperNotice('Extracting columns from sample file...');
      const id = await saveDefinition({ formOverride: source });
      const res = await api.autoGenerateTemplate(id, sampleFile, source.fileFormat, source.hasHeaderRow);
      const headers = res.headers || [];
      const nextMappings = headers.length
        ? buildDraftMappings(headers, source.mappings, targetFields)
        : normalizeMappings(res.currentMapping || [], targetFields);
      setForm((prev) => ({ ...prev, id, saved: true, sampleFile, mappings: nextMappings }));
      if (res.headers?.length) {
        setSamplePreview((prev) => ({ ...prev, columns: res.headers, firstDataRow: res.firstDataRow || prev.firstDataRow }));
      }
      setPreviewResult(null);
      setMapperNotice(headers.length ? `${headers.length} columns extracted and ready to map.` : 'No columns were returned by the extractor.');
      setMapperError('');
      return true;
    } catch (err) {
      setMapperNotice('');
      fail(err);
      return false;
    }
  };

  const handleAutoMap = () => {
    const { mappings, count, conversionCount } = autoMapMappings(form.mappings, targetFields);
    setForm({ ...form, mappings });
    setPreviewResult(null);
    setMapperNotice(count ? `Auto mapped ${count} source column${count === 1 ? '' : 's'} and selected ${conversionCount} conversion rule${conversionCount === 1 ? '' : 's'} by destination type.` : 'No confident automatic matches found.');
    setMapperError('');
  };

  const saveTemplateDocument = async ({ draft = false, ignoreSourceAcknowledged = false, ignoreSourceComment = '' } = {}) => {
    setSavingTemplate(true);
    setSaveReviewError('');
    try {
      const id = await saveDefinition({ enabledOverride: draft ? false : form.enabled });
      await api.saveTemplateMappings(id, buildMappingDocument({
        status: draft ? 'draft' : 'ready',
        ignoreSourceAcknowledged,
        ignoreSourceComment,
      }));
      await onDone();
    } finally {
      setSavingTemplate(false);
    }
  };

  const openSaveReview = (draft = false) => {
    setAttemptedAction(draft ? 'draft' : 'save');
    if (!validateDefinition()) return;
    setMapperError('');
    setSaveReviewError('');
    setSaveReview({
      draft,
      acknowledgeIgnoredSources: false,
      ignoreSourceComment: '',
    });
  };

  const stepDefinitions = [
    ['basics', 'Basics'],
    ['automap', 'Auto-map'],
    ['required', 'Required fields'],
    ['rules', 'Value rules'],
    ...(TEMPLATE_SAMPLE_TEST_ENABLED ? [['test', 'Test sample']] : []),
    ['save', 'Save'],
  ];
  const activeStepIndex = Math.max(0, stepDefinitions.findIndex(([id]) => id === activeStep));
  const mappedCount = form.mappings.filter((mapping) => mapping.targetFieldName).length;
  const canSaveReady = requiredMissing.length === 0 && conversionIssues.length === 0;

  const goStep = (stepId) => {
    setMapperError('');
    setActiveStep(stepId);
  };

  const nextStep = async () => {
    if (activeStep === 'basics') {
      setAttemptedAction('extract');
      if (!validateDefinition({ requireSample: !form.id && !form.sampleFile })) return;
      if (!form.mappings.length) {
        setMapperError('Upload a sample file and let columns extract before continuing.');
        return;
      }
      goStep('automap');
    } else if (activeStep === 'automap') {
      goStep('required');
    } else if (activeStep === 'required') {
      goStep('rules');
    } else if (activeStep === 'rules') {
      goStep(TEMPLATE_SAMPLE_TEST_ENABLED ? 'test' : 'save');
    } else if (activeStep === 'test') {
      goStep('save');
    }
  };

  const previousStep = () => {
    const previous = stepDefinitions[Math.max(0, activeStepIndex - 1)]?.[0];
    if (previous) goStep(previous);
  };

  return (
    <div className="space-y-5">
      <div className="sticky top-0 z-20 -mx-6 -mt-6 border-b border-slate-800 bg-slate-950/95 px-6 py-4 backdrop-blur">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <FileSpreadsheet className="h-5 w-5 text-brand-blue" />
            <div>
              <h3 className="m-0 text-lg font-bold">Template Mapping Workspace</h3>
              <p className="m-0 text-xs text-slate-500">Map uploaded source columns to the PostgreSQL active finding schema.</p>
            </div>
          </div>
          <Button variant="ghost" onClick={requestClose}><X className="h-4 w-4" /> Close</Button>
        </div>
      </div>
      <TemplateStepNav steps={stepDefinitions} activeStep={activeStep} onStep={goStep} />
      {mapperNotice && <AlertBox tone="green">{mapperNotice}</AlertBox>}
      {mapperError && <AlertBox tone="red">{mapperError}</AlertBox>}
      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
        <section className="space-y-5">
          {activeStep === 'basics' && (
          <TemplateBasicsStep
              form={form}
              setForm={(next) => {
                setPreviewResult(null);
                setForm(next);
              }}
              softwares={selectableSoftwares}
              customers={customers}
              customerSoftwareWarning={form.customerId && selectableSoftwares.length === 0 ? 'Assign and enable software for this customer before creating a customer template.' : ''}
              samplePreview={samplePreview}
              setSamplePreview={setSamplePreview}
              targetFields={targetFields}
              shownDefinitionErrors={shownDefinitionErrors}
              setMapperNotice={setMapperNotice}
              setMapperError={setMapperError}
              setAttemptedAction={setAttemptedAction}
              extractColumns={extractColumns}
              autoExtractColumns={extractColumns}
            />
          )}
          {activeStep === 'automap' && (
            <AutoMapStep
              mappings={form.mappings}
              mappedCount={mappedCount}
              samplePreview={samplePreview}
              onAutoMap={handleAutoMap}
              onReview={() => goStep('required')}
            />
          )}
          {activeStep === 'required' && (
            <RequiredFieldsStep
              mappings={form.mappings}
              setMappings={(mappings) => {
                setPreviewResult(null);
                setForm({ ...form, mappings });
              }}
              targetFields={targetFields}
              requiredMissing={requiredMissing}
              addDestinationRule={(field) => {
                setPreviewResult(null);
                setForm({ ...form, mappings: addDestinationOnlyMapping(form.mappings, field) });
              }}
            />
          )}
          {activeStep === 'rules' && (
            <SelfServiceMappingGrid
              mappings={form.mappings}
              setMappings={(mappings) => {
                setPreviewResult(null);
                setForm({ ...form, mappings });
              }}
              hasHeaderRow={form.hasHeaderRow}
              samplePreview={samplePreview}
              requiredMissing={requiredMissing}
              conversionIssues={conversionIssues}
              targetFields={targetFields}
              addDestinationRule={(field) => {
                setPreviewResult(null);
                setForm({ ...form, mappings: addDestinationOnlyMapping(form.mappings, field) });
              }}
            />
          )}
          {TEMPLATE_SAMPLE_TEST_ENABLED && activeStep === 'test' && (
            <TemplateTestStep
              previewResult={previewResult}
              previewing={previewing}
              runPreview={runPreview}
              requiredMissing={requiredMissing}
              conversionIssues={conversionIssues}
              mappings={form.mappings}
            />
          )}
          {activeStep === 'save' && (
            <TemplateFinalStep
              mappedCount={mappedCount}
              requiredMissing={requiredMissing}
              unmappedSource={unmappedSource}
              optionalDestinationMissing={optionalDestinationMissing}
              conversionIssues={conversionIssues}
              previewResult={previewResult}
              canSaveReady={canSaveReady}
              openSaveReview={openSaveReview}
            />
          )}
          <div className="flex flex-wrap justify-between gap-2 border-t border-slate-800 pt-4">
            <Button variant="ghost" onClick={previousStep} disabled={activeStepIndex === 0}>Back</Button>
            <div className="flex flex-wrap gap-2">
              {requiredMissing.length > 0 && <Button variant="warn" onClick={() => openSaveReview(true)}>Save Draft</Button>}
              {activeStep !== 'save' ? (
                <Button variant="gradient" onClick={nextStep}>Continue</Button>
              ) : (
                <Button variant="gradient" disabled={!canSaveReady} onClick={() => openSaveReview(false)}>Save Template</Button>
              )}
            </div>
          </div>
        </section>
        <aside className="space-y-5">
          <MapperSummary mappings={form.mappings} requiredMissing={requiredMissing} unmappedSource={unmappedSource} unmappedDestination={unmappedDestination} conversionIssues={conversionIssues} />
          <SchemaReference mappedFields={mappedDestinationValues} targetFields={targetFields} />
        </aside>
      </div>
      {saveReview && (
        <TemplateSaveReviewDialog
          review={saveReview}
          setReview={setSaveReview}
          mappedCount={form.mappings.filter((mapping) => mapping.targetFieldName).length}
          defaultValueCount={form.mappings.filter((mapping) => mapping.emptySourcePolicy?.mode === 'USE_DEFAULT' && hasText(mapping.emptySourcePolicy?.defaultValue)).length}
          forceValueCount={form.mappings.filter((mapping) => hasText(mapping.forceValue)).length}
          conversionCount={form.mappings.filter((mapping) => mapping.conversionType && mapping.conversionType !== 'NONE').length}
          requiredMissing={requiredMissing}
          unmappedSource={unmappedSource}
          optionalDestinationMissing={optionalDestinationMissing}
          templateEnabled={form.enabled}
          saving={savingTemplate}
          error={saveReviewError}
          onCancel={() => setSaveReview(null)}
          onConfirm={async () => {
            try {
              setSaveReviewError('');
              if (!saveReview.draft && unmappedSource.length > 0 && !saveReview.acknowledgeIgnoredSources) {
                setSaveReviewError('Acknowledge ignored source columns before saving, or map those columns.');
                return;
              }
              await saveTemplateDocument({
                draft: saveReview.draft,
                ignoreSourceAcknowledged: !saveReview.draft && unmappedSource.length > 0 ? saveReview.acknowledgeIgnoredSources : false,
                ignoreSourceComment: !saveReview.draft ? saveReview.ignoreSourceComment : '',
              });
            } catch (err) {
              const message = err.message || String(err);
              setSaveReviewError(message);
              fail(err);
            }
          }}
        />
      )}
    </div>
  );
}

function TemplateStepNav({ steps, activeStep, onStep }) {
  const activeIndex = steps.findIndex(([id]) => id === activeStep);
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-950/70 p-2">
      <div className="flex min-w-max items-center gap-2">
        {steps.map(([id, label], index) => {
          const active = id === activeStep;
          const complete = index < activeIndex;
          return (
            <button
              key={id}
              type="button"
              onClick={() => onStep(id)}
              className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-bold transition ${
                active
                  ? 'border-brand-blue bg-brand-blue text-slate-950'
                  : complete
                    ? 'border-green-500/30 bg-green-500/10 text-green-300'
                    : 'border-slate-800 bg-slate-900 text-slate-400 hover:text-white'
              }`}
            >
              <span className="flex h-5 w-5 items-center justify-center rounded-full border border-current text-[10px]">{index + 1}</span>
              {label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function TemplateBasicsStep({
  form,
  setForm,
  softwares,
  customers,
  samplePreview,
  setSamplePreview,
  targetFields,
  shownDefinitionErrors,
  setMapperNotice,
  setMapperError,
  setAttemptedAction,
  extractColumns,
  autoExtractColumns,
  customerSoftwareWarning = '',
}) {
  const activeSoftwares = softwares.filter((software) => !software.archived);
  const activeCustomers = customers.filter((customer) => !customer.archived);
  return (
    <section className="space-y-5">
      <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
        {customerSoftwareWarning && <div className="mb-4 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-200">{customerSoftwareWarning}</div>}
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="m-0 text-sm font-bold">Template Basics</h3>
            <p className="m-0 mt-1 text-xs text-slate-500">Set the vendor, scope, file type, and sample file used to build this template.</p>
          </div>
          <StatusPill tone={form.id ? 'green' : 'blue'}>{form.id ? 'Saved definition' : 'New template'}</StatusPill>
        </div>
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <Field label="Software" required error={shownDefinitionErrors.softwareId}>
            <SelectInput value={form.softwareId} onChange={(e) => setForm({ ...form, softwareId: e.target.value })} disabled={Boolean(form.id)} className={shownDefinitionErrors.softwareId ? 'border-red-500' : ''}>
              {activeSoftwares.map((software) => <option key={software.id} value={software.id}>{software.softwareName}</option>)}
            </SelectInput>
          </Field>
          <Field label="Scope">
            <SelectInput value={form.customerId} onChange={(e) => setForm({ ...form, customerId: e.target.value, softwareId: e.target.value ? '' : form.softwareId })} disabled={Boolean(form.id)}>
              <option value="">Global standard</option>
              {activeCustomers.map((customer) => <option key={customer.id} value={customer.id}>{customer.customerName}</option>)}
            </SelectInput>
          </Field>
          <Field label="Template Name" required error={shownDefinitionErrors.name}>
            <TextInput value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={shownDefinitionErrors.name ? 'border-red-500' : ''} />
          </Field>
          <Field label="File Format">
            <SelectInput value={form.fileFormat} onChange={async (e) => {
              const fileFormat = e.target.value;
              const preview = form.sampleFile ? await readDelimitedPreview(form.sampleFile, fileFormat, form.hasHeaderRow) : { columns: [], firstDataRow: [], parseNote: '' };
              setSamplePreview(preview);
              setForm({ ...form, fileFormat, mappings: preview.columns.length ? buildDraftMappings(preview.columns, form.mappings, targetFields) : form.mappings });
            }}>{FORMATS.map((format) => <option key={format}>{format}</option>)}</SelectInput>
          </Field>
        </div>
        <div className="mt-4 grid gap-4 md:grid-cols-[minmax(0,1fr)_220px] md:items-end">
          <Field label="Description">
            <TextInput value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Optional operator note" />
          </Field>
          <label className="flex items-center gap-2 rounded-lg border border-slate-800 bg-slate-900 px-3 py-2 text-sm text-slate-300">
            <input type="checkbox" checked={form.hasHeaderRow} onChange={async (e) => {
              const hasHeaderRow = e.target.checked;
              const preview = form.sampleFile ? await readDelimitedPreview(form.sampleFile, form.fileFormat, hasHeaderRow) : { columns: [], firstDataRow: [], parseNote: '' };
              setSamplePreview(preview);
              setForm({ ...form, hasHeaderRow, mappings: preview.columns.length ? buildDraftMappings(preview.columns, form.mappings, targetFields) : form.mappings });
            }} />
            First row has headers
          </label>
        </div>
      </div>
      <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
        <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_auto] md:items-end">
          <Field label="Sample File" required={!form.id} error={shownDefinitionErrors.sampleFile}>
            <input type="file" onChange={async (e) => {
              const file = e.target.files?.[0] || null;
              const preview = file ? await readDelimitedPreview(file, form.fileFormat, form.hasHeaderRow) : { columns: [], firstDataRow: [], parseNote: '' };
              const nextForm = { ...form, sampleFile: file, mappings: preview.columns.length ? buildDraftMappings(preview.columns, form.mappings, targetFields) : form.mappings };
              setSamplePreview(preview);
              setMapperNotice(preview.columns.length ? 'Columns loaded from the selected sample file.' : preview.parseNote);
              setMapperError('');
              setAttemptedAction('');
              setForm(nextForm);
              if (file && hasText(nextForm.name) && nextForm.softwareId) {
                await autoExtractColumns({ fileOverride: file, formOverride: nextForm });
              } else if (file) {
                setMapperNotice('Sample file loaded. Add template name and software, then extraction will be available.');
              }
            }} className={`w-full rounded-lg border border-dashed p-3 text-sm text-slate-300 ${shownDefinitionErrors.sampleFile ? 'border-red-500 bg-red-500/10' : 'border-slate-700 bg-slate-950/60'}`} />
          </Field>
          <Button variant="gradient" onClick={extractColumns}><Upload className="h-4 w-4" /> Extract Columns</Button>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <MetricCard label="Detected Columns" value={samplePreview.columns.length} />
          <MetricCard label="Mapped Fields" value={form.mappings.filter((mapping) => mapping.targetFieldName).length} tone="green" />
          <MetricCard label="Format" value={form.fileFormat} tone="amber" />
        </div>
        {samplePreview.columns.length > 0 && (
          <div className="mt-4 rounded-lg border border-slate-800 bg-slate-900 p-3">
            <div className="mb-2 text-[10px] font-bold uppercase tracking-wider text-slate-500">Extracted columns</div>
            <div className="flex max-h-24 flex-wrap gap-2 overflow-y-auto">
              {samplePreview.columns.map((column, index) => <StatusPill key={`${column}-${index}`} tone="slate">{column || `Column_${index}`}</StatusPill>)}
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

function AutoMapStep({ mappings, mappedCount, samplePreview, onAutoMap, onReview }) {
  const unmappedCount = mappings.length - mappedCount;
  return (
    <section className="space-y-5">
      <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="m-0 text-sm font-bold">Auto-map Review</h3>
            <p className="m-0 mt-1 text-xs text-slate-500">Start with automatic matches, then review any source columns that still need attention.</p>
          </div>
          <Button variant="gradient" onClick={onAutoMap}>Auto Map</Button>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <MetricCard label="Source Columns" value={mappings.length} />
          <MetricCard label="Mapped" value={mappedCount} tone="green" />
          <MetricCard label="Needs Review" value={unmappedCount} tone={unmappedCount ? 'amber' : 'green'} />
        </div>
      </div>
      <div className="overflow-hidden rounded-xl border border-slate-800">
        <table className="w-full min-w-[760px] text-left text-xs">
          <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
            <tr>
              <th className="px-3 py-3">Source column</th>
              <th className="px-3 py-3">Sample value</th>
              <th className="px-3 py-3">Suggested destination</th>
              <th className="px-3 py-3">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {mappings.map((mapping, index) => (
              <tr key={`${mapping.sourceColumnName}-${index}`} className="bg-slate-900/50">
                <td className="px-3 py-3 font-semibold">{mapping.sourceColumnName}</td>
                <td className="px-3 py-3 max-w-56 truncate text-slate-400">{samplePreview.firstDataRow?.[index] || 'No sample value'}</td>
                <td className="px-3 py-3 font-mono text-slate-300">{mapping.targetFieldName || 'Unmapped'}</td>
                <td className="px-3 py-3"><StatusPill tone={mapping.targetFieldName ? 'green' : 'amber'}>{mapping.targetFieldName ? 'Mapped' : 'Review'}</StatusPill></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Button variant="ghost" onClick={onReview}>Review Required Fields</Button>
    </section>
  );
}

function RequiredFieldsStep({ mappings, setMappings, targetFields, requiredMissing, addDestinationRule }) {
  const updateTarget = (fieldValue, sourceIndex) => {
    const sourceColumnIndex = sourceIndex === '' ? null : Number(sourceIndex);
    setMappings(mappings.map((mapping) => {
      if (mapping.targetFieldName === fieldValue) {
        return { ...mapping, targetFieldName: '' };
      }
      if (mapping.sourceColumnIndex === sourceColumnIndex) {
        const field = targetFields.find((item) => item.value === fieldValue);
        const conversionType = recommendConversion(field?.type || 'STRING', mapping.sourceDataType || 'STRING');
        return {
          ...mapping,
          targetFieldName: fieldValue,
          targetDataType: field?.type || 'STRING',
          conversionType,
          conversionErrorMode: 'FAIL_ROW',
          conversionAutoSelected: conversionType !== 'NONE',
        };
      }
      return mapping;
    }));
  };
  const requiredFields = targetFields.filter((field) => field.required);
  return (
    <section className="space-y-5">
      <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
        <h3 className="m-0 text-sm font-bold">Required Fields</h3>
        <p className="m-0 mt-1 text-xs text-slate-500">Confirm every mandatory destination field has a source column before enabling this template.</p>
      </div>
      <div className="overflow-hidden rounded-xl border border-slate-800">
        <table className="w-full min-w-[720px] text-left text-xs">
          <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
            <tr>
              <th className="px-3 py-3">Destination field</th>
              <th className="px-3 py-3">Source column</th>
              <th className="px-3 py-3">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {requiredFields.map((field) => {
              const mapped = mappings.find((mapping) => mapping.targetFieldName === field.value);
              return (
                <tr key={field.value} className="bg-slate-900/50">
                  <td className="px-3 py-3">
                    <div className="font-semibold">{field.label}</div>
                    <div className="font-mono text-[10px] text-slate-500">{field.value}</div>
                  </td>
                  <td className="px-3 py-3">
                    <div className="grid gap-2 md:grid-cols-[minmax(0,1fr)_auto]">
                      <SelectInput value={mapped?.sourceColumnIndex ?? ''} onChange={(event) => updateTarget(field.value, event.target.value)} disabled={mapped?.mappingMode === 'CONSTANT'}>
                        <option value="">Choose source column</option>
                        {mappings.filter((mapping) => mapping.sourceColumnIndex !== null && mapping.sourceColumnIndex !== undefined).map((mapping) => <option key={mapping.sourceColumnIndex} value={mapping.sourceColumnIndex}>{mapping.sourceColumnName}</option>)}
                      </SelectInput>
                      <Button variant="ghost" onClick={() => addDestinationRule(field)}>Set Constant</Button>
                    </div>
                    {mapped?.mappingMode === 'CONSTANT' && <div className="mt-2 text-[10px] font-bold uppercase tracking-wider text-brand-blue">Destination-only rule</div>}
                  </td>
                  <td className="px-3 py-3"><StatusPill tone={mapped ? 'green' : 'red'}>{mapped ? 'Complete' : 'Missing'}</StatusPill></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {requiredMissing.length === 0 && <AlertBox tone="green">All required destination fields are mapped.</AlertBox>}
    </section>
  );
}

function SelfServiceMappingGrid({ mappings, setMappings, hasHeaderRow, samplePreview, requiredMissing, conversionIssues = [], targetFields = TARGET_FIELDS, addDestinationRule }) {
  const [expanded, setExpanded] = useState({});
  const update = (index, patch) => setMappings(mappings.map((mapping, i) => (i === index ? { ...mapping, ...patch } : mapping)));
  const missingRequiredValues = requiredMissing.map((field) => field.value);
  const issueIndexes = new Set(conversionIssues.map((issue) => issue.sourceColumnIndex));
  const mapped = mappings.filter((mapping) => mapping.targetFieldName);
  const unboundDestinations = targetFields.filter((field) => !mapped.some((mapping) => mapping.targetFieldName === field.value));
  return (
    <section className="overflow-hidden rounded-xl border border-slate-800">
      <div className="border-b border-slate-800 bg-slate-950/70 p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="m-0 text-sm font-bold">Value Rules</h3>
            <p className="m-0 mt-1 text-xs text-slate-500">Mapped fields use safe defaults. Expand a row only when a field needs cleaning, defaults, constants, or failure handling.</p>
          </div>
          <StatusPill tone={hasHeaderRow ? 'blue' : 'amber'}>{hasHeaderRow ? 'Header row' : 'Manual names'}</StatusPill>
        </div>
        {unboundDestinations.length > 0 && (
          <div className="mt-4 grid gap-2 md:grid-cols-[minmax(0,1fr)_auto] md:items-end">
            <Field label="Set destination without a source">
              <SelectInput defaultValue="" onChange={(event) => {
                const field = targetFields.find((item) => item.value === event.target.value);
                if (field) addDestinationRule(field);
                event.target.value = '';
              }}>
                <option value="">Choose destination field</option>
                {unboundDestinations.map((field) => <option key={field.value} value={field.value}>{field.label}{field.required ? ' *' : ''}</option>)}
              </SelectInput>
            </Field>
            <div className="pb-2 text-xs text-slate-500">Creates a constant/default destination rule.</div>
          </div>
        )}
      </div>
      <table className="w-full min-w-[920px] text-left text-xs">
        <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
          <tr>
            <th className="px-3 py-3">Source</th>
            <th className="px-3 py-3">Destination</th>
            <th className="px-3 py-3">Output preview</th>
            <th className="px-3 py-3">Status</th>
            <th className="px-3 py-3 text-right">Rules</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800">
          {mapped.length === 0 && <tr><td colSpan="5" className="px-4 py-10 text-center text-slate-500">Map at least one source column before configuring rules.</td></tr>}
          {mapped.map((mapping) => {
            const index = mappings.findIndex((item) => item.sourceColumnIndex === mapping.sourceColumnIndex);
            const field = targetFields.find((item) => item.value === mapping.targetFieldName);
            const destinationOnly = mapping.mappingMode === 'CONSTANT' || (mapping.sourceColumnIndex === null && !mapping.sourceColumnName);
            const firstValue = destinationOnly ? '' : samplePreview.firstDataRow?.[index];
            const previewValue = applyMappingPreview(firstValue, mapping);
            const rowKey = mappingKey(mapping);
            const open = Boolean(expanded[rowKey]);
            const hasConversionIssue = issueIndexes.has(mapping.sourceColumnIndex);
            const mapsMissingRequired = missingRequiredValues.includes(mapping.targetFieldName);
            return (
              <Fragment key={rowKey}>
                <tr className={`bg-slate-900/50 ${mapsMissingRequired || hasConversionIssue ? 'outline outline-1 outline-amber-500/60' : ''}`}>
                  <td className="px-3 py-3">
                    <div className="font-semibold">{destinationOnly ? 'Destination-only' : mapping.sourceColumnName}</div>
                    <div className="mt-1 max-w-56 truncate text-slate-500">{destinationOnly ? 'No source column used' : firstValue || 'No sample value'}</div>
                  </td>
                  <td className="px-3 py-3">
                    <SelectInput value={mapping.targetFieldName || ''} onChange={(e) => {
                      const nextField = targetFields.find((item) => item.value === e.target.value);
                      const targetDataType = nextField?.type || 'STRING';
                      const conversionType = recommendConversion(targetDataType, mapping.sourceDataType || 'STRING');
                      update(index, {
                        targetFieldName: e.target.value,
                        targetDataType,
                        conversionType,
                        conversionAutoSelected: conversionType !== 'NONE',
                        conversionErrorMode: 'FAIL_ROW',
                        isNullable: false,
                      });
                    }}>
                      <option value="">Unmapped</option>
                      {targetFields.map((fieldOption) => <option key={fieldOption.value} value={fieldOption.value}>{fieldOption.label}{fieldOption.required ? ' *' : ''}</option>)}
                    </SelectInput>
                  </td>
                  <td className="px-3 py-3">
                    <div className="max-w-64 truncate font-semibold text-brand-blue">{previewValue === null || previewValue === undefined || previewValue === '' ? 'NULL / empty' : String(previewValue)}</div>
                    <div className="mt-1 max-w-64 truncate text-[10px] text-slate-500">{describeMappingRule(mapping)}</div>
                  </td>
                  <td className="px-3 py-3">
                    <StatusPill tone={hasConversionIssue ? 'amber' : 'green'}>{hasConversionIssue ? 'Review conversion' : field?.type || 'Mapped'}</StatusPill>
                  </td>
                  <td className="px-3 py-3 text-right">
                    <Button variant="ghost" onClick={() => setExpanded((current) => ({ ...current, [rowKey]: !open }))}>
                      {open ? 'Hide Rules' : 'Configure Rules'}
                    </Button>
                  </td>
                </tr>
                {open && (
                  <tr className="bg-slate-950/80">
                    <td colSpan="5" className="px-3 py-4">
                      <div className="grid gap-4 lg:grid-cols-3">
                        <div className="rounded-lg border border-slate-800 bg-slate-900 p-3">
                          <div className="mb-3 text-[10px] font-bold uppercase tracking-wider text-slate-500">Clean value</div>
                          <div className="flex flex-wrap gap-2">
                            {TRANSFORMS.map((transform) => {
                              const active = (mapping.transformations || []).some((t) => t.action === transform);
                              return (
                                <button
                                  key={transform}
                                  type="button"
                                  onClick={() => {
                                    const transformations = active
                                      ? (mapping.transformations || []).filter((t) => t.action !== transform)
                                      : [...(mapping.transformations || []), { action: transform }];
                                    update(index, { transformations });
                                  }}
                                  className={`rounded border px-2 py-1 text-[10px] font-bold ${active ? 'border-brand-pink bg-brand-pink text-white' : 'border-slate-800 bg-slate-950 text-slate-400 hover:text-white'}`}
                                >
                                  {transform}
                                </button>
                              );
                            })}
                          </div>
                        </div>
                        <EmptySourceControls mapping={mapping} update={(patch) => update(index, patch)} />
                        <div className="rounded-lg border border-slate-800 bg-slate-900 p-3">
                          <div className="mb-3 text-[10px] font-bold uppercase tracking-wider text-slate-500">Convert and handle failures</div>
                          <div className="grid gap-2">
                            <SelectInput value={mapping.conversionType || 'NONE'} onChange={(e) => update(index, { conversionType: e.target.value, conversionAutoSelected: false })}>
                              {CONVERSION_TYPES.map((type) => <option key={type}>{type}</option>)}
                            </SelectInput>
                            {mapping.conversionType && mapping.conversionType !== 'NONE' ? (
                              <ConversionFailureControls mapping={mapping} update={(patch) => update(index, patch)} />
                            ) : (
                              <div className="rounded border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-slate-500">Conversion failure handling is available when conversion is enabled.</div>
                            )}
                          </div>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}

function TemplateTestStep({ previewResult, previewing, runPreview, requiredMissing, conversionIssues, mappings }) {
  return (
    <section className="space-y-5">
      <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="m-0 text-sm font-bold">Test With Sample</h3>
            <p className="m-0 mt-1 text-xs text-slate-500">Run the saved sample through the exact backend mapping engine used during ingestion.</p>
          </div>
          <Button variant="gradient" onClick={runPreview} disabled={previewing || mappings.length === 0}>
            <Eye className="h-4 w-4" /> {previewing ? 'Testing...' : 'Test Sample'}
          </Button>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <MetricCard label="Required Missing" value={requiredMissing.length} tone={requiredMissing.length ? 'red' : 'green'} />
          <MetricCard label="Conversion Issues" value={conversionIssues.length} tone={conversionIssues.length ? 'amber' : 'green'} />
          <MetricCard label="Mapped Fields" value={mappings.filter((mapping) => mapping.targetFieldName).length} />
        </div>
      </div>
      {previewResult ? <BackendPreviewPanel preview={previewResult} /> : <AlertBox tone="amber">Run Test Sample before saving an enabled template.</AlertBox>}
    </section>
  );
}

function TemplateFinalStep({
  mappedCount,
  requiredMissing,
  unmappedSource,
  optionalDestinationMissing,
  conversionIssues,
  previewResult,
  canSaveReady,
  openSaveReview,
}) {
  return (
    <section className="space-y-5">
      <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
        <h3 className="m-0 text-sm font-bold">Save Summary</h3>
        <div className="mt-4 grid gap-3 md:grid-cols-4">
          <MetricCard label="Mapped Fields" value={mappedCount} tone="green" />
          <MetricCard label="Ignored Source" value={unmappedSource.length} tone={unmappedSource.length ? 'amber' : 'green'} />
          <MetricCard label="Required Missing" value={requiredMissing.length} tone={requiredMissing.length ? 'red' : 'green'} />
          <MetricCard label="Sample Test" value={previewResult ? `${previewResult.passedRows || 0}/${previewResult.testedRows || 0}` : 'Phase 2'} tone="slate" />
        </div>
        {conversionIssues.length > 0 && <div className="mt-4"><AlertBox tone="amber">Review conversion rules before enabling this template.</AlertBox></div>}
        {optionalDestinationMissing.length > 0 && (
          <div className="mt-4 rounded-lg border border-slate-800 bg-slate-900 p-3">
            <div className="mb-2 text-[10px] font-bold uppercase tracking-wider text-slate-500">Optional destination fields left blank</div>
            <div className="flex max-h-24 flex-wrap gap-2 overflow-y-auto">
              {optionalDestinationMissing.map((field) => <StatusPill key={field.value} tone="slate">{field.label}</StatusPill>)}
            </div>
          </div>
        )}
      </div>
      <div className="flex flex-wrap gap-2">
        <Button variant="gradient" disabled={!canSaveReady} onClick={() => openSaveReview(false)}>Save Enabled Template</Button>
        <Button variant="warn" onClick={() => openSaveReview(true)}>Save Draft</Button>
      </div>
    </section>
  );
}

function TemplateSaveReviewDialog({
  review,
  setReview,
  mappedCount,
  defaultValueCount,
  forceValueCount,
  conversionCount,
  requiredMissing,
  unmappedSource,
  optionalDestinationMissing,
  templateEnabled,
  saving,
  error,
  onCancel,
  onConfirm,
}) {
  const requiresIgnoreAcknowledgement = !review.draft && unmappedSource.length > 0;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur-sm">
      <section className="w-full max-w-3xl rounded-xl border border-slate-800 bg-slate-900 p-5 shadow-2xl">
        <div className="mb-5 flex items-start gap-3">
          <AlertCircle className={`mt-0.5 h-5 w-5 ${review.draft ? 'text-amber-300' : 'text-brand-blue'}`} />
          <div>
            <h3 className="m-0 text-base font-bold text-white">{review.draft ? 'Save Template as Draft' : 'Review Template Before Saving'}</h3>
            <p className="m-0 mt-2 text-sm leading-6 text-slate-300">
              {review.draft
                ? 'Required destination fields are missing. This template will be saved disabled and cannot be used for uploads until those fields are mapped.'
                : 'Review what will be mapped, ignored, and left blank before this template is saved.'}
            </p>
          </div>
        </div>

        <div className="grid gap-3 md:grid-cols-4">
          <MetricCard label="Mapped Columns" value={mappedCount} />
          <MetricCard label="Defaults" value={defaultValueCount} tone="amber" />
          <MetricCard label="Always Set" value={forceValueCount} tone="blue" />
          <MetricCard label="Conversions" value={conversionCount} tone="green" />
        </div>

        {requiredMissing.length > 0 && (
          <div className="mt-4 rounded-lg border border-red-500/30 bg-red-500/10 p-3">
            <div className="mb-2 text-xs font-bold text-red-200">Required destination fields missing</div>
            <div className="flex flex-wrap gap-2">
              {requiredMissing.map((field) => <StatusPill key={field.value} tone="red">{field.label}</StatusPill>)}
            </div>
          </div>
        )}

        {unmappedSource.length > 0 && (
          <div className="mt-4 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3">
            <div className="mb-2 text-xs font-bold text-amber-200">Uploaded file columns not mapped</div>
            <p className="m-0 mb-3 text-xs leading-5 text-amber-100">These source columns will be ignored by ingestion for this template.</p>
            <div className="max-h-28 overflow-y-auto">
              <div className="flex flex-wrap gap-2">
                {unmappedSource.map((mapping) => <StatusPill key={`${mapping.sourceColumnIndex}-${mapping.sourceColumnName}`} tone="amber">{mapping.sourceColumnName}</StatusPill>)}
              </div>
            </div>
          </div>
        )}

        {optionalDestinationMissing.length > 0 && (
          <div className="mt-4 rounded-lg border border-slate-800 bg-slate-950/70 p-3">
            <div className="mb-2 text-xs font-bold text-slate-300">Optional destination fields left blank</div>
            <div className="max-h-28 overflow-y-auto">
              <div className="flex flex-wrap gap-2">
                {optionalDestinationMissing.map((field) => <StatusPill key={field.value} tone="slate">{field.label}</StatusPill>)}
              </div>
            </div>
          </div>
        )}

        {!review.draft && unmappedSource.length > 0 && (
          <div className="mt-4 space-y-3 rounded-lg border border-slate-800 bg-slate-950/70 p-3">
            <label className="flex items-start gap-2 text-sm text-slate-200">
              <input
                type="checkbox"
                checked={review.acknowledgeIgnoredSources}
                onChange={(event) => setReview((prev) => ({ ...prev, acknowledgeIgnoredSources: event.target.checked }))}
                className="mt-1"
              />
              <span>I understand the unmapped uploaded file columns listed above will be ignored for this template.</span>
            </label>
            <Field label="Reason for ignoring source columns (optional)">
              <TextArea
                rows={3}
                value={review.ignoreSourceComment}
                onChange={(event) => setReview((prev) => ({ ...prev, ignoreSourceComment: event.target.value }))}
                placeholder="Example: These columns are vendor metadata and are not needed for active vulnerability records."
              />
            </Field>
          </div>
        )}

        <div className="mt-4 rounded-lg border border-slate-800 bg-slate-950/70 p-3 text-sm text-slate-300">
          Template status after save: <span className="font-bold text-white">{review.draft ? 'Disabled draft' : templateEnabled ? 'Enabled' : 'Disabled'}</span>
        </div>

        {requiresIgnoreAcknowledgement && !review.acknowledgeIgnoredSources && (
          <div className="mt-4 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-100">
            Acknowledgement is mandatory when uploaded source columns are ignored. The reason/comment is optional.
          </div>
        )}

        {error && <div className="mt-4"><AlertBox tone="red">{error}</AlertBox></div>}

        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={onCancel} disabled={saving}>Cancel</Button>
          <Button variant={review.draft ? 'warn' : 'gradient'} onClick={onConfirm} disabled={saving}>
            {saving ? 'Saving...' : review.draft ? 'Save Draft' : 'Confirm Save'}
          </Button>
        </div>
      </section>
    </div>
  );
}

function BackendPreviewPanel({ preview }) {
  const rows = preview.rows || [];
  const failedRows = rows.filter((row) => row.status === 'FAILED');
  const visibleRows = (failedRows.length ? failedRows : rows).slice(0, 5);
  return (
    <section className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="m-0 text-sm font-bold">Backend Sample Test</h3>
          <p className="m-0 mt-1 text-xs text-slate-500">This uses the same compiled mapping engine as full ingestion, limited to sample rows.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusPill tone="blue">{preview.testedRows || 0} tested</StatusPill>
          <StatusPill tone="green">{preview.passedRows || 0} passed</StatusPill>
          <StatusPill tone={(preview.failedRows || 0) ? 'red' : 'green'}>{preview.failedRows || 0} failed</StatusPill>
        </div>
      </div>
      <div className="mt-4 overflow-hidden rounded-lg border border-slate-800">
        <table className="w-full min-w-[720px] text-left text-xs">
          <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
            <tr>
              <th className="px-3 py-2">Row</th>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2">Details</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {visibleRows.length === 0 && <tr><td colSpan="3" className="px-3 py-6 text-center text-slate-500">No preview rows returned.</td></tr>}
            {visibleRows.map((row) => (
              <tr key={row.rowNumber} className="bg-slate-900/50">
                <td className="px-3 py-2 font-mono text-slate-400">{row.rowNumber}</td>
                <td className="px-3 py-2"><StatusPill tone={row.status === 'PASSED' ? 'green' : 'red'}>{row.status}</StatusPill></td>
                <td className="px-3 py-2 text-slate-300">
                  {row.status === 'FAILED' ? row.error : `${(row.fields || []).filter((field) => field.status === 'OK').length} mapped fields tested`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function ConversionFailureControls({ mapping, update }) {
  const mode = mapping.conversionErrorMode || 'FAIL_ROW';
  const emptyPolicy = normalizeEmptySourcePolicy(mapping.emptySourcePolicy, mapping.defaultValue);
  const disableNullFallback = emptyPolicy.mode === 'USE_DEFAULT';
  const groupName = `conversion-${mappingKey(mapping)}`;
  const setMode = (conversionErrorMode, extra = {}) => {
    if (conversionErrorMode === 'SET_NULL' && disableNullFallback) return;
    update({
      conversionErrorMode,
      isNullable: conversionErrorMode === 'SET_NULL',
      ...extra,
    });
  };

  return (
    <div className="space-y-2">
      <label className="flex items-center gap-2 text-xs text-slate-300"><input type="radio" name={groupName} checked={mode === 'FAIL_ROW'} onChange={() => setMode('FAIL_ROW', { conversionErrorValue: '' })} /> Reject row</label>
      <label className={`flex items-center gap-2 text-xs ${disableNullFallback ? 'text-slate-600' : 'text-slate-300'}`}><input type="radio" name={groupName} checked={mode === 'SET_NULL'} disabled={disableNullFallback} onChange={() => setMode('SET_NULL', { conversionErrorValue: '' })} /> Set blank/null</label>
      <label className="flex items-center gap-2 text-xs text-slate-300"><input type="radio" name={groupName} checked={mode === 'SET_CUSTOM'} onChange={() => setMode('SET_CUSTOM')} /> Use fallback</label>
      <TextInput
        value={mode === 'SET_CUSTOM' ? mapping.conversionErrorValue || '' : ''}
        onChange={(e) => setMode(e.target.value ? 'SET_CUSTOM' : 'FAIL_ROW', { conversionErrorValue: e.target.value })}
        placeholder="Fallback value"
        disabled={mode !== 'SET_CUSTOM'}
      />
      <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
        {disableNullFallback && mode === 'SET_NULL' ? 'Default handling is active; choose reject row or fallback for conversion failures.' : failureModeLabel(mode)}
      </div>
    </div>
  );
}

function EmptySourceControls({ mapping, update }) {
  const policy = normalizeEmptySourcePolicy(mapping.emptySourcePolicy, mapping.defaultValue);
  const groupName = `empty-${mappingKey(mapping)}`;
  const setPolicy = (mode, patch = {}) => {
    update({
      emptySourcePolicy: {
        mode,
        defaultValue: mode === 'USE_DEFAULT' ? (patch.defaultValue ?? policy.defaultValue ?? '') : '',
      },
      defaultValue: mode === 'USE_DEFAULT' ? (patch.defaultValue ?? policy.defaultValue ?? '') : '',
      forceValue: mapping.mappingMode === 'CONSTANT' && mode === 'USE_DEFAULT' ? (patch.defaultValue ?? policy.defaultValue ?? '') : mapping.forceValue,
      conversionErrorMode: mode === 'USE_DEFAULT' && mapping.conversionErrorMode === 'SET_NULL' ? 'FAIL_ROW' : mapping.conversionErrorMode,
    });
  };
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 p-3">
      <div className="mb-3 text-[10px] font-bold uppercase tracking-wider text-slate-500">When source is empty</div>
      <div className="space-y-2">
        <label className="flex items-center gap-2 text-xs text-slate-300"><input type="radio" name={groupName} checked={policy.mode === 'LEAVE_EMPTY'} onChange={() => setPolicy('LEAVE_EMPTY')} /> Leave destination empty</label>
        <label className="flex items-center gap-2 text-xs text-slate-300"><input type="radio" name={groupName} checked={policy.mode === 'SET_NULL'} onChange={() => setPolicy('SET_NULL')} /> Set destination NULL</label>
        <label className="flex items-center gap-2 text-xs text-slate-300"><input type="radio" name={groupName} checked={policy.mode === 'USE_DEFAULT'} onChange={() => setPolicy('USE_DEFAULT')} /> Use default value</label>
        <TextInput
          value={policy.mode === 'USE_DEFAULT' ? policy.defaultValue || '' : ''}
          onChange={(event) => setPolicy('USE_DEFAULT', { defaultValue: event.target.value })}
          placeholder="Default value"
          disabled={policy.mode !== 'USE_DEFAULT'}
        />
        <label className="flex items-center gap-2 text-xs text-slate-300"><input type="radio" name={groupName} checked={policy.mode === 'FAIL_ROW'} onChange={() => setPolicy('FAIL_ROW')} /> Reject row</label>
        <TextInput value={mapping.forceValue || ''} onChange={(e) => update({ forceValue: e.target.value })} placeholder="Always use this value" />
      </div>
    </div>
  );
}

function MapperSummary({ mappings, requiredMissing, unmappedSource, unmappedDestination, conversionIssues = [] }) {
  const mapped = mappings.filter((mapping) => mapping.targetFieldName);
  const defaultRules = mappings.filter((mapping) => {
    const policy = normalizeEmptySourcePolicy(mapping.emptySourcePolicy, mapping.defaultValue);
    return policy.mode === 'USE_DEFAULT' && hasText(policy.defaultValue);
  });
  const forceRules = mappings.filter((mapping) => hasText(mapping.forceValue));
  const conversionRules = mappings.filter((mapping) => mapping.conversionType && mapping.conversionType !== 'NONE');
  return (
    <section className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
      <h3 className="m-0 text-sm font-bold">Mapping Summary</h3>
      <div className="mt-4 grid grid-cols-2 gap-3">
        <MetricCard label="Mapped" value={mapped.length} tone="green" />
        <MetricCard label="Unmapped Source" value={unmappedSource.length} tone={unmappedSource.length ? 'amber' : 'green'} />
        <MetricCard label="Required Missing" value={requiredMissing.length} tone={requiredMissing.length ? 'red' : 'green'} />
        <MetricCard label="Conversions" value={conversionRules.length} tone={conversionIssues.length ? 'amber' : 'blue'} />
      </div>
      {(defaultRules.length > 0 || forceRules.length > 0) && (
        <div className="mt-4 grid grid-cols-2 gap-3">
          <MetricCard label="Default Rules" value={defaultRules.length} tone="green" />
          <MetricCard label="Always Set Rules" value={forceRules.length} tone="amber" />
        </div>
      )}
      {unmappedSource.length > 0 && (
        <div className="mt-4">
          <div className="mb-2 text-[10px] font-bold uppercase tracking-wider text-amber-300">Source columns not mapped</div>
          <div className="max-h-32 space-y-1 overflow-y-auto">
            {unmappedSource.map((mapping) => (
              <div key={`${mapping.sourceColumnIndex}-${mapping.sourceColumnName}`} className="rounded border border-slate-800 bg-slate-900 px-2 py-1 text-xs text-slate-300">
                {mapping.sourceColumnName}
              </div>
            ))}
          </div>
        </div>
      )}
      {requiredMissing.length > 0 && (
        <div className="mt-4 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-xs text-red-200">
          Required destination missing: {requiredMissing.map((field) => field.label).join(', ')}
        </div>
      )}
      {conversionIssues.length > 0 && (
        <div className="mt-4 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-200">
          Conversion rules need attention: {conversionIssues.map((issue) => issue.sourceColumnName).join(', ')}
        </div>
      )}
      {unmappedDestination.length > 0 && (
        <div className="mt-4">
          <div className="mb-2 text-[10px] font-bold uppercase tracking-wider text-slate-500">Destination fields not filled</div>
          <div className="max-h-40 space-y-1 overflow-y-auto">
            {unmappedDestination.map((field) => (
              <div key={field.value} className={`rounded border px-2 py-1 text-xs ${field.required ? 'border-red-500/30 bg-red-500/10 text-red-200' : 'border-slate-800 bg-slate-900 text-slate-400'}`}>
                {field.label}
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}

function SchemaReference({ mappedFields = [], targetFields = TARGET_FIELDS }) {
  return (
    <aside className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
      <h3 className="m-0 text-sm font-bold">PostgreSQL Target Schema</h3>
      <p className="m-0 mt-1 text-xs text-slate-500">Only active vulnerability fields are mappable here. Upload history fields are generated by the system during ingestion.</p>
      <div className="mt-4">
        <div className="mb-2 text-[10px] font-bold uppercase tracking-wider text-brand-blue">Active Records</div>
        <div className="max-h-64 space-y-2 overflow-y-auto pr-1">
          {targetFields.map((field) => (
            <div key={field.value} className={`rounded-lg border px-3 py-2 ${field.required && !mappedFields.includes(field.value) ? 'border-amber-500/50 bg-amber-500/10' : mappedFields.includes(field.value) ? 'border-green-500/30 bg-green-500/10' : 'border-slate-800 bg-slate-900'}`}>
              <div className="flex items-center justify-between gap-2">
                <span className="font-mono text-xs text-slate-200">{field.value}</span>
                <StatusPill tone={mappedFields.includes(field.value) ? 'green' : field.required ? 'amber' : 'slate'}>{mappedFields.includes(field.value) ? 'Mapped' : field.required ? 'Required' : field.type}</StatusPill>
              </div>
              <div className="mt-1 text-xs text-slate-500">{field.label}</div>
            </div>
          ))}
        </div>
      </div>
    </aside>
  );
}

function UserAccessModal({ user, customers, onClose, onDone, fail, onDirtyChange }) {
  const [checkedIds, setCheckedIds] = useState((user.allowedCustomers || []).map((c) => c.id));
  const [query, setQuery] = useState('');
  const initialCheckedKey = useMemo(() => [...(user.allowedCustomers || []).map((c) => c.id)].sort().join('|'), [user.allowedCustomers]);
  const currentCheckedKey = useMemo(() => [...checkedIds].sort().join('|'), [checkedIds]);
  const accessDirty = currentCheckedKey !== initialCheckedKey;
  const normalizedQuery = query.trim().toLowerCase();
  const visibleCustomers = customers.filter((customer) => (
    !normalizedQuery || customer.customerName.toLowerCase().includes(normalizedQuery)
  ));
  const assignedCustomers = customers.filter((customer) => checkedIds.includes(customer.id));
  const toggleCustomer = (customerId) => {
    setCheckedIds((current) => (
      current.includes(customerId) ? current.filter((id) => id !== customerId) : [...current, customerId]
    ));
  };
  useEffect(() => {
    onDirtyChange?.(accessDirty);
  }, [accessDirty, onDirtyChange]);
  return (
    <InteractionPage title={`Customer Access: ${user.fullName}`} subtitle="Manage assigned customers for this operator." icon={<Users className="h-5 w-5 text-brand-blue" />} onBack={onClose}>
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <section className="rounded-xl border border-slate-800 bg-slate-900/70 p-4">
          <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <h3 className="m-0 text-sm font-bold">Customer List</h3>
              <p className="m-0 mt-1 text-xs text-slate-500">{checkedIds.length} of {customers.length} customers assigned</p>
            </div>
            <div className="relative md:w-72">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
              <TextInput value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search customers" className="pl-9" />
            </div>
          </div>
          <div className="overflow-hidden rounded-xl border border-slate-800">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-950 text-[10px] uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3">Customer</th>
                  <th className="px-4 py-3">Access</th>
                  <th className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {visibleCustomers.length === 0 && <tr><td colSpan="3" className="px-4 py-10 text-center text-slate-500">No customers match the search.</td></tr>}
                {visibleCustomers.map((customer) => {
                  const assigned = checkedIds.includes(customer.id);
                  return (
                    <tr key={customer.id} className="hover:bg-slate-950/50">
                      <td className="px-4 py-3 font-semibold">{customer.customerName}</td>
                      <td className="px-4 py-3"><StatusPill tone={assigned ? 'green' : 'slate'}>{assigned ? 'Assigned' : 'Not Assigned'}</StatusPill></td>
                      <td className="px-4 py-3 text-right">
                        <Button variant={assigned ? 'danger' : 'ghost'} onClick={() => toggleCustomer(customer.id)}>
                          {assigned ? 'Remove' : 'Add'}
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
        <aside className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
          <h3 className="m-0 text-sm font-bold">Assigned Customers</h3>
          <p className="m-0 mt-1 text-xs text-slate-500">Total assigned: {assignedCustomers.length}</p>
          <div className="mt-4 max-h-80 space-y-2 overflow-y-auto pr-1">
            {assignedCustomers.length === 0 && <div className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm text-slate-500">No customers assigned.</div>}
            {assignedCustomers.map((customer) => (
              <div key={customer.id} className="flex items-center justify-between gap-2 rounded-lg border border-slate-800 bg-slate-900 px-3 py-2">
                <span className="min-w-0 truncate text-sm font-semibold">{customer.customerName}</span>
                <Button variant="ghost" onClick={() => toggleCustomer(customer.id)}>Remove</Button>
              </div>
            ))}
          </div>
          <Button variant="gradient" className="mt-4 w-full" onClick={async () => {
            try {
              await api.updateUserAccess(user.id, checkedIds);
              await onDone();
            } catch (err) { fail(err); }
          }}>Save Access Matrix</Button>
        </aside>
      </div>
    </InteractionPage>
  );
}

function CustomerAccessPreview({ user }) {
  if (user.role !== 'CUSTOMER_OPERATOR') {
    return (
      <div className="min-w-48 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-slate-300">
        <div className="font-semibold text-slate-100">All customers</div>
        <div className="mt-1 text-slate-500">Role grants global access</div>
      </div>
    );
  }

  const customers = user.allowedCustomers || [];
  const preview = customers.slice(0, 3);
  const hiddenCount = Math.max(customers.length - preview.length, 0);
  return (
    <div className="group relative min-w-56 max-w-72 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-xs">
      <div className="mb-1 font-semibold text-slate-100">{customers.length} customer{customers.length === 1 ? '' : 's'} assigned</div>
      <div className="flex flex-wrap gap-1">
        {preview.length === 0 && <span className="text-slate-500">No customers assigned</span>}
        {preview.map((customer) => (
          <span key={customer.id} className="max-w-28 truncate rounded border border-slate-700 bg-slate-900 px-2 py-0.5 text-slate-300">{customer.customerName}</span>
        ))}
        {hiddenCount > 0 && <span className="rounded border border-brand-blue/25 bg-brand-blue/10 px-2 py-0.5 text-brand-blue">+{hiddenCount}</span>}
      </div>
      {customers.length > 0 && (
        <div className="invisible absolute left-0 top-full z-40 mt-2 w-72 rounded-xl border border-slate-800 bg-slate-900 p-3 opacity-0 shadow-2xl transition group-hover:visible group-hover:opacity-100 group-focus-within:visible group-focus-within:opacity-100">
          <div className="mb-2 text-[10px] font-bold uppercase tracking-wider text-slate-500">Full Customer Access</div>
          <div className="max-h-56 space-y-1 overflow-y-auto">
            {customers.map((customer) => (
              <div key={customer.id} className="rounded border border-slate-800 bg-slate-950 px-2 py-1 text-slate-300">{customer.customerName}</div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function CheckboxList({ label, items, checkedIds, onChange }) {
  return (
    <Field label={label}>
      <div className="max-h-48 space-y-2 overflow-y-auto rounded-lg border border-slate-800 bg-slate-950 p-3">
        {items.map((item) => {
          const checked = checkedIds.includes(item.id);
          return (
            <label key={item.id} className="flex cursor-pointer items-center gap-2 text-sm text-slate-300">
              <input
                type="checkbox"
                checked={checked}
                onChange={() => onChange(checked ? checkedIds.filter((id) => id !== item.id) : [...checkedIds, item.id])}
              />
              {item.customerName}
            </label>
          );
        })}
      </div>
    </Field>
  );
}

function TabBar({ value, onChange, options }) {
  return (
    <div className="inline-flex rounded-lg border border-slate-800 bg-slate-950 p-1">
      {options.map(([id, label]) => (
        <button key={id} onClick={() => onChange(id)} className={`rounded-md px-4 py-2 text-xs font-bold transition ${value === id ? 'bg-brand-blue text-slate-950' : 'text-slate-400 hover:text-white'}`}>
          {label}
        </button>
      ))}
    </div>
  );
}

function LifecycleTabs({ value, onChange, counts }) {
  const options = [
    ['ACTIVE', `Active (${counts.active})`],
    ['ARCHIVE', `Archive (${counts.archived})`],
  ];
  return <TabBar value={value} onChange={onChange} options={options} />;
}

function StatusFilter({ value, onChange, counts }) {
  return (
    <label className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-slate-500">
      Status
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="min-w-40 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm font-semibold normal-case tracking-normal text-slate-200 outline-none transition focus:border-brand-blue"
      >
        <option value="ALL">All ({counts.all})</option>
        <option value="ENABLED">Enabled ({counts.enabled})</option>
        <option value="DISABLED">Disabled ({counts.disabled})</option>
      </select>
    </label>
  );
}

function getLifecycleCounts(records = []) {
  const activeRecords = records.filter((record) => !record.archived);
  return {
    active: activeRecords.length,
    archived: records.filter((record) => record.archived).length,
    all: activeRecords.length,
    enabled: activeRecords.filter((record) => record.enabled !== false).length,
    disabled: activeRecords.filter((record) => record.enabled === false).length,
  };
}

function matchesLifecycle(record, lifecycleTab) {
  return lifecycleTab === 'ARCHIVE' ? record.archived === true : record.archived !== true;
}

function matchesStatus(record, statusFilter) {
  if (statusFilter === 'ENABLED') return record.enabled !== false;
  if (statusFilter === 'DISABLED') return record.enabled === false;
  return true;
}

function IconButton({ title, children, onClick }) {
  return <button title={title} onClick={onClick} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white">{children}</button>;
}

function DetailBlock({ label, value }) {
  return (
    <div>
      <div className="mb-1 text-[10px] font-bold uppercase tracking-wider text-slate-500">{label}</div>
      <p className="m-0 whitespace-pre-wrap text-sm leading-6 text-slate-300">{value || 'No details available.'}</p>
    </div>
  );
}

function SeverityPill({ severity }) {
  const tone = severity === 'CRITICAL' ? 'red' : severity === 'HIGH' ? 'amber' : severity === 'MEDIUM' ? 'blue' : 'slate';
  return <StatusPill tone={tone}>{severity || 'MEDIUM'}</StatusPill>;
}

function WorkflowPill({ status }) {
  const tone = status === 'RESOLVED' ? 'green' : status === 'FALSE_POSITIVE' ? 'purple' : status === 'IN_PROGRESS' ? 'amber' : 'slate';
  return <StatusPill tone={tone}>{status.replace('_', ' ')}</StatusPill>;
}

function UploadStatusPill({ status }) {
  const tone = status === 'SUCCESS' ? 'green' : status === 'PARTIAL_FAILURE' ? 'amber' : status === 'FAILED' ? 'red' : 'blue';
  return <StatusPill tone={tone}>{(status || 'PROCESSING').replace('_', ' ')}</StatusPill>;
}

function findingHash(finding) {
  return `${finding.cveId || 'NOCVE'}-${finding.issueTitle}-${finding.cvssScore || '0'}`;
}

function pageTitle(section) {
  return {
    'vuln-dashboard': 'Vulnerability Dashboard',
    'vuln-management': 'Vulnerability Management',
    software: 'Security Software Manager',
    customers: 'Customer Management',
    users: 'User Management',
  }[section];
}

function formatDate(value) {
  if (!value) return 'N/A';
  return new Date(value).toLocaleString();
}

function formatScore(value) {
  if (value === null || value === undefined || value === '') return 'N/A';
  const number = Number(value);
  return Number.isFinite(number) ? number.toFixed(1) : value;
}

function failedRowsDownloadName(run, blob) {
  const original = run.fileName || String(run.id || 'upload');
  const base = original.replace(/\.[^.]+$/, '');
  const ext = blob?.type?.includes('spreadsheet') || /\.xlsx?$/i.test(original) ? 'xlsx' : 'csv';
  return `failed_rows_${base}.${ext}`;
}

function originalUploadDownloadName(run) {
  return run.fileName || `uploaded_scan_${run.id || 'file'}`;
}

function mappingKey(mapping) {
  return mapping.mappingId || `${mapping.sourceColumnIndex ?? 'dest'}-${mapping.targetFieldName || mapping.sourceColumnName || 'unmapped'}`;
}

function addDestinationOnlyMapping(mappings, field) {
  const existingIndex = mappings.findIndex((mapping) => mapping.targetFieldName === field.value);
  const conversionType = recommendConversion(field.type || 'STRING', 'STRING');
  const destinationMapping = {
    mappingId: `constant-${field.value}`,
    mappingMode: 'CONSTANT',
    sourceColumnIndex: null,
    sourceColumnName: '',
    sourceDataType: 'STRING',
    targetFieldName: field.value,
    targetDataType: field.type || 'STRING',
    transformations: [],
    emptySourcePolicy: { mode: 'USE_DEFAULT', defaultValue: '' },
    defaultValue: '',
    forceValue: '',
    conversionType,
    conversionErrorMode: 'FAIL_ROW',
    conversionErrorValue: '',
    conversionAutoSelected: conversionType !== 'NONE',
    isNullable: false,
  };
  if (existingIndex >= 0) {
    return mappings.map((mapping, index) => (index === existingIndex ? { ...destinationMapping, forceValue: mapping.forceValue || '' } : mapping));
  }
  return [...mappings, destinationMapping];
}

function downloadBlob(blob, fileName) {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}

function serializeSoftwareAssignments(rows = []) {
  return rows
    .filter((row) => row.assigned)
    .map((row) => `${row.software?.id}:${row.enabled ? 'enabled' : 'disabled'}`)
    .sort()
    .join('|');
}

function parseMappings(raw, targetFields = TARGET_FIELDS) {
  if (!raw) return [];
  if (Array.isArray(raw)) return normalizeMappings(raw, targetFields);
  if (raw.mappings && Array.isArray(raw.mappings)) return normalizeMappings(raw.mappings, targetFields);
  try {
    const parsed = JSON.parse(raw);
    return normalizeMappings(Array.isArray(parsed) ? parsed : parsed.mappings || [], targetFields);
  } catch {
    return [];
  }
}

function normalizeMappings(mappings, targetFields = TARGET_FIELDS) {
  return mappings.map((mapping, index) => ({
    mappingId: mapping.mappingId || '',
    mappingMode: mapping.mappingMode || (mapping.sourceColumnIndex === null ? 'CONSTANT' : 'SOURCE'),
    sourceColumnIndex: mapping.sourceColumnIndex === null ? null : mapping.sourceColumnIndex ?? index,
    sourceColumnName: mapping.sourceColumnName || `Column_${index}`,
    sourceDataType: mapping.sourceDataType || 'STRING',
    targetFieldName: mapping.targetFieldName || '',
    targetDataType: mapping.targetDataType || targetFields.find((field) => field.value === mapping.targetFieldName)?.type || 'STRING',
    transformations: sanitizeTransformations(mapping.transformations),
    emptySourcePolicy: normalizeEmptySourcePolicy(mapping.emptySourcePolicy, mapping.defaultValue),
    defaultValue: mapping.defaultValue || '',
    forceValue: mapping.forceValue || '',
    conversionType: mapping.conversionType || 'NONE',
    conversionErrorMode: normalizeFailureMode(mapping.conversionErrorMode),
    conversionErrorValue: mapping.conversionErrorValue || '',
    conversionAutoSelected: Boolean(mapping.conversionAutoSelected),
    isNullable: normalizeFailureMode(mapping.conversionErrorMode) === 'SET_NULL',
  }));
}

function buildDraftMappings(columns, existingMappings = [], targetFields = TARGET_FIELDS) {
  return columns.map((column, index) => {
    const existing = existingMappings.find((mapping) => (
      mapping.sourceColumnIndex === index || mapping.sourceColumnName === column
    ));
    return {
      sourceColumnIndex: index,
      mappingId: existing?.mappingId || '',
      mappingMode: existing?.mappingMode || 'SOURCE',
      sourceColumnName: column || `Column_${index}`,
      sourceDataType: existing?.sourceDataType || 'STRING',
      targetFieldName: existing?.targetFieldName || '',
      targetDataType: existing?.targetDataType || targetFields.find((field) => field.value === existing?.targetFieldName)?.type || 'STRING',
      transformations: sanitizeTransformations(existing?.transformations),
      emptySourcePolicy: normalizeEmptySourcePolicy(existing?.emptySourcePolicy, existing?.defaultValue),
      defaultValue: existing?.defaultValue || '',
      forceValue: existing?.forceValue || '',
      conversionType: existing?.conversionType || 'NONE',
      conversionErrorMode: normalizeFailureMode(existing?.conversionErrorMode),
      conversionErrorValue: existing?.conversionErrorValue || '',
      conversionAutoSelected: Boolean(existing?.conversionAutoSelected),
      isNullable: normalizeFailureMode(existing?.conversionErrorMode) === 'SET_NULL',
    };
  });
}

function isTemplateDirty(initial, form) {
  const initialState = {
    id: initial.id || null,
    name: initial.name || '',
    description: initial.description || '',
    softwareId: initial.software?.id || initial.softwareId || '',
    customerId: initial.customer?.id || initial.customerId || '',
    fileFormat: initial.fileFormat || 'CSV',
    hasHeaderRow: initial.hasHeaderRow ?? true,
    enabled: initial.enabled ?? true,
    mappings: parseMappings(initial.columnMappingJson),
    hasSampleFile: false,
  };
  const currentState = {
    id: form.id || null,
    name: form.name || '',
    description: form.description || '',
    softwareId: form.softwareId || '',
    customerId: form.customerId || '',
    fileFormat: form.fileFormat || 'CSV',
    hasHeaderRow: form.hasHeaderRow ?? true,
    enabled: form.enabled ?? true,
    mappings: normalizeMappings(form.mappings || []),
    hasSampleFile: Boolean(form.sampleFile),
  };
  return JSON.stringify(initialState) !== JSON.stringify(currentState);
}

function autoMapMappings(mappings, targetFields = TARGET_FIELDS) {
  const usedTargets = new Set(mappings.map((mapping) => mapping.targetFieldName).filter(Boolean));
  let count = 0;
  let conversionCount = 0;
  const next = mappings.map((mapping) => {
    if (mapping.targetFieldName) return mapping;
    const match = findTargetFieldMatch(mapping.sourceColumnName, usedTargets, targetFields);
    if (!match) return mapping;
    usedTargets.add(match.value);
    count += 1;
    const conversionType = recommendConversion(match.type, mapping.sourceDataType || 'STRING');
    if (conversionType !== 'NONE') conversionCount += 1;
    return {
      ...mapping,
      targetFieldName: match.value,
      targetDataType: match.type,
      conversionType,
      conversionErrorMode: 'FAIL_ROW',
      conversionErrorValue: '',
      emptySourcePolicy: normalizeEmptySourcePolicy(mapping.emptySourcePolicy, mapping.defaultValue),
      isNullable: false,
      conversionAutoSelected: conversionType !== 'NONE',
      autoMapped: true,
    };
  });
  return { mappings: next, count, conversionCount };
}

function findTargetFieldMatch(sourceName, usedTargets = new Set(), targetFields = TARGET_FIELDS) {
  const normalizedSource = normalizeColumnName(sourceName);
  if (!normalizedSource) return null;
  const candidates = targetFields.map((field) => {
    const names = [field.value, field.label, ...(FIELD_ALIASES[field.value] || [])].map(normalizeColumnName);
    let score = 0;
    names.forEach((name) => {
      if (normalizedSource === name) score = Math.max(score, 100);
      else if (normalizedSource.includes(name) || name.includes(normalizedSource)) score = Math.max(score, 80);
      else if (tokenOverlapScore(normalizedSource, name) >= 0.7) score = Math.max(score, 70);
    });
    return { field, score };
  })
    .filter(({ field, score }) => score >= 70 && !usedTargets.has(field.value))
    .sort((a, b) => b.score - a.score);
  return candidates[0]?.field || null;
}

const FIELD_ALIASES = {
  issue_title: ['title', 'plugin name', 'finding', 'vulnerability', 'vulnerability name', 'issue', 'name'],
  severity: ['risk', 'risk level', 'threat', 'priority'],
  cvss_score: ['cvss', 'cvss base score', 'base score', 'score'],
  cvss_vector: ['vector', 'cvss vector'],
  cve_id: ['cve', 'cves', 'cve id', 'cve ids'],
  oid: ['plugin id', 'qid', 'vulnerability id', 'oid'],
  summary: ['description', 'synopsis', 'summary'],
  impact: ['risk description', 'business impact', 'impact'],
  solution: ['remediation', 'recommendation', 'fix', 'solution'],
  vulnerability_insight: ['insight', 'vulnerability insight', 'details'],
  vulnerability_detection_result: ['result', 'detection result', 'output', 'evidence'],
  vulnerability_detection_method: ['detection method', 'method', 'check type'],
  affected_devices: ['host', 'hosts', 'asset', 'assets', 'ip', 'ip address', 'hostname', 'device'],
  number_of_devices: ['device count', 'host count', 'asset count', 'count'],
  references_info: ['references', 'reference', 'links', 'see also'],
  known_exploited: ['kev', 'known exploited', 'exploited'],
  known_ransomware_campaign: ['ransomware', 'ransomware campaign'],
  last_detected_at: ['last detected', 'last seen', 'detected date', 'last observed'],
};

function normalizeColumnName(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[_\-./()[\]]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function tokenOverlapScore(a, b) {
  const aTokens = new Set(a.split(' ').filter(Boolean));
  const bTokens = new Set(b.split(' ').filter(Boolean));
  if (!aTokens.size || !bTokens.size) return 0;
  const overlap = [...aTokens].filter((token) => bTokens.has(token)).length;
  return overlap / Math.max(aTokens.size, bTokens.size);
}

function hasText(value) {
  return value !== null && value !== undefined && String(value).trim() !== '';
}

function sanitizeTransformations(transformations = []) {
  return (transformations || [])
    .filter((transform) => TRANSFORMS.includes(transform.action))
    .map((transform) => ({ action: transform.action }));
}

function normalizeFailureMode(mode) {
  return ['FAIL_ROW', 'SET_NULL', 'SET_EMPTY', 'SET_CUSTOM'].includes(mode) ? mode : 'FAIL_ROW';
}

function normalizeEmptySourceMode(mode) {
  return ['LEAVE_EMPTY', 'SET_NULL', 'USE_DEFAULT', 'FAIL_ROW'].includes(mode) ? mode : 'LEAVE_EMPTY';
}

function normalizeEmptySourcePolicy(policy, legacyDefaultValue = '') {
  if (policy && typeof policy === 'object') {
    return {
      mode: normalizeEmptySourceMode(policy.mode),
      defaultValue: policy.defaultValue || '',
    };
  }
  if (hasText(legacyDefaultValue)) {
    return { mode: 'USE_DEFAULT', defaultValue: legacyDefaultValue };
  }
  return { mode: 'LEAVE_EMPTY', defaultValue: '' };
}

function toMappingPayload(mapping, targetFields = TARGET_FIELDS) {
  const conversionErrorMode = normalizeFailureMode(mapping.conversionErrorMode);
  const emptySourcePolicy = normalizeEmptySourcePolicy(mapping.emptySourcePolicy, mapping.defaultValue);
  return {
    sourceColumnIndex: mapping.sourceColumnIndex,
    sourceColumnName: mapping.sourceColumnName,
    mappingMode: mapping.mappingMode || (mapping.sourceColumnIndex === null ? 'CONSTANT' : 'SOURCE'),
    sourceDataType: mapping.sourceDataType || 'STRING',
    targetFieldName: mapping.targetFieldName,
    targetDataType: mapping.targetDataType || targetFields.find((field) => field.value === mapping.targetFieldName)?.type || 'STRING',
    transformations: sanitizeTransformations(mapping.transformations),
    emptySourcePolicy,
    defaultValue: emptySourcePolicy.mode === 'USE_DEFAULT' ? emptySourcePolicy.defaultValue || '' : '',
    forceValue: mapping.forceValue || '',
    conversionType: mapping.conversionType || 'NONE',
    conversionErrorMode,
    conversionErrorValue: conversionErrorMode === 'SET_CUSTOM' ? mapping.conversionErrorValue || '' : '',
    isNullable: conversionErrorMode === 'SET_NULL',
  };
}

function failureModeLabel(mode) {
  if (mode === 'SET_NULL') return 'Invalid conversion becomes NULL';
  if (mode === 'SET_CUSTOM') return 'Invalid conversion uses fallback';
  return 'Invalid conversion fails the row';
}

function recommendConversion(targetDataType, sourceDataType = 'STRING') {
  const target = targetDataType || 'STRING';
  if (target === 'NUMERIC' || target === 'INTEGER') return 'TO_NUMBER';
  if (target === 'DATE') return 'TO_DATE';
  if (target === 'BOOLEAN') return 'TO_BOOLEAN';
  if (target === 'STRING' && sourceDataType !== 'STRING') return 'TO_STRING';
  return 'NONE';
}

function getMappingConversionIssues(mappings, targetFields = TARGET_FIELDS) {
  return mappings
    .filter((mapping) => mapping.targetFieldName)
    .filter((mapping) => {
      const targetType = mapping.targetDataType || targetFields.find((field) => field.value === mapping.targetFieldName)?.type || 'STRING';
      const recommended = recommendConversion(targetType, mapping.sourceDataType || 'STRING');
      const emptyPolicy = normalizeEmptySourcePolicy(mapping.emptySourcePolicy, mapping.defaultValue);
      if (recommended !== 'NONE' && (!mapping.conversionType || mapping.conversionType === 'NONE')) return true;
      if (mapping.conversionErrorMode === 'SET_CUSTOM' && !hasText(mapping.conversionErrorValue)) return true;
      if (emptyPolicy.mode === 'USE_DEFAULT' && mapping.conversionErrorMode === 'SET_NULL') return true;
      return false;
    });
}

function applyMappingPreview(value, mapping) {
  let next = value;
  (mapping.transformations || []).forEach((transform) => {
    const action = transform.action;
    if (next !== null && next !== undefined) {
      const text = String(next);
      if (action === 'TRIM') next = text.trim();
      else if (action === 'TO_UPPER') next = text.toUpperCase();
      else if (action === 'TO_LOWER') next = text.toLowerCase();
      else if (action === 'REMOVESPACES') next = text.replace(/\s+/g, '');
    }
  });
  if (hasText(mapping.forceValue)) {
    next = mapping.forceValue;
  } else if (!hasText(next)) {
    const emptySourcePolicy = normalizeEmptySourcePolicy(mapping.emptySourcePolicy, mapping.defaultValue);
    if (emptySourcePolicy.mode === 'SET_NULL') next = null;
    else if (emptySourcePolicy.mode === 'USE_DEFAULT') next = emptySourcePolicy.defaultValue;
    else if (emptySourcePolicy.mode === 'FAIL_ROW') return 'ROW WOULD FAIL';
    else next = '';
  }
  next = applyPreviewConversion(next, mapping);
  return next;
}

function applyPreviewConversion(value, mapping) {
  const conversionType = mapping.conversionType || 'NONE';
  if (conversionType === 'NONE' || value === null || value === undefined || value === '') return value;
  try {
    const text = String(value).trim();
    if (conversionType === 'TO_STRING') return String(value);
    if (conversionType === 'TO_NUMBER') {
      const number = Number(text.replace(/,/g, ''));
      if (!Number.isFinite(number)) throw new Error('not a number');
      return number;
    }
    if (conversionType === 'TO_DATE') {
      const date = new Date(text);
      if (Number.isNaN(date.getTime())) throw new Error('not a date');
      return date.toISOString();
    }
    if (conversionType === 'TO_BOOLEAN') {
      const normalized = text.toLowerCase();
      if (['true', '1', 'yes', 'y'].includes(normalized)) return true;
      if (['false', '0', 'no', 'n'].includes(normalized)) return false;
      throw new Error('not boolean');
    }
  } catch {
    if (mapping.conversionErrorMode === 'SET_CUSTOM') return mapping.conversionErrorValue || '';
    if (mapping.conversionErrorMode === 'FAIL_ROW') return 'ROW WOULD FAIL';
    return null;
  }
  return value;
}

function describeMappingRule(mapping) {
  const parts = [];
  const transforms = (mapping.transformations || []).map((transform) => transform.action);
  const emptySourcePolicy = normalizeEmptySourcePolicy(mapping.emptySourcePolicy, mapping.defaultValue);
  if (transforms.length) parts.push(transforms.join(' -> '));
  if (emptySourcePolicy.mode === 'USE_DEFAULT') parts.push(`empty=${emptySourcePolicy.defaultValue}`);
  if (emptySourcePolicy.mode === 'SET_NULL') parts.push('empty=NULL');
  if (emptySourcePolicy.mode === 'FAIL_ROW') parts.push('empty fails row');
  if (hasText(mapping.forceValue)) parts.push(`always=${mapping.forceValue}`);
  if (mapping.conversionType && mapping.conversionType !== 'NONE') parts.push(mapping.conversionType);
  if (mapping.conversionType && mapping.conversionType !== 'NONE') parts.push(`on error ${mapping.conversionErrorMode || 'FAIL_ROW'}`);
  return parts.length ? parts.join(' | ') : 'No value rule selected';
}

async function readDelimitedPreview(file, format, hasHeaderRow) {
  if (!['CSV', 'TSV', 'PSV'].includes(format)) {
    return {
      columns: [],
      firstDataRow: [],
      parseNote: 'Preview is available after backend extraction for spreadsheet formats.',
    };
  }
  const delimiter = format === 'TSV' ? '\t' : format === 'PSV' ? '|' : ',';
  const text = await file.text();
  const rows = text.split(/\r?\n/).filter((row) => row.trim()).slice(0, 2).map((row) => splitDelimitedRow(row, delimiter));
  const firstRow = rows[0] || [];
  const secondRow = rows[1] || [];
  return {
    columns: hasHeaderRow ? firstRow : firstRow.map((_, index) => `Column_${index}`),
    firstDataRow: hasHeaderRow ? secondRow : firstRow,
    parseNote: rows.length ? 'Preview parsed from the uploaded sample file.' : 'No preview rows found in the uploaded sample file.',
  };
}

function splitDelimitedRow(row, delimiter) {
  const cells = [];
  let value = '';
  let quoted = false;
  for (let i = 0; i < row.length; i += 1) {
    const char = row[i];
    if (char === '"') {
      quoted = !quoted;
    } else if (char === delimiter && !quoted) {
      cells.push(value.trim());
      value = '';
    } else {
      value += char;
    }
  }
  cells.push(value.trim());
  return cells;
}

async function toggleSoftware(event, software, onRefresh, announce, fail, confirmAction) {
  event.stopPropagation();
  const confirmed = await confirmStatusChange(confirmAction, software.softwareName, software.enabled);
  if (!confirmed) return;
  try {
    await api.updateSoftware(software.id, { enabled: !software.enabled });
    await onRefresh();
    announce('Software status updated.');
  } catch (err) { fail(err); }
}

async function deleteSoftware(event, software, onRefresh, announce, fail, confirmAction) {
  event.stopPropagation();
  const confirmed = await confirmArchive(confirmAction, software.softwareName);
  if (!confirmed) return;
  try {
    await api.deleteSoftware(software.id);
    await onRefresh();
    announce('Software archived.');
  } catch (err) { fail(err); }
}
