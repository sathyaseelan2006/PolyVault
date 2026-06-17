/**
 * PolyVault Client UI Module
 * Handles inspector view rendering, file preview modals, path traversal, sidebar tabs, guide popovers, and updates.
 */

import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.8.0/firebase-app.js';
import { 
  getAuth, 
  signInWithEmailAndPassword, 
  createUserWithEmailAndPassword, 
  GoogleAuthProvider, 
  signInWithPopup, 
  signOut,
  onAuthStateChanged
} from 'https://www.gstatic.com/firebasejs/10.8.0/firebase-auth.js';

import { request } from './api.js';
import { render, zoomBy, transform, detectType, escapeHtml, short, animateCamera } from './graph.js';

// Firebase configuration (Configured with your project details)
const firebaseConfig = {
  apiKey: "AIzaSyAObvOnvicvdHZkWmyXoWaunrLT5gzvEwI",
  authDomain: "polyvault-6990a.firebaseapp.com",
  projectId: "polyvault-6990a",
  storageBucket: "polyvault-6990a.firebasestorage.app",
  messagingSenderId: "862933673886",
  appId: "1:862933673886:web:1e093b1e781daf1619d88f",
  measurementId: "G-G4M2Y9HMCT"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const googleProvider = new GoogleAuthProvider();

// Monitor state changes asynchronously to automatically refresh tokens
onAuthStateChanged(auth, async (user) => {
  const userDisplay = document.querySelector("#user-display-name");
  if (user) {
    const token = await user.getIdToken();
    localStorage.setItem("pv_session_token", token);
    if (userDisplay) {
      userDisplay.innerHTML = `<svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round" style="display:inline-block; vertical-align:middle; margin-right:6px;"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>${escapeHtml(user.displayName || user.email || 'User')}`;
      userDisplay.style.display = "inline-flex";
      userDisplay.style.alignItems = "center";
      userDisplay.style.gap = "6px";
    }
  } else {
    localStorage.removeItem("pv_session_token");
    if (userDisplay) {
      userDisplay.textContent = "";
      userDisplay.style.display = "none";
    }
  }
});

// DOM elements
const toast = document.querySelector("#toast");
const statusText = document.querySelector("#status-text");
const statusDot = document.querySelector(".status-dot");
const statusPill = document.querySelector("#server-status");
const fileContainer = document.querySelector("#file-attachment-container");
const modeButtons = document.querySelectorAll(".segmented button");
const goParentBtn = document.querySelector("#go-to-parent-btn");
const previewBtn = document.querySelector("#inspect-preview-btn");
const shareBtn = document.querySelector("#share-link-btn");
const toggleSidebarsBtn = document.querySelector("#toggle-sidebars-btn");
const appContainer = document.querySelector(".app");
const previewModal = document.querySelector("#preview-modal");
const previewCloseBtn = document.querySelector("#preview-close-btn");
const guideToggleBtn = document.querySelector("#guide-toggle-btn");
const guidePopover = document.querySelector("#guide-popover");

const selected = {
  type: document.querySelector("#node-type"),
  title: document.querySelector("#node-title"),
  copy: document.querySelector("#node-copy"),
  id: document.querySelector("#node-id"),
  activity: document.querySelector("#node-activity"),
  file: document.querySelector("#node-file"),
  download: document.querySelector("#download-link")
};

let recentlyVisited = [];

// Initialize UI triggers and event handlers
export function initUiEvents() {
  previewCloseBtn.addEventListener("click", () => {
    previewModal.classList.remove("show");
  });
  
  previewModal.addEventListener("click", (e) => {
    if (e.target === previewModal) {
      previewModal.classList.remove("show");
    }
  });

  guideToggleBtn.addEventListener("click", (e) => {
    e.stopPropagation();
    guidePopover.classList.toggle("show");
  });

  document.addEventListener("click", (e) => {
    if (guidePopover && !guidePopover.contains(e.target) && e.target !== guideToggleBtn) {
      guidePopover.classList.remove("show");
    }
  });

  toggleSidebarsBtn.addEventListener("click", () => {
    appContainer.classList.toggle("sidebars-collapsed");
    const monitorSvg = `<svg class="btn-icon" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>`;
    if (appContainer.classList.contains("sidebars-collapsed")) {
      toggleSidebarsBtn.innerHTML = `${monitorSvg}Show Sidebars`;
      toggleSidebarsBtn.classList.add("active");
      showToast("Full window map mode.");
    } else {
      toggleSidebarsBtn.innerHTML = `${monitorSvg}Full Window`;
      toggleSidebarsBtn.classList.remove("active");
      showToast("Sidebars restored.");
    }
    setTimeout(() => {
      loadGraph();
    }, 450);
  });

  modeButtons.forEach(btn => {
    if (btn.id === "toggle-sidebars-btn") return;
    btn.addEventListener("click", () => {
      modeButtons.forEach(b => { if (b.id !== "toggle-sidebars-btn") b.classList.remove("active"); });
      btn.classList.add("active");
      window.currentMode = btn.textContent.toLowerCase().trim();
      
      if (window.currentMode === "focus" && !window.selectedId) {
        window.selectedId = "vault-root";
      }
      
      loadGraph();
    });
  });

  document.querySelector("#refresh").addEventListener("click", loadGraph);
  document.querySelector("#create-node").addEventListener("click", createNode);
  document.querySelector("#upload-file").addEventListener("click", uploadFile);
  document.querySelector("#load-root").addEventListener("click", loadChildren);

  const nodeParentPathSelect = document.querySelector("#node-parent-path");
  if (nodeParentPathSelect) {
    nodeParentPathSelect.addEventListener("change", loadChildren);
  }

  document.querySelectorAll("[data-template]").forEach(button => {
    button.addEventListener("click", () => {
      const parentSelect = document.querySelector("#node-parent-path");
      if (parentSelect) parentSelect.value = "0";
      document.querySelector("#node-kind").value = "WORKSPACE";
      document.querySelector("#node-name").value = button.dataset.template;
      createNode();
    });
  });

  document.querySelector("#zoom-in-btn").addEventListener("click", () => zoomBy(1.2));
  document.querySelector("#zoom-out-btn").addEventListener("click", () => zoomBy(0.8));

  // Sidebar Tab Switching
  const tabExplorerBtn = document.querySelector("#tab-explorer-btn");
  const tabBookmarksBtn = document.querySelector("#tab-bookmarks-btn");
  const tabExplorerContent = document.querySelector("#tab-explorer-content");
  const tabBookmarksContent = document.querySelector("#tab-bookmarks-content");

  if (tabExplorerBtn && tabBookmarksBtn && tabExplorerContent && tabBookmarksContent) {
    tabExplorerBtn.addEventListener("click", () => {
      tabExplorerBtn.classList.add("active");
      tabBookmarksBtn.classList.remove("active");
      tabExplorerContent.style.display = "flex";
      tabBookmarksContent.style.display = "none";
    });

    tabBookmarksBtn.addEventListener("click", () => {
      tabExplorerBtn.classList.remove("active");
      tabBookmarksBtn.classList.add("active");
      tabExplorerContent.style.display = "none";
      tabBookmarksContent.style.display = "flex";
      updateBookmarksSidebar();
    });
  }

  const customColorSelect = document.querySelector("#custom-color");
  const customShapeSelect = document.querySelector("#custom-shape");
  const chkFavorite = document.querySelector("#chk-favorite");
  const chkImportant = document.querySelector("#chk-important");
  const customShortcutSelect = document.querySelector("#custom-shortcut");
  
  [customColorSelect, customShapeSelect, chkFavorite, chkImportant, customShortcutSelect].forEach(el => {
    if (el) el.addEventListener("change", saveCustomization);
  });

  // Auth Form Switching (Login ⇆ Signup tabs)
  const tabLoginBtn = document.querySelector("#auth-tab-login");
  const tabSignupBtn = document.querySelector("#auth-tab-signup");
  const loginForm = document.querySelector("#login-form");
  const signupForm = document.querySelector("#signup-form");

  if (tabLoginBtn && tabSignupBtn && loginForm && signupForm) {
    tabLoginBtn.addEventListener("click", () => {
      tabLoginBtn.classList.add("active");
      tabSignupBtn.classList.remove("active");
      loginForm.classList.add("active");
      signupForm.classList.remove("active");
    });

    tabSignupBtn.addEventListener("click", () => {
      tabLoginBtn.classList.remove("active");
      tabSignupBtn.classList.add("active");
      loginForm.classList.remove("active");
      signupForm.classList.add("active");
    });
  }

  // Password Visibility toggles
  const eyeOpenSvg = `<svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>`;
  const eyeClosedSvg = `<svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>`;

  const setupPasswordToggle = (btnId, inputId) => {
    const btn = document.querySelector(btnId);
    const input = document.querySelector(inputId);
    if (btn && input) {
      btn.addEventListener("click", () => {
        if (input.type === "password") {
          input.type = "text";
          btn.innerHTML = eyeClosedSvg;
        } else {
          input.type = "password";
          btn.innerHTML = eyeOpenSvg;
        }
      });
    }
  };
  setupPasswordToggle("#toggle-login-pass", "#login-password");
  setupPasswordToggle("#toggle-signup-pass", "#signup-password");
  setupPasswordToggle("#toggle-signup-confirm-pass", "#signup-confirm-password");

  // Login Form Submission
  if (loginForm) {
    loginForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const email = document.querySelector("#login-username").value.trim();
      const password = document.querySelector("#login-password").value;
      
      showToast("Signing in...");
      try {
        const userCredential = await signInWithEmailAndPassword(auth, email, password);
        const token = await userCredential.user.getIdToken();
        
        localStorage.setItem("pv_session_token", token);
        showToast(`Welcome back, ${userCredential.user.email || 'User'}! 🔐`);
        
        // Switch to workspace view
        document.querySelector("#auth-portal").style.display = "none";
        document.querySelector("#studio-workspace").style.display = "block";
        
        // Load graph
        await loadGraph();
      } catch (err) {
        showToast("Login failed: " + err.message);
      }
    });
  }

  // Signup Form Submission
  if (signupForm) {
    signupForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const email = document.querySelector("#signup-username").value.trim();
      const password = document.querySelector("#signup-password").value;
      const confirmPass = document.querySelector("#signup-confirm-password").value;
      
      if (password !== confirmPass) {
        showToast("Passwords do not match!");
        return;
      }
      
      showToast("Creating account...");
      try {
        const userCredential = await createUserWithEmailAndPassword(auth, email, password);
        const token = await userCredential.user.getIdToken();
        
        localStorage.setItem("pv_session_token", token);
        showToast(`Account registered successfully, welcome! 🚀`);
        
        // Switch to workspace view
        document.querySelector("#auth-portal").style.display = "none";
        document.querySelector("#studio-workspace").style.display = "block";
        
        // Load graph
        await loadGraph();
      } catch (err) {
        showToast("Registration failed: " + err.message);
      }
    });
  }

  // Google Sign-In
  const googleBtn = document.querySelector(".google-btn");
  if (googleBtn) {
    googleBtn.addEventListener("click", async () => {
      showToast("Connecting with Google...");
      try {
        const result = await signInWithPopup(auth, googleProvider);
        const token = await result.user.getIdToken();
        
        localStorage.setItem("pv_session_token", token);
        showToast(`Welcome, ${result.user.displayName || 'User'}! 🚀`);
        
        // Switch to workspace view
        document.querySelector("#auth-portal").style.display = "none";
        document.querySelector("#studio-workspace").style.display = "block";
        
        // Load graph
        await loadGraph();
      } catch (err) {
        showToast("Google Auth failed: " + err.message);
      }
    });
  }

  // Sign Out Button
  const signoutBtn = document.querySelector("#signout-btn");
  if (signoutBtn) {
    signoutBtn.addEventListener("click", async () => {
      if (!confirm("Are you sure you want to sign out?")) return;
      
      showToast("Signing out...");
      try {
        await signOut(auth);
      } catch (e) {
        // even if Firebase signout fails, we proceed to clear token locally
      }
      localStorage.removeItem("pv_session_token");
      
      // Notify backend to invalidate token from local session cache
      try {
        await request("/api/auth/logout", { method: "POST" });
      } catch (e) {}

      // Reload page to wipe state
      window.location.reload();
    });
  }

  // Authenticated file download link handler
  const downloadLink = document.querySelector("#download-link");
  if (downloadLink) {
    downloadLink.addEventListener("click", (e) => {
      e.preventDefault();
      const selectedId = window.selectedId;
      if (!selectedId || selectedId === "vault-root") return;
      const graph = window.currentGraph;
      if (!graph) return;
      const node = graph.nodes.find(n => n.id === selectedId);
      if (node && node.fileId) {
        const fileUrl = `/api/download?fileId=${node.fileId}&download=true`;
        openAuthenticatedUrl(fileUrl, node.filename, true);
      }
    });
  }
}

