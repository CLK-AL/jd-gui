/**
 * gui Collaborative Editing Server
 *
 * Uses Hocuspocus (Yjs) for real-time collaborative code editing.
 * Supports Monaco and Ace editors via y-monaco and y-ace bindings.
 *
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * Licensed under GPLv3.
 */

import { Server } from '@hocuspocus/server';
import { Logger } from '@hocuspocus/extension-logger';
import { Throttle } from '@hocuspocus/extension-throttle';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { createServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import * as Y from 'yjs';
import dotenv from 'dotenv';
import { FileWatcher, FileChangeEvent, getFileIcon } from './watcher.js';

dotenv.config();

// File watcher instance
const fileWatcher = new FileWatcher({
  extensions: ['.java', '.class', '.jar', '.kt', '.scala', '.groovy', '.xml', '.json', '.properties'],
  debounceMs: 150,
  readContent: true,
});

// WebSocket clients for file change notifications
const watchClients = new Set<WebSocket>();

const PORT = parseInt(process.env.PORT || '3000', 10);
const WS_PORT = parseInt(process.env.WS_PORT || '3001', 10);
const HOST = process.env.HOST || '0.0.0.0';

// Document storage (in-memory for demo, use Redis/Postgres in production)
const documents = new Map<string, Y.Doc>();

/**
 * Hocuspocus Server Configuration
 */
const hocuspocus = Server.configure({
  name: 'gui-collab',
  port: WS_PORT,
  address: HOST,

  extensions: [
    new Logger({
      log: (message) => console.log(`[Hocuspocus] ${message}`),
      onLoadDocument: true,
      onStoreDocument: true,
      onConnect: true,
      onDisconnect: true,
    }),
    new Throttle({
      throttle: 50,      // Max messages per connection per second
      banTime: 5000,     // Ban time in ms
    }),
  ],

  async onConnect(data) {
    console.log(`Client connected to document: ${data.documentName}`);
    return true;
  },

  async onDisconnect(data) {
    console.log(`Client disconnected from document: ${data.documentName}`);
  },

  async onLoadDocument(data) {
    // Load existing document or create new one
    const docName = data.documentName;
    console.log(`Loading document: ${docName}`);

    // Initialize document structure for code editor
    const doc = data.document;

    // Create shared types for collaborative editing
    if (!doc.getMap('meta').has('initialized')) {
      // Monaco/Ace text content
      doc.getText('content');

      // Cursor positions and selections
      doc.getArray('cursors');

      // Document metadata
      const meta = doc.getMap('meta');
      meta.set('initialized', true);
      meta.set('language', 'java');
      meta.set('fileName', '');
      meta.set('createdAt', new Date().toISOString());
    }

    return doc;
  },

  async onStoreDocument(data) {
    // Persist document to storage (implement for production)
    console.log(`Storing document: ${data.documentName}`);
    documents.set(data.documentName, data.document);
  },

  async onAuthenticate(data) {
    // Implement authentication logic
    // For demo, allow all connections
    const { token } = data;

    if (token === 'invalid') {
      throw new Error('Authentication failed');
    }

    // Return user data to be available in other hooks
    return {
      user: {
        id: data.connection?.readOnly ? 'anonymous' : 'user-' + Math.random().toString(36).substr(2, 9),
        name: 'Anonymous',
        color: getRandomColor(),
      },
    };
  },

  async onStateless(data) {
    // Handle stateless messages (e.g., RPC calls)
    const { payload, document } = data;

    try {
      const message = JSON.parse(payload);

      switch (message.type) {
        case 'getLanguage':
          return JSON.stringify({
            type: 'language',
            language: document.getMap('meta').get('language'),
          });

        case 'setLanguage':
          document.getMap('meta').set('language', message.language);
          return JSON.stringify({ type: 'ok' });

        case 'getContent':
          return JSON.stringify({
            type: 'content',
            content: document.getText('content').toString(),
          });

        default:
          return JSON.stringify({ type: 'error', message: 'Unknown message type' });
      }
    } catch (error) {
      return JSON.stringify({ type: 'error', message: 'Invalid payload' });
    }
  },
});

/**
 * Express REST API Server
 */
const app = express();

// Security middleware
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'", "'unsafe-inline'", "'unsafe-eval'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      connectSrc: ["'self'", `ws://localhost:${WS_PORT}`, `wss://localhost:${WS_PORT}`],
    },
  },
}));

app.use(cors({
  origin: process.env.CORS_ORIGIN || '*',
  credentials: true,
}));

app.use(express.json({ limit: '10mb' }));

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // Limit each IP to 100 requests per window
});
app.use(limiter);

/**
 * API Routes
 */

