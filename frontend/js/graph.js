/**
 * PolyVault Client SVG Mindmap Graph Module
 * Handles coordinates, panning, zooming, node shapes, dragging physics, and D3-style SVG rendering.
 */

// Camera animation and transform state
export let transform = { x: 0, y: 0, scale: 1 };
let cameraAnimationId = null;

// Drag and drop states
let isPanning = false;
let isDraggingNode = false;
let draggedNodeId = null;
let lastMouseX = 0;
let lastMouseY = 0;

// Set up camera transforms
export function applyTransform() {
  const g = document.querySelector("#viewport-g");
  if (g) {
    g.setAttribute("transform", `translate(${transform.x}, ${transform.y}) scale(${transform.scale})`);
  }
}

export function animateCamera(targetX, targetY, targetScale, duration = 650) {
  if (cameraAnimationId) {
    cancelAnimationFrame(cameraAnimationId);
  }
  
  const startX = transform.x;
  const startY = transform.y;
  const startScale = transform.scale;
  const startTime = performance.now();
  
  function step(now) {
    const progress = Math.min((now - startTime) / duration, 1);
    const t = easeOutCubic(progress);
    
    transform.x = startX + (targetX - startX) * t;
    transform.y = startY + (targetY - startY) * t;
    transform.scale = startScale + (targetScale - startScale) * t;
    
    applyTransform();
    
    if (progress < 1) {
      cameraAnimationId = requestAnimationFrame(step);
    } else {
      cameraAnimationId = null;
    }
  }
  
  cameraAnimationId = requestAnimationFrame(step);
}

function easeOutCubic(x) {
  return 1 - Math.pow(1 - x, 3);
}

export function resetCamera() {
  animateCamera(0, 0, 1, 600);
}

// Global Zoom actions
export function zoomBy(factor) {
  const viewport = document.querySelector(".stage-viewport");
  const w = viewport.clientWidth || 800;
  const h = viewport.clientHeight || 600;
  
  const mouseX = w * 0.5;
  const mouseY = h * 0.5;
  
  const nextScale = Math.min(Math.max(transform.scale * factor, 0.15), 4);
  
  transform.x = mouseX - (mouseX - transform.x) * (nextScale / transform.scale);
  transform.y = mouseY - (mouseY - transform.y) * (nextScale / transform.scale);
  transform.scale = nextScale;
  
  applyTransform();
}