// Toast helper
export function showToast(message) {
  toast.textContent = message;
  toast.classList.add("show");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 2500);
}

// Format bytes
export function formatBytes(bytes) {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

// Copy sharing link
export function copyShareLink(fileId) {
  const shareUrl = `${window.location.origin}/api/download?fileId=${fileId}`;
  navigator.clipboard.writeText(shareUrl).then(() => {
    showToast("Share link copied to clipboard! 📋");
  }).catch(() => {
    showToast("Failed to copy link.");
  });
}

// Generate inspector template
export function suggestionFor(node) {
  if (node.type === "workspace") return "Make this a main sector of your life: project vaults, academic courses, finance tags, or career tracks.";
  if (node.type === "project") return "Attach repositories, demo clips, system architectures, and markdown notes as children nodes.";
  if (node.type === "file") return "This file is compressed and versioned. You can track multi-version updates seamlessly.";
  if (node.type === "folder" || node.type === "branch") return "Convert boring directory structures into meaning-based, active map pathways.";
  return "Your digital second brain map is fully modular. Lay out the structure first, then seed it with data.";
}

// Populate inspector data on selection
export function inspect(node, graph) {
  window.selectedId = node.id;
  selected.type.textContent = node.type || "Node";
  selected.title.textContent = node.label || "Untitled";
  selected.copy.textContent = suggestionFor(node);
  selected.id.textContent = node.nodeId ? `#${node.nodeId}` : node.id;

  const stageTitle = document.querySelector("#stage-title");
  const stageKicker = document.querySelector("#stage-kicker");
  if (stageTitle && stageKicker) {
    stageTitle.textContent = node.label || "Untitled";
    stageKicker.textContent = (node.type || "Constellation").toUpperCase() + " VIEW";
  }
  
  const activity = node.recency || "fresh";
  selected.activity.textContent = activity;
  
  selected.activity.className = "";
  if (activity === "hot" || activity === "fresh") selected.activity.classList.add("status-fresh");
  else if (activity === "warm") selected.activity.classList.add("status-warm");
  else selected.activity.classList.add("status-stale");

  if (node.fileId) {
    selected.file.textContent = `File #${node.fileId}: ${node.filename} (${formatBytes(node.originalSize || 0)})`;
    fileContainer.classList.add("has-file");
    
    previewBtn.style.display = "block";
    previewBtn.onclick = () => openPreviewModal(node);
    
    shareBtn.style.display = "block";
    shareBtn.onclick = () => copyShareLink(node.fileId);
  } else {
    selected.file.textContent = "No file attached to this node.";
    fileContainer.classList.remove("has-file");
    
    previewBtn.style.display = "none";
    shareBtn.style.display = "none";
  }
  
  selected.download.style.display = node.fileId ? "block" : "none";
  
  const nodeIdVal = node.nodeId || 0;
  const nodeParentSelect = document.querySelector("#node-parent-path");
  const uploadParentSelect = document.querySelector("#upload-parent-path");
  
  let targetParentId = nodeIdVal;
  if (node.type === "file") {
    targetParentId = node.parentId || 0;
  }
  
  if (nodeParentSelect) nodeParentSelect.value = targetParentId;
  if (uploadParentSelect) uploadParentSelect.value = targetParentId;
  
  // Customizer fields
  const customColorSelect = document.querySelector("#custom-color");
  const customShapeSelect = document.querySelector("#custom-shape");
  const chkFavorite = document.querySelector("#chk-favorite");
  const chkImportant = document.querySelector("#chk-important");
  const customShortcutSelect = document.querySelector("#custom-shortcut");
  const customizerPanel = document.querySelector("#customizer-panel");
  
  if (node.id !== "vault-root") {
    if (customizerPanel) customizerPanel.style.display = "block";
    if (customColorSelect) customColorSelect.value = node.color || "";
    if (customShapeSelect) customShapeSelect.value = node.shape || "circle";
    if (chkFavorite) chkFavorite.checked = !!node.favorite;
    if (chkImportant) chkImportant.checked = !!node.important;
    if (customShortcutSelect) customShortcutSelect.value = node.shortcut || "";
  } else {
    if (customizerPanel) customizerPanel.style.display = "none";
  }

  // Visited history
  if (node.id !== "vault-root") {
    recentlyVisited = recentlyVisited.filter(n => n.id !== node.id);
    recentlyVisited.unshift({ 
      id: node.id, 
      label: node.label || node.filename || "Untitled", 
      type: node.type || "node" 
    });
    if (recentlyVisited.length > 5) {
      recentlyVisited.pop();
    }
    updateBookmarksSidebar();
  }

  loadChildren();

  if (node.id !== "vault-root") {
    goParentBtn.style.display = "block";
    goParentBtn.onclick = () => window.selectNodeById(node.parentId || 0);
  } else {
    goParentBtn.style.display = "none";
  }

  const deleteBtn = document.querySelector("#delete-node-btn");
  if (deleteBtn) {
    if (node.id !== "vault-root") {
      deleteBtn.style.display = "block";
      deleteBtn.onclick = () => deleteCurrentNode(node);
    } else {
      deleteBtn.style.display = "none";
    }
  }

  render(graph);
}
window.inspect = inspect; // Make it globally accessible for graph.js mousedown trigger

// Save custom color, shape, favorite, shortcut customization
export async function saveCustomization() {
  const selectedId = window.selectedId || "vault-root";
  if (selectedId === "vault-root") return;
  
  const nodeId = parseInt(selectedId.replace("node-", ""));
  const color = document.querySelector("#custom-color").value;
  const shape = document.querySelector("#custom-shape").value;
  const important = document.querySelector("#chk-important").checked;
  const favorite = document.querySelector("#chk-favorite").checked;
  const shortcut = document.querySelector("#custom-shortcut").value;
  
  try {
    await request(`/api/nodes/update?nodeId=${nodeId}&color=${encodeURIComponent(color)}&shape=${encodeURIComponent(shape)}&important=${important}&favorite=${favorite}&shortcut=${encodeURIComponent(shortcut)}`, {
      method: "POST"
    });
    
    await loadGraph();
    
    const graph = window.currentGraph;
    if (graph) {
      const updatedNode = graph.nodes.find(n => n.nodeId === nodeId);
      if (updatedNode) {
        inspect(updatedNode, graph);
      }
    }
  } catch (err) {
    showToast("Error updating appearance: " + err.message);
  }
}

// Refresh bookmarks sidebar
export function updateBookmarksSidebar() {
  const graph = window.currentGraph;
  if (!graph) return;
  
  const favsTarget = document.querySelector("#favs-list");
  const impsTarget = document.querySelector("#imps-list");
  const recentsTarget = document.querySelector("#recents-list");
  
  if (!favsTarget || !impsTarget || !recentsTarget) return;
  
  const favNodes = graph.nodes.filter(n => n.favorite && n.id !== "vault-root");
  favsTarget.innerHTML = favNodes.map(node => `
    <div class="child-row clickable" onclick="selectNodeById(${node.nodeId})">
      <strong style="display: inline-flex; align-items: center; gap: 6px;"><svg class="panel-icon" viewBox="0 0 24 24" stroke="var(--gold-primary)" stroke-width="2.5" fill="var(--gold-primary)" style="width:12px; height:12px; margin-right:0;"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>${escapeHtml(node.label)}</strong>
      <span>${node.type} (#${node.nodeId})</span>
    </div>
  `).join("") || `
    <div class="child-row">
      <strong>No favorites marked</strong>
      <span>Tick "Favorite" on any selected node.</span>
    </div>
  `;
  
  const impNodes = graph.nodes.filter(n => n.important && n.id !== "vault-root");
  impsTarget.innerHTML = impNodes.map(node => `
    <div class="child-row clickable" onclick="selectNodeById(${node.nodeId})">
      <strong style="display: inline-flex; align-items: center; gap: 6px;"><svg class="panel-icon" viewBox="0 0 24 24" stroke="#ff6b4a" stroke-width="2.5" fill="#ff6b4a" style="width:12px; height:12px; margin-right:0;"><path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 3.5z"/></svg>${escapeHtml(node.label)}</strong>
      <span>${node.type} (#${node.nodeId})</span>
    </div>
  `).join("") || `
    <div class="child-row">
      <strong>No important nodes</strong>
      <span>Tick "Important" on any selected node.</span>
    </div>
  `;
  
  recentsTarget.innerHTML = recentlyVisited.map(node => `
    <div class="child-row clickable" onclick="selectNodeById(${node.id.startsWith('node-') ? parseInt(node.id.replace('node-','')) : 0})">
      <strong style="display: inline-flex; align-items: center; gap: 6px;"><svg class="panel-icon" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5" fill="none" style="width:12px; height:12px; margin-right:0;"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>${escapeHtml(node.label)}</strong>
      <span>${node.type}</span>
    </div>
  `).join("") || `
    <div class="child-row">
      <strong>No recent visits</strong>
      <span>Click folder/file nodes in graph.</span>
    </div>
  `;
}

// Delete folder/file recursively
export async function deleteCurrentNode(node) {
  const isFile = node.type === "file";
  const itemWord = isFile ? "file" : "folder/branch and all its contents recursively";
  
  if (!confirm(`Are you sure you want to delete the ${itemWord} "${node.label || node.filename}"? This action cannot be undone.`)) {
    return;
  }
  
  showToast("Deleting...");
  try {
    const nodeIdVal = node.nodeId || 0;
    await request(`/api/nodes?nodeId=${nodeIdVal}`, {
      method: "DELETE"
    });
    
    showToast("Deleted successfully!");
    
    window.selectedId = "vault-root";
    const graph = await request("/api/graph");
    const rootNode = graph.nodes.find(n => n.id === "vault-root");
    if (rootNode) {
      inspect(rootNode, graph);
    }
  } catch (err) {
    showToast("Error deleting: " + err.message);
  }
  
  await loadGraph();
  await loadChildren();
}

// Open preview modal
export async function openAuthenticatedUrl(url, filename, forceDownload) {
  const token = localStorage.getItem('pv_session_token');
  const headers = token ? { 'Authorization': `Bearer ${token}` } : {};
  try {
    const response = await fetch(url, { headers });
    if (!response.ok) throw new Error("Failed to load file");
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    if (forceDownload) {
      const a = document.createElement('a');
      a.href = objectUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    } else {
      window.open(objectUrl, "_blank");
    }
  } catch (err) {
    showToast("Download error: " + err.message);
  }
}

export async function openPreviewModal(node) {
  const filenameEl = document.querySelector("#preview-filename");
  const bodyEl = document.querySelector("#preview-body");
  
  filenameEl.textContent = node.filename || node.label;
  bodyEl.innerHTML = `<div class="status-fresh">Fetching content from server...</div>`;
  previewModal.classList.add("show");
  
  const fileUrl = `/api/download?fileId=${node.fileId}`;
  const downloadUrl = `/api/download?fileId=${node.fileId}&download=true`;
  document.querySelector("#preview-download-btn").onclick = () => openAuthenticatedUrl(downloadUrl, node.filename, true);
  document.querySelector("#preview-full-btn").onclick = () => openAuthenticatedUrl(fileUrl, node.filename, false);
  document.querySelector("#preview-share-btn").onclick = () => copyShareLink(node.fileId);
  
  const type = detectType(node.filename);
  
  try {
    const token = localStorage.getItem('pv_session_token');
    const headers = token ? { 'Authorization': `Bearer ${token}` } : {};

    if (type === "image") {
      const response = await fetch(fileUrl, { headers });
      if (!response.ok) throw new Error(`Fetch failed: ${response.status}`);
      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);
      bodyEl.innerHTML = `<img src="${objectUrl}" class="preview-image" alt="${escapeHtml(node.filename)}">`;
    } else if (type === "pdf") {
      const response = await fetch(fileUrl, { headers });
      if (!response.ok) throw new Error(`Fetch failed: ${response.status}`);
      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);
      bodyEl.innerHTML = `<iframe src="${objectUrl}" style="width: 100%; height: 100%; border: none; border-radius: 12px; background: white;"></iframe>`;
    } else if (type === "code" || node.filename.endsWith(".txt") || node.filename.endsWith(".md") || node.filename.endsWith(".json") || node.filename.endsWith(".tsv") || node.filename.endsWith(".xml")) {
      const text = await fetch(fileUrl, { headers }).then(r => {
        if (!r.ok) throw new Error(`Fetch failed: ${r.status}`);
        return r.text();
      });
      bodyEl.innerHTML = `<pre class="preview-code"><code>${escapeHtml(text)}</code></pre>`;
    } else {
      bodyEl.innerHTML = `
        <div class="preview-generic">
          <div class="preview-icon"><svg viewBox="0 0 24 24" width="48" height="48" stroke="var(--cyan)" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round" style="opacity:0.8; display:block; margin:0 auto 12px;"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg></div>
          <strong>${escapeHtml(node.filename)}</strong>
          <span style="color: var(--cyan); font-weight:700;">Format: ${type.toUpperCase()}</span>
          <span>Size: ${formatBytes(node.originalSize || 0)}</span>
          <p style="max-width: 340px; color: var(--muted); font-size: 12px; margin-top: 8px; line-height: 1.5;">Previewing not supported for this format. Use the action buttons above to download or view it fully.</p>
        </div>
      `;
    }
  } catch (err) {
    bodyEl.innerHTML = `<div class="status-stale">Error rendering preview: ${err.message}</div>`;
  }
}