// Health check
app.get('/health', (req, res) => {
  res.json({
    status: 'healthy',
    server: 'gui-collab',
    version: '2026.2.2',
    uptime: process.uptime(),
    documents: documents.size,
  });
});

// Get document list
app.get('/api/documents', (req, res) => {
  const docList = Array.from(documents.entries()).map(([name, doc]) => ({
    name,
    language: doc.getMap('meta').get('language'),
    createdAt: doc.getMap('meta').get('createdAt'),
  }));
  res.json(docList);
});

// Create new document
app.post('/api/documents', (req, res) => {
  const { name, content, language } = req.body;

  if (!name) {
    return res.status(400).json({ error: 'Document name required' });
  }

  if (documents.has(name)) {
    return res.status(409).json({ error: 'Document already exists' });
  }

  const doc = new Y.Doc();
  doc.getText('content').insert(0, content || '');
  const meta = doc.getMap('meta');
  meta.set('initialized', true);
  meta.set('language', language || 'java');
  meta.set('fileName', name);
  meta.set('createdAt', new Date().toISOString());

  documents.set(name, doc);

  res.status(201).json({
    name,
    language: language || 'java',
    websocket: `ws://${HOST}:${WS_PORT}/${name}`,
  });
});

// Get document content
app.get('/api/documents/:name', (req, res) => {
  const { name } = req.params;
  const doc = documents.get(name);

  if (!doc) {
    return res.status(404).json({ error: 'Document not found' });
  }

  res.json({
    name,
    content: doc.getText('content').toString(),
    language: doc.getMap('meta').get('language'),
    createdAt: doc.getMap('meta').get('createdAt'),
  });
});

// Delete document
app.delete('/api/documents/:name', (req, res) => {
  const { name } = req.params;

  if (!documents.has(name)) {
    return res.status(404).json({ error: 'Document not found' });
  }

  documents.delete(name);
  res.status(204).send();
});

// Upload decompiled class file
app.post('/api/decompile', async (req, res) => {
  const { classData, className } = req.body;

  if (!classData || !className) {
    return res.status(400).json({ error: 'classData and className required' });
  }

  // In production, this would call the decompiler service
  // For now, return placeholder
  const docName = className.replace(/\//g, '.') + '-' + Date.now();

  const doc = new Y.Doc();
  doc.getText('content').insert(0, `// Decompiled: ${className}\n// TODO: Implement decompiler integration\n`);
  const meta = doc.getMap('meta');
  meta.set('initialized', true);
  meta.set('language', 'java');
  meta.set('fileName', className);
  meta.set('createdAt', new Date().toISOString());

  documents.set(docName, doc);

  res.status(201).json({
    documentName: docName,
    websocket: `ws://${HOST}:${WS_PORT}/${docName}`,
  });
});

/**
 * Watch Folder API Routes
 */

// Get watched directories
app.get('/api/watch', (req, res) => {
  res.json({
    directories: fileWatcher.getWatchedDirs(),
    fileCount: fileWatcher.getWatchedFiles().length,
  });
});

// Start watching a directory
app.post('/api/watch', async (req, res) => {
  const { path } = req.body;

  if (!path) {
    return res.status(400).json({ error: 'Directory path required' });
  }

  try {
    await fileWatcher.watch(path);
    res.status(201).json({
      message: `Now watching: ${path}`,
      path,
      websocket: `ws://${HOST}:${WS_PORT + 1}/watch`,
    });
  } catch (error) {
    res.status(500).json({
      error: 'Failed to watch directory',
      details: error instanceof Error ? error.message : 'Unknown error',
    });
  }
});

// Stop watching a directory
app.delete('/api/watch', (req, res) => {
  const { path } = req.body;

  if (!path) {
    return res.status(400).json({ error: 'Directory path required' });
  }

  fileWatcher.unwatch(path);
  res.status(200).json({ message: `Stopped watching: ${path}` });
});

// Get all watched files
app.get('/api/watch/files', (req, res) => {
  const files = fileWatcher.getWatchedFiles().map(filePath => ({
    path: filePath,
    icon: getFileIcon(filePath),
  }));
  res.json(files);
});

// Stop all watchers
app.post('/api/watch/stop-all', (req, res) => {
  fileWatcher.unwatchAll();
  res.json({ message: 'All watchers stopped' });
});

/**
 * Utility Functions
 */
function getRandomColor(): string {
  const colors = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4',
    '#FFEAA7', '#DDA0DD', '#98D8C8', '#F7DC6F',
    '#BB8FCE', '#85C1E9', '#F8B500', '#00CED1',
  ];
  return colors[Math.floor(Math.random() * colors.length)];
}

/**
 * Start Servers
 */