// Custom SVG shape drawing
export function drawNodeShape(group, node, size) {
  const shape = node.shape || "circle";
  const color = node.color || colorFor(node);
  const stroke = strokeFor(node);
  const strokeWidth = 1.5;
  
  const selectedId = window.selectedId || "vault-root";
  
  // Selected ring highlights
  if (node.id === selectedId) {
    const ringSize = size + 6;
    if (shape === "circle") {
      group.append(el("circle", {
        class: "selected-ring",
        cx: 0,
        cy: 0,
        r: ringSize
      }));
    } else if (shape === "rect") {
      group.append(el("rect", {
        class: "selected-ring",
        x: -ringSize,
        y: -ringSize * 0.7,
        width: ringSize * 2,
        height: ringSize * 1.4,
        rx: 6,
        ry: 6
      }));
    } else if (shape === "diamond") {
      const points = `0,-${ringSize} ${ringSize},0 0,${ringSize} -${ringSize},0`;
      group.append(el("polygon", {
        class: "selected-ring",
        points
      }));
    } else if (shape === "hexagon") {
      const w = ringSize;
      const h = ringSize * 0.86;
      const points = `${w*0.5},-${h} ${w},0 ${w*0.5},${h} -${w*0.5},${h} -${w},0 -${w*0.5},-${h}`;
      group.append(el("polygon", {
        class: "selected-ring",
        points
      }));
    } else if (shape === "triangle") {
      const points = `0,-${ringSize * 1.1} ${ringSize},${ringSize * 0.8} -${ringSize},${ringSize * 0.8}`;
      group.append(el("polygon", {
        class: "selected-ring",
        points
      }));
    }
  }
  
  // Draw body
  if (shape === "circle") {
    group.append(el("circle", {
      cx: 0,
      cy: 0,
      r: size,
      fill: color,
      stroke: stroke,
      "stroke-width": strokeWidth
    }));
  } else if (shape === "rect") {
    group.append(el("rect", {
      x: -size,
      y: -size * 0.7,
      width: size * 2,
      height: size * 1.4,
      rx: 6,
      ry: 6,
      fill: color,
      stroke: stroke,
      "stroke-width": strokeWidth
    }));
  } else if (shape === "diamond") {
    const points = `0,-${size} ${size},0 0,${size} -${size},0`;
    group.append(el("polygon", {
      points,
      fill: color,
      stroke: stroke,
      "stroke-width": strokeWidth
    }));
  } else if (shape === "hexagon") {
    const w = size;
    const h = size * 0.86;
    const points = `${w*0.5},-${h} ${w},0 ${w*0.5},${h} -${w*0.5},${h} -${w},0 -${w*0.5},-${h}`;
    group.append(el("polygon", {
      points,
      fill: color,
      stroke: stroke,
      "stroke-width": strokeWidth
    }));
  } else if (shape === "triangle") {
    const points = `0,-${size * 1.1} ${size},${size * 0.8} -${size},${size * 0.8}`;
    group.append(el("polygon", {
      points,
      fill: color,
      stroke: stroke,
      "stroke-width": strokeWidth
    }));
  }
  
  // Star & Flame status badges
  if (node.important || node.favorite) {
    const badgeGroup = el("g", {
      transform: `translate(${size * 0.45}, ${-size * 0.5}) scale(0.6)`,
      style: "pointer-events:none;"
    });

    if (node.important && node.favorite) {
      // Draw star and flame side-by-side
      const star = el("path", {
        d: "M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z",
        transform: "translate(-22, -11)",
        class: "badge-star"
      });
      const flame = el("path", {
        d: "M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 3.5z",
        transform: "translate(0, -11)",
        class: "badge-flame"
      });
      badgeGroup.append(star);
      badgeGroup.append(flame);
    } else if (node.favorite) {
      const star = el("path", {
        d: "M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z",
        transform: "translate(-11, -11)",
        class: "badge-star"
      });
      badgeGroup.append(star);
    } else {
      const flame = el("path", {
        d: "M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 3.5z",
        transform: "translate(-11, -11)",
        class: "badge-flame"
      });
      badgeGroup.append(flame);
    }
    group.append(badgeGroup);
  }
}

export function colorFor(node) {
  if (node.type === "root") return "url(#rootGradient)";
  const palette = {
    workspace: "var(--cyan)",
    branch: "var(--lime)",
    folder: "var(--amber)",
    project: "var(--pink)",
    note: "#a855f7",
    file: "var(--purple)"
  };
  const color = palette[node.type] || "var(--cyan)";
  if (node.recency === "stale") return "var(--stale)";
  return color;
}

export function strokeFor(node) {
  if (node.readOnly) return "var(--purple)";
  if (node.recency === "hot" || node.recency === "fresh") return "rgba(0, 240, 255, 0.8)";
  if (node.recency === "warm") return "rgba(139, 92, 246, 0.6)";
  return "rgba(255, 255, 255, 0.25)";
}

export function short(value) {
  if (!value) return "";
  return value.length > 15 ? `${value.slice(0, 13)}...` : value;
}

export function detectType(name) {
  const lower = name.toLowerCase();
  if (lower.endsWith(".java") || lower.endsWith(".js") || lower.endsWith(".py") || lower.endsWith(".html") || lower.endsWith(".css")) return "code";
  if (lower.endsWith(".pdf")) return "pdf";
  if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".svg") || lower.endsWith(".webp")) return "image";
  return "doc";
}

export function escapeHtml(value) {
  return value.replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#039;"
  })[char]);
}

export function getDescendants(id, children, list = []) {
  const ids = children.get(id) || [];
  ids.forEach(childId => {
    list.push(childId);
    getDescendants(childId, children, list);
  });
  return list;
}