// Traversal path selectors updates
export function updateParentPathSelectors(graph) {
  const nodeParentSelect = document.querySelector("#node-parent-path");
  const uploadParentSelect = document.querySelector("#upload-parent-path");
  
  if (!nodeParentSelect || !uploadParentSelect) return;
  
  const prevNodeVal = nodeParentSelect.value;
  const prevUploadVal = uploadParentSelect.value;
  
  const folderNodes = graph.nodes.filter(n => n.type !== "file");
  
  const pathsList = folderNodes.map(node => {
    let nodeId = parseInt(node.nodeId || "0");
    if (node.id === "vault-root") nodeId = 0;
    return {
      id: nodeId,
      path: getNodePath(nodeId, graph.nodes)
    };
  });
  
  pathsList.sort((a, b) => a.path.localeCompare(b.path));
  
  const optionsHtml = pathsList.map(item => {
    const displayPath = item.path === "/" ? "/ (Root)" : item.path;
    return `<option value="${item.id}">${escapeHtml(displayPath)}</option>`;
  }).join("");
  
  nodeParentSelect.innerHTML = optionsHtml;
  uploadParentSelect.innerHTML = optionsHtml;
  
  if (prevNodeVal && pathsList.some(p => p.id == prevNodeVal)) {
    nodeParentSelect.value = prevNodeVal;
  } else {
    nodeParentSelect.value = "0";
  }
  
  if (prevUploadVal && pathsList.some(p => p.id == prevUploadVal)) {
    uploadParentSelect.value = prevUploadVal;
  } else {
    uploadParentSelect.value = "0";
  }
}

