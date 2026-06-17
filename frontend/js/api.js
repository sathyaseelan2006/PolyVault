export const API_BASE = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
  ? ''
  : 'https://polyvault-7m5s.onrender.com';

export async function request(url, options = {}) {
  const token = localStorage.getItem('pv_session_token');
  if (token) {
    options.headers = options.headers || {};
    options.headers['Authorization'] = `Bearer ${token}`;
  }
  
  const formattedUrl = url.startsWith('http') ? url : `${API_BASE}${url.startsWith('/') ? url : '/' + url}`;
  const response = await fetch(formattedUrl, options);
  
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