export function el(name, attrs = {}, text = "") {
  const node = document.createElementNS("http://www.w3.org/2000/svg", name);
  const svg = document.querySelector("#graph");
  if (name === "svg") return node;
  if (!svg.querySelector("defs")) {
    const defs = document.createElementNS("http://www.w3.org/2000/svg", "defs");
    defs.innerHTML = `
      <linearGradient id="rootGradient" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="var(--cyan)"/>
        <stop offset="50%" stop-color="var(--purple)"/>
        <stop offset="100%" stop-color="var(--pink)"/>
      </linearGradient>`;
    svg.append(defs);
  }
  for (const [key, value] of Object.entries(attrs)) {
    node.setAttribute(key, value);
  }
  node.textContent = text;
  return node;
}

// Render SVGs to Mindmap canvas
export function render(graph) {
  const svg = document.querySelector("#graph");
  const currentMode = window.currentMode || "map";
  const selectedId = window.selectedId || "vault-root";
  
  svg.replaceChildren();
  
  const viewportG = el("g", { id: "viewport-g" });
  svg.append(viewportG);
  
  const viewport = document.querySelector(".stage-viewport");
  const width = viewport.clientWidth || 800;
  const height = viewport.clientHeight || 600;
  
  const children = new Map();
  for (const edge of graph.edges) {
    if (!children.has(edge.source)) children.set(edge.source, []);
    children.get(edge.source).push(edge.target);
  }
  
  window.currentGraphChildren = children;
  window.currentGraphEdges = graph.edges;

  const positions = new Map();
  let displayNodes = graph.nodes;
  let displayEdges = graph.edges;

  if (currentMode === "timeline") {
    // Timeline Layout
    const sortedNodes = [...graph.nodes].sort((a, b) => {
      if (a.id === "vault-root") return -1;
      if (b.id === "vault-root") return 1;
      return new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0);
    });

    const spacingY = 85;
    const neededHeight = Math.max(height, 160 + sortedNodes.length * spacingY);
    svg.setAttribute("height", neededHeight);
    svg.style.height = `${neededHeight}px`;

    positions.set("vault-root", { x: width * 0.5, y: 110 });
    sortedNodes.forEach((node, index) => {
      if (node.id === "vault-root") return;
      const y = 110 + index * spacingY;
      const x = width * 0.5 + (index % 2 === 0 ? 140 : -140);
      positions.set(node.id, { x, y });
    });

    viewportG.append(el("line", {
      x1: width * 0.5,
      y1: 70,
      x2: width * 0.5,
      y2: 110 + sortedNodes.length * spacingY,
      stroke: "var(--cyan)",
      "stroke-width": 2,
      "stroke-dasharray": "6 4",
      style: "opacity: 0.35;"
    }));

  } else if (currentMode === "focus") {
    // Focus Layout
    svg.setAttribute("height", "100%");
    svg.style.height = "100%";

    displayNodes = graph.nodes.filter(node => {
      if (node.id === selectedId) return true;
      const isParent = graph.edges.some(edge => edge.target === selectedId && edge.source === node.id);
      if (isParent) return true;
      const isChild = graph.edges.some(edge => edge.source === selectedId && edge.target === node.id);
      if (isChild) return true;
      return false;
    });

    displayEdges = graph.edges.filter(edge => {
      return (edge.source === selectedId || edge.target === selectedId) &&
             displayNodes.some(n => n.id === edge.source) &&
             displayNodes.some(n => n.id === edge.target);
    });

    const childrenNodes = displayNodes.filter(n => n.id !== selectedId && graph.edges.some(e => e.source === selectedId && e.target === n.id));
    const parentNode = displayNodes.find(n => n.id !== selectedId && graph.edges.some(e => e.target === selectedId && e.source === n.id));
    const center = { x: width * 0.5, y: height * 0.55 };
    positions.set(selectedId, center);

    if (parentNode) {
      positions.set(parentNode.id, { x: width * 0.5, y: center.y - 150 });
    }

    childrenNodes.forEach((child, index) => {
      const count = childrenNodes.length;
      let angle;
      if (parentNode) {
        const start = Math.PI * 0.15;
        const end = Math.PI * 0.85;
        angle = count > 1 ? start + (index / (count - 1)) * (end - start) : Math.PI * 0.5;
      } else {
        angle = (index / count) * Math.PI * 2;
      }
      positions.set(child.id, {
        x: center.x + Math.cos(angle) * 150,
        y: center.y + Math.sin(angle) * 150
      });
    });

  } else {
    // Map Layout
    svg.setAttribute("height", "100%");
    svg.style.height = "100%";

    const center = { x: width * 0.5, y: height * 0.62 };
    positions.set("vault-root", center);
    layoutChildren("vault-root", center, Math.min(width, height) * 0.25, -Math.PI / 2, 0);

    function layoutChildren(id, origin, radius, angleOffset, depth) {
      const ids = children.get(id) || [];
      ids.forEach((childId, index) => {
        let angle;
        if (depth === 0) {
          angle = angleOffset + (index / ids.length) * Math.PI * 2;
        } else {
          const arc = Math.PI * 0.7;
          const startAngle = angleOffset - arc / 2;
          const step = ids.length > 1 ? arc / (ids.length - 1) : 0;
          angle = startAngle + index * step;
        }
        const drift = index % 2 === 0 ? 1 : 0.82;
        const point = {
          x: origin.x + Math.cos(angle) * radius * drift,
          y: origin.y + Math.sin(angle) * radius * drift
        };
        positions.set(childId, point);
        layoutChildren(childId, point, radius * 0.55, angle, depth + 1);
      });
    }
  }

  // Draw Edges
  for (const edge of displayEdges) {
    const a = positions.get(edge.source);
    const b = positions.get(edge.target);
    if (!a || !b) continue;
    
    let edgeEl;
    if (currentMode === "timeline") {
      const midX = width * 0.5;
      edgeEl = el("path", {
        id: `edge-${edge.source}-${edge.target}`,
        class: `edge ${edge.source === selectedId || edge.target === selectedId ? "hot" : ""}`,
        d: `M ${a.x} ${a.y} L ${midX} ${b.y} L ${b.x} ${b.y}`,
        fill: "none"
      });
    } else {
      edgeEl = el("line", {
        id: `edge-${edge.source}-${edge.target}`,
        class: `edge ${edge.source === selectedId || edge.target === selectedId ? "hot" : ""}`,
        x1: a.x,
        y1: a.y,
        x2: b.x,
        y2: b.y
      });
    }
    viewportG.append(edgeEl);
  }

  // Draw Nodes
  for (const node of displayNodes) {
    const point = positions.get(node.id);
    if (!point) continue;

    const group = el("g", { 
      id: `node-group-${node.id}`,
      class: `node ${node.recency || ""}`,
      transform: `translate(${point.x}, ${point.y})`
    });
    
    drawNodeShape(group, node, node.size || 24);
    
    if (node.readOnly) {
      const badgeG = el("g", {
        transform: `translate(${(node.size || 24) * 0.7}, -${(node.size || 24) * 0.7})`
      });
      badgeG.append(el("circle", {
        r: 7,
        fill: "#090d16",
        stroke: "var(--cyan)",
        "stroke-width": 1.2
      }));
      badgeG.append(el("path", {
        d: "M -3.5 3 A 3.5 3.5 0 0 1 3.5 3 M 0 -0.5 A 1.5 1.5 0 1 1 0 -3.5",
        stroke: "var(--cyan)",
        fill: "none",
        "stroke-width": 0.9,
        "stroke-linecap": "round"
      }));
      group.append(badgeG);
    }
    
    let labelText = short(node.label);
    if (node.shortcut) {
      labelText += ` [Alt+${node.shortcut.toUpperCase()}]`;
    }
    group.append(el("text", { class: "label", x: 0, y: -2 }, labelText));
    
    let subtitleText = node.subtitle || node.type;
    if (node.readOnly && node.ownerEmail) {
      subtitleText = `By ${node.ownerEmail.split('@')[0]}`;
    }
    group.append(el("text", { class: "subtitle", x: 0, y: 14 }, subtitleText));
    
    // Listen to node selection and drag start
    group.addEventListener("mousedown", (e) => {
      e.stopPropagation();
      isDraggingNode = true;
      draggedNodeId = node.id;
      lastMouseX = e.clientX;
      lastMouseY = e.clientY;
      
      if (window.inspect) {
        window.inspect(node, graph);
      }
    });
    
    viewportG.append(group);
  }

  window.activePositions = positions;
  applyTransform();

  // Glide camera smoothly to center on the selected node
  const activePoint = positions.get(selectedId);
  if (activePoint) {
    const targetScale = 1.25;
    const targetX = width * 0.5 - activePoint.x * targetScale;
    const targetY = height * 0.5 - activePoint.y * targetScale;
    animateCamera(targetX, targetY, targetScale, 650);
  }
}