// Compute full path of node
export function getNodePath(nodeId, nodes) {
  const idVal = parseInt(nodeId);
  if (idVal === 0) return "/";
  
  const node = nodes.find(n => parseInt(n.nodeId) === idVal);
  if (!node) return "";
  
  if (node.id === "vault-root") return "/";
  
  const parentPath = getNodePath(node.parentId, nodes);
  return parentPath === "/" ? `/${node.label}` : `${parentPath}/${node.label}`;
}

// Traversal resolution and creation
export async function resolveAndCreatePath(parentPathId, pathString, targetType) {
  let currentParentId = pathString.startsWith("/") ? 0 : parseInt(parentPathId || "0");
  
  const segments = pathString.split("/").map(s => s.trim()).filter(s => s.length > 0);
  if (segments.length === 0) {
    return currentParentId;
  }
  
  let graph = await request("/api/graph");
  
  for (let i = 0; i < segments.length; i++) {
    const segment = segments[i];
    const isLast = (i === segments.length - 1);
    
    const existing = graph.nodes.find(n => {
      const nParentId = parseInt(n.parentId || "0");
      const checkParentId = parseInt(currentParentId || "0");
      return nParentId === checkParentId && 
             n.label.toLowerCase() === segment.toLowerCase() &&
             n.type !== "file";
    });
    
    if (existing) {
      currentParentId = existing.nodeId;
    } else {
      const typeToCreate = isLast ? targetType : "FOLDER";
      const response = await request(`/api/nodes?parentId=${currentParentId}&type=${encodeURIComponent(typeToCreate)}&title=${encodeURIComponent(segment)}`, {
        method: "POST"
      });
      currentParentId = parseInt(response.nodeId);
      graph = await request("/api/graph");
    }
  }
  
  return currentParentId;
}

