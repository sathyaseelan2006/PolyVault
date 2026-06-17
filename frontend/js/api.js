/**
 * PolyVault Client API Layer
 * Handles network requests to the local REST server.
 */

export async function request(url, options = {}) {
  const token = localStorage.getItem('pv_session_token');
  if (token) {
    options.headers = options.headers || {};
    options.headers['Authorization'] = `Bearer ${token}`;
  }
  
  const response = await fetch(url, options);
  
  if (response.status === 401) {
    localStorage.removeItem('pv_session_token');
    window.location.reload();
    throw new Error('Unauthorized');
  }
  
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
  return response.json();
}