// Attach Drag & Zoom events to the canvas
export function initGraphEvents() {
  const svg = document.querySelector("#graph");
  
  svg.addEventListener("mousedown", (e) => {
    if (e.target.closest(".node")) return;
    isPanning = true;
    lastMouseX = e.clientX;
    lastMouseY = e.clientY;
  });

  svg.addEventListener("mousemove", (e) => {
    if (isPanning) {
      const dx = e.clientX - lastMouseX;
      const dy = e.clientY - lastMouseY;
      transform.x += dx;
      transform.y += dy;
      applyTransform();
      
      lastMouseX = e.clientX;
      lastMouseY = e.clientY;
    } else if (isDraggingNode && draggedNodeId && window.activePositions) {
      const dx = (e.clientX - lastMouseX) / transform.scale;
      const dy = (e.clientY - lastMouseY) / transform.scale;
      
      const childrenMap = window.currentGraphChildren || new Map();
      const dragTargets = [draggedNodeId, ...getDescendants(draggedNodeId, childrenMap)];
      
      dragTargets.forEach(target => {
        const pt = window.activePositions.get(target);
        if (pt) {
          pt.x += dx;
          pt.y += dy;
          
          const nodeG = document.getElementById(`node-group-${target}`);
          if (nodeG) {
            nodeG.setAttribute("transform", `translate(${pt.x}, ${pt.y})`);
          }
        }
      });

      const graphEdges = window.currentGraphEdges || [];
      const currentMode = window.currentMode || "map";
      for (const edge of graphEdges) {
        if (dragTargets.includes(edge.source) || dragTargets.includes(edge.target)) {
          const lineEl = document.getElementById(`edge-${edge.source}-${edge.target}`);
          if (lineEl) {
            const a = window.activePositions.get(edge.source);
            const b = window.activePositions.get(edge.target);
            if (a && b) {
              if (currentMode === "timeline") {
                const viewport = document.querySelector(".stage-viewport");
                const width = viewport.clientWidth || 800;
                const midX = width * 0.5;
                lineEl.setAttribute("d", `M ${a.x} ${a.y} L ${midX} ${b.y} L ${b.x} ${b.y}`);
              } else {
                lineEl.setAttribute("x1", a.x);
                lineEl.setAttribute("y1", a.y);
                lineEl.setAttribute("x2", b.x);
                lineEl.setAttribute("y2", b.y);
              }
            }
          }
        }
      }
      
      lastMouseX = e.clientX;
      lastMouseY = e.clientY;
    }
  });

  const mouseUpOrLeave = () => {
    isPanning = false;
    isDraggingNode = false;
    draggedNodeId = null;
  };

  svg.addEventListener("mouseup", mouseUpOrLeave);
  svg.addEventListener("mouseleave", mouseUpOrLeave);

  svg.addEventListener("wheel", (e) => {
    e.preventDefault();
    const rect = svg.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;
    
    const zoomFactor = e.deltaY > 0 ? 0.9 : 1.1;
    const nextScale = Math.min(Math.max(transform.scale * zoomFactor, 0.15), 4);
    
    transform.x = mouseX - (mouseX - transform.x) * (nextScale / transform.scale);
    transform.y = mouseY - (mouseY - transform.y) * (nextScale / transform.scale);
    transform.scale = nextScale;
    
    applyTransform();
  });
}