// Create new node action
export async function createNode() {
  const parentSelect = document.querySelector("#node-parent-path");
  const parentId = parseInt(parentSelect ? parentSelect.value : "0");
  const type = document.querySelector("#node-kind").value;
  const namePath = document.querySelector("#node-name").value.trim();
  if (!namePath) {
    showToast("Give the node a name or directory path first.");
    return;
  }
  
  showToast("Creating directory path...");
  try {
    const resolvedParentId = await resolveAndCreatePath(parentId, namePath, type);
    document.querySelector("#node-name").value = "";
    showToast(`Path created successfully!`);
    window.selectedId = `node-${resolvedParentId}`;
  } catch (err) {
    showToast("Error creating node: " + err.message);
  }
  
  await loadGraph();
  await loadChildren();
}

// Upload file action
export async function uploadFile() {
  const picker = document.querySelector("#file-picker");
  const file = picker.files[0];
  if (!file) {
    showToast("Pick a file first.");
    return;
  }
  
  const parentSelect = document.querySelector("#upload-parent-path");
  const parentId = parseInt(parentSelect ? parentSelect.value : "0");
  const subpath = document.querySelector("#upload-subpath").value.trim();
  const title = document.querySelector("#upload-title").value.trim() || file.name;
  
  showToast("Resolving path and uploading...");
  try {
    let targetParentId = parentId;
    if (subpath.length > 0) {
      targetParentId = await resolveAndCreatePath(parentId, subpath, "FOLDER");
    }
    
    await request(`/api/upload?parentId=${targetParentId}&filename=${encodeURIComponent(file.name)}&title=${encodeURIComponent(title)}&type=${encodeURIComponent(detectType(file.name))}`, {
      method: "POST",
      body: await file.arrayBuffer()
    });
    
    picker.value = "";
    document.querySelector("#upload-title").value = "";
    document.querySelector("#upload-subpath").value = "";
    showToast(`File "${file.name}" uploaded successfully!`);
  } catch (err) {
    showToast("Error uploading file: " + err.message);
  }
  
  await loadGraph();
  await loadChildren();
}