async function main() {
  // Start Hocuspocus WebSocket server
  await hocuspocus.listen();
  console.log(`🔌 Hocuspocus WebSocket server running on ws://${HOST}:${WS_PORT}`);

  // Start Express HTTP server
  const httpServer = createServer(app);

  // Create WebSocket server for file watch notifications
  const watchWss = new WebSocketServer({ port: WS_PORT + 1 });

  watchWss.on('connection', (ws) => {
    console.log('📡 Watch client connected');
    watchClients.add(ws);

    // Send current watched directories
    ws.send(JSON.stringify({
      type: 'init',
      directories: fileWatcher.getWatchedDirs(),
      files: fileWatcher.getWatchedFiles().map(path => ({
        path,
        icon: getFileIcon(path),
      })),
    }));

    ws.on('close', () => {
      watchClients.delete(ws);
      console.log('📡 Watch client disconnected');
    });

    ws.on('message', async (message) => {
      try {
        const data = JSON.parse(message.toString());

        switch (data.type) {
          case 'watch':
            await fileWatcher.watch(data.path);
            ws.send(JSON.stringify({ type: 'watching', path: data.path }));
            break;

          case 'unwatch':
            fileWatcher.unwatch(data.path);
            ws.send(JSON.stringify({ type: 'unwatched', path: data.path }));
            break;

          case 'getFiles':
            ws.send(JSON.stringify({
              type: 'files',
              files: fileWatcher.getWatchedFiles().map(path => ({
                path,
                icon: getFileIcon(path),
              })),
            }));
            break;
        }
      } catch (error) {
        ws.send(JSON.stringify({
          type: 'error',
          message: error instanceof Error ? error.message : 'Unknown error',
        }));
      }
    });
  });

  // Set up file watcher event handlers
  fileWatcher.on('change', (event: FileChangeEvent) => {
    const message = JSON.stringify({
      type: 'fileChange',
      event: {
        ...event,
        icon: getFileIcon(event.path),
      },
    });

    // Broadcast to all connected watch clients
    watchClients.forEach((client) => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(message);
      }
    });

    // Auto-create/update document for changed files
    if (event.type === 'change' || event.type === 'add') {
      const docName = event.relativePath.replace(/\//g, '.');

      if (event.content) {
        let doc = documents.get(docName);

        if (!doc) {
          doc = new Y.Doc();
          const meta = doc.getMap('meta');
          meta.set('initialized', true);
          meta.set('language', getLanguageFromExtension(event.extension));
          meta.set('fileName', event.relativePath);
          meta.set('filePath', event.path);
          meta.set('createdAt', new Date().toISOString());
          documents.set(docName, doc);
        }

        // Update content
        const ytext = doc.getText('content');
        doc.transact(() => {
          ytext.delete(0, ytext.length);
          ytext.insert(0, event.content || '');
        });

        doc.getMap('meta').set('lastModified', event.timestamp);

        console.log(`📄 Document updated: ${docName}`);
      }
    }
  });

  fileWatcher.on('watching', ({ path }) => {
    const message = JSON.stringify({ type: 'watching', path });
    watchClients.forEach((client) => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(message);
      }
    });
  });

  fileWatcher.on('unwatched', ({ path }) => {
    const message = JSON.stringify({ type: 'unwatched', path });
    watchClients.forEach((client) => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(message);
      }
    });
  });

  fileWatcher.on('error', ({ path, error }) => {
    const message = JSON.stringify({
      type: 'watchError',
      path,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    watchClients.forEach((client) => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(message);
      }
    });
  });

  httpServer.listen(PORT, HOST, () => {
    console.log(`🚀 gui Collab API server running on http://${HOST}:${PORT}`);
    console.log(`📁 File watch WebSocket running on ws://${HOST}:${WS_PORT + 1}`);
    console.log(`📝 Health check: http://${HOST}:${PORT}/health`);
  });
}

/**
 * Get language from file extension
 */
function getLanguageFromExtension(ext: string): string {
  const languages: Record<string, string> = {
    '.java': 'java',
    '.kt': 'kotlin',
    '.kts': 'kotlin',
    '.scala': 'scala',
    '.groovy': 'groovy',
    '.xml': 'xml',
    '.json': 'json',
    '.yaml': 'yaml',
    '.yml': 'yaml',
    '.properties': 'properties',
    '.js': 'javascript',
    '.ts': 'typescript',
    '.py': 'python',
    '.rb': 'ruby',
    '.go': 'go',
    '.rs': 'rust',
    '.c': 'c',
    '.cpp': 'cpp',
    '.h': 'c',
    '.md': 'markdown',
    '.sql': 'sql',
    '.sh': 'shell',
    '.html': 'html',
    '.css': 'css',
  };
  return languages[ext.toLowerCase()] || 'text';
}

main().catch(console.error);
