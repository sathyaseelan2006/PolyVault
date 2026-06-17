/**
 * PolyVault Client Core App Module
 * Initializes the mindmap graph events, UI controls, and runs first-load procedures.
 */

import { initGraphEvents } from './graph.js';
import { initUiEvents, loadGraph } from './ui.js';

document.addEventListener("DOMContentLoaded", () => {
  // 1. Initialize interactive canvas drawing listeners
  initGraphEvents();
  
  // 2. Initialize sidebars, modals, customizer input listeners
  initUiEvents();
  
  // 3. Set core default states
  window.currentMode = "map";
  window.selectedId = "vault-root";
  
  // 4. Check session and toggle views
  const token = localStorage.getItem("pv_session_token");
  const authPortal = document.querySelector("#auth-portal");
  const studioWorkspace = document.querySelector("#studio-workspace");
  
  if (token) {
    if (authPortal) authPortal.style.display = "none";
    if (studioWorkspace) studioWorkspace.style.display = "block";
    // Boot first graph loader fetch
    loadGraph();
  } else {
    if (authPortal) authPortal.style.display = "flex";
    if (studioWorkspace) studioWorkspace.style.display = "none";
  }
});