// Load children row listing
export async function loadChildren() {
  const parentSelect = document.querySelector("#node-parent-path");
  const parentId = parentSelect ? parentSelect.value : "0";
  try {
    const data = await request(`/api/list?parentId=${encodeURIComponent(parentId)}`);
    const target = document.querySelector("#children-list");
    
    const nodes = data.nodes.map(node => {
      const icon = getSvgIcon(node.type.toLowerCase());
      return `
        <div class="child-row clickable" onclick="selectNodeById(${node.id})">
          <strong style="display: inline-flex; align-items: center; gap: 6px;">${icon}${escapeHtml(node.title)}</strong>
          <span>${node.type} (#${node.id})</span>
        </div>
      `;
    });
    
    const files = data.files.map(file => {
      const icon = getSvgIcon("file");
      return `
        <div class="child-row clickable" onclick="selectNodeById(${file.nodeId})">
          <strong style="display: inline-flex; align-items: center; gap: 6px;">${icon}${escapeHtml(file.filename)}</strong>
          <span>File #${file.id} (Version ${file.currentVersion})</span>
        </div>
      `;
    });
    
    target.innerHTML = [...nodes, ...files].join("") || `
      <div class="child-row">
        <strong>No children here</strong>
        <span>Spawn one in the fields above.</span>
      </div>
    `;
  } catch (err) {
    console.error(err);
  }
}

// selectNodeById handler
window.selectNodeById = async function(nodeId) {
  try {
    const graph = await request("/api/graph");
    let targetNode = graph.nodes.find(n => n.nodeId === nodeId);
    
    if (nodeId === 0) {
      targetNode = graph.nodes.find(n => n.id === "vault-root");
    }
    
    if (targetNode) {
      inspect(targetNode, graph);
    } else {
      window.currentMode = "map";
      modeButtons.forEach(b => {
        if (b.id === "toggle-sidebars-btn") return;
        if (b.textContent.toLowerCase().trim() === "map") b.classList.add("active");
        else b.classList.remove("active");
      });
      const newGraph = await request("/api/graph");
      const fallbackNode = newGraph.nodes.find(n => n.nodeId === nodeId || (nodeId === 0 && n.id === "vault-root"));
      if (fallbackNode) {
        inspect(fallbackNode, newGraph);
      }
    }
  } catch (err) {
    showToast("Error inspecting node: " + err.message);
  }
};

// Main Graph Loader coordinating SVG and UI lists
export async function loadGraph() {
  try {
    const graph = await request("/api/graph");
    window.currentGraph = graph;
    statusText.textContent = "ONLINE";
    statusPill.style.borderColor = "rgba(57, 255, 20, 0.3)";
    statusPill.style.background = "rgba(57, 255, 20, 0.06)";
    statusPill.style.color = "var(--lime)";
    statusDot.style.background = "var(--lime)";
    statusDot.style.boxShadow = "0 0 8px var(--lime)";
    
    updateParentPathSelectors(graph);
    updateBookmarksSidebar();
    loadChildren();
    render(graph);
  } catch (error) {
    statusText.textContent = "OFFLINE";
    statusPill.style.borderColor = "rgba(239, 68, 68, 0.3)";
    statusPill.style.background = "rgba(239, 68, 68, 0.06)";
    statusPill.style.color = "var(--danger)";
    statusDot.style.background = "var(--danger)";
    statusDot.style.boxShadow = "0 0 8px var(--danger)";
    showToast("Server offline. Boot Java backend & refresh!");
  }
}
window.loadGraph = loadGraph; // Make it globally accessible

// Helper function to return high-quality vector SVGs instead of text emojis
function getSvgIcon(type) {
  if (type === "workspace") {
    return `<svg class="tab-icon" viewBox="0 0 24 24" stroke="var(--cyan)" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right:0;"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/></svg>`;
  } else if (type === "branch") {
    return `<svg class="tab-icon" viewBox="0 0 24 24" stroke="var(--lime)" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right:0;"><line x1="6" y1="3" x2="6" y2="15"/><circle cx="18" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M18 9a9 9 0 0 1-9 9"/></svg>`;
  } else if (type === "project") {
    return `<svg class="tab-icon" viewBox="0 0 24 24" stroke="var(--pink)" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right:0;"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>`;
  } else if (type === "note") {
    return `<svg class="tab-icon" viewBox="0 0 24 24" stroke="var(--amber)" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right:0;"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>`;
  } else if (type === "file") {
    return `<svg class="tab-icon" viewBox="0 0 24 24" stroke="var(--cyan)" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right:0;"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="13 2 13 9 20 9"/></svg>`;
  } else {
    // Default folder
    return `<svg class="tab-icon" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right:0;"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>`;
  }
}

