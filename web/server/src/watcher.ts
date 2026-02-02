/**
 * JD-GUI File Watcher Service
 *
 * Monitors directories for file changes and broadcasts updates
 * to connected clients via WebSocket.
 *
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * Licensed under GPLv3.
 */

import { watch, FSWatcher, Stats } from 'fs';
import { readFile, stat, readdir } from 'fs/promises';
import { join, extname, relative, basename } from 'path';
import { EventEmitter } from 'events';

export interface FileChangeEvent {
  type: 'add' | 'change' | 'unlink' | 'addDir' | 'unlinkDir';
  path: string;
  relativePath: string;
  extension: string;
  content?: string;
  stats?: Stats;
  timestamp: number;
}

export interface WatchOptions {
  extensions?: string[];
  ignored?: RegExp[];
  depth?: number;
  debounceMs?: number;
  readContent?: boolean;
}

const DEFAULT_OPTIONS: WatchOptions = {
  extensions: ['.java', '.class', '.jar', '.kt', '.scala', '.groovy', '.xml', '.json', '.properties'],
  ignored: [/node_modules/, /\.git/, /\.idea/, /build/, /target/, /out/],
  depth: 10,
  debounceMs: 100,
  readContent: true,
};

/**
 * File Watcher class that monitors directories for changes
 */
export class FileWatcher extends EventEmitter {
  private watchers: Map<string, FSWatcher> = new Map();
  private options: WatchOptions;
  private debounceTimers: Map<string, NodeJS.Timeout> = new Map();
  private watchedPaths: Set<string> = new Set();

  constructor(options: Partial<WatchOptions> = {}) {
    super();
    this.options = { ...DEFAULT_OPTIONS, ...options };
  }

  /**
   * Start watching a directory
   */
  async watch(dirPath: string): Promise<void> {
    if (this.watchers.has(dirPath)) {
      console.log(`Already watching: ${dirPath}`);
      return;
    }

    try {
      const stats = await stat(dirPath);
      if (!stats.isDirectory()) {
        throw new Error(`Not a directory: ${dirPath}`);
      }

      // Initial scan
      await this.scanDirectory(dirPath, dirPath, 0);

      // Set up watcher
      const watcher = watch(dirPath, { recursive: true }, (eventType, filename) => {
        if (filename) {
          this.handleFileChange(dirPath, filename, eventType);
        }
      });

      watcher.on('error', (error) => {
        console.error(`Watcher error for ${dirPath}:`, error);
        this.emit('error', { path: dirPath, error });
      });

      this.watchers.set(dirPath, watcher);
      this.emit('watching', { path: dirPath });
      console.log(`📁 Watching directory: ${dirPath}`);

    } catch (error) {
      console.error(`Failed to watch ${dirPath}:`, error);
      throw error;
    }
  }

  /**
   * Stop watching a directory
   */
  unwatch(dirPath: string): void {
    const watcher = this.watchers.get(dirPath);
    if (watcher) {
      watcher.close();
      this.watchers.delete(dirPath);

      // Clean up watched paths
      for (const path of this.watchedPaths) {
        if (path.startsWith(dirPath)) {
          this.watchedPaths.delete(path);
        }
      }

      this.emit('unwatched', { path: dirPath });
      console.log(`📁 Stopped watching: ${dirPath}`);
    }
  }

  /**
   * Stop all watchers
   */
  unwatchAll(): void {
    for (const [path, watcher] of this.watchers) {
      watcher.close();
      this.emit('unwatched', { path });
    }
    this.watchers.clear();
    this.watchedPaths.clear();
    this.debounceTimers.forEach(timer => clearTimeout(timer));
    this.debounceTimers.clear();
    console.log('📁 Stopped all watchers');
  }

  /**
   * Get list of watched directories
   */
  getWatchedDirs(): string[] {
    return Array.from(this.watchers.keys());
  }

  /**
   * Get all watched files
   */
  getWatchedFiles(): string[] {
    return Array.from(this.watchedPaths);
  }

  /**
   * Scan directory recursively
   */
  private async scanDirectory(basePath: string, dirPath: string, depth: number): Promise<void> {
    if (depth > (this.options.depth || 10)) {
      return;
    }

    try {
      const entries = await readdir(dirPath, { withFileTypes: true });

      for (const entry of entries) {
        const fullPath = join(dirPath, entry.name);
        const relativePath = relative(basePath, fullPath);

        // Check if should be ignored
        if (this.shouldIgnore(relativePath)) {
          continue;
        }

        if (entry.isDirectory()) {
          this.emit('change', {
            type: 'addDir',
            path: fullPath,
            relativePath,
            extension: '',
            timestamp: Date.now(),
          } as FileChangeEvent);

          await this.scanDirectory(basePath, fullPath, depth + 1);
        } else if (entry.isFile()) {
          const ext = extname(entry.name);

          if (this.shouldWatchExtension(ext)) {
            this.watchedPaths.add(fullPath);

            const event: FileChangeEvent = {
              type: 'add',
              path: fullPath,
              relativePath,
              extension: ext,
              timestamp: Date.now(),
            };

            if (this.options.readContent && this.isTextFile(ext)) {
              try {
                event.content = await readFile(fullPath, 'utf-8');
              } catch (e) {
                // Binary file or read error
              }
            }

            try {
              event.stats = await stat(fullPath);
            } catch (e) {
              // Stat failed
            }

            this.emit('change', event);
          }
        }
      }
    } catch (error) {
      console.error(`Error scanning ${dirPath}:`, error);
    }
  }

  /**
   * Handle file change event with debouncing
   */
  private handleFileChange(basePath: string, filename: string, eventType: string): void {
    const fullPath = join(basePath, filename);
    const relativePath = relative(basePath, fullPath);

    // Check if should be ignored
    if (this.shouldIgnore(relativePath)) {
      return;
    }

    const ext = extname(filename);
    if (!this.shouldWatchExtension(ext) && ext !== '') {
      return;
    }

    // Debounce rapid changes
    const debounceKey = fullPath;
    const existingTimer = this.debounceTimers.get(debounceKey);
    if (existingTimer) {
      clearTimeout(existingTimer);
    }

    const timer = setTimeout(async () => {
      this.debounceTimers.delete(debounceKey);
      await this.processFileChange(basePath, fullPath, relativePath, ext);
    }, this.options.debounceMs || 100);

    this.debounceTimers.set(debounceKey, timer);
  }

  /**
   * Process file change after debounce
   */
  private async processFileChange(
    basePath: string,
    fullPath: string,
    relativePath: string,
    ext: string
  ): Promise<void> {
    try {
      const stats = await stat(fullPath);
      const isDir = stats.isDirectory();

      const event: FileChangeEvent = {
        type: isDir ? 'addDir' : 'change',
        path: fullPath,
        relativePath,
        extension: ext,
        stats,
        timestamp: Date.now(),
      };

      if (!isDir && this.options.readContent && this.isTextFile(ext)) {
        try {
          event.content = await readFile(fullPath, 'utf-8');
        } catch (e) {
          // Binary file or read error
        }
      }

      if (!isDir) {
        this.watchedPaths.add(fullPath);
      }

      this.emit('change', event);

    } catch (error) {
      // File was deleted
      const wasWatched = this.watchedPaths.has(fullPath);
      this.watchedPaths.delete(fullPath);

      if (wasWatched || ext === '') {
        const event: FileChangeEvent = {
          type: ext === '' ? 'unlinkDir' : 'unlink',
          path: fullPath,
          relativePath,
          extension: ext,
          timestamp: Date.now(),
        };

        this.emit('change', event);
      }
    }
  }

  /**
   * Check if path should be ignored
   */
  private shouldIgnore(relativePath: string): boolean {
    if (!this.options.ignored) {
      return false;
    }
    return this.options.ignored.some(pattern => pattern.test(relativePath));
  }

  /**
   * Check if extension should be watched
   */
  private shouldWatchExtension(ext: string): boolean {
    if (!this.options.extensions || this.options.extensions.length === 0) {
      return true;
    }
    return this.options.extensions.includes(ext.toLowerCase());
  }

  /**
   * Check if file is a text file that can be read
   */
  private isTextFile(ext: string): boolean {
    const textExtensions = [
      '.java', '.kt', '.scala', '.groovy', '.xml', '.json',
      '.properties', '.yaml', '.yml', '.txt', '.md', '.gradle',
      '.html', '.css', '.js', '.ts', '.py', '.rb', '.go', '.rs'
    ];
    return textExtensions.includes(ext.toLowerCase());
  }
}

/**
 * Get emoji icon for file extension
 */
export function getFileIcon(filename: string): string {
  const ext = extname(filename).toLowerCase();
  const name = basename(filename).toLowerCase();

  // Special files
  if (name === 'package.json') return '📦';
  if (name === 'dockerfile') return '🐳';
  if (name === 'makefile') return '🔧';
  if (name === '.gitignore') return '🙈';
  if (name === 'readme.md') return '📖';
  if (name === 'license') return '📜';

  // By extension
  const icons: Record<string, string> = {
    '.java': '☕',
    '.class': '⚙️',
    '.jar': '📦',
    '.kt': '🟣',
    '.kts': '🟣',
    '.scala': '🔴',
    '.groovy': '💎',
    '.xml': '📋',
    '.json': '📋',
    '.yaml': '📋',
    '.yml': '📋',
    '.properties': '⚙️',
    '.gradle': '🐘',
    '.html': '🌐',
    '.css': '🎨',
    '.js': '💛',
    '.ts': '💙',
    '.py': '🐍',
    '.rb': '💎',
    '.go': '🐹',
    '.rs': '🦀',
    '.c': '©️',
    '.cpp': '➕',
    '.h': '📑',
    '.md': '📝',
    '.txt': '📄',
    '.sql': '🗃️',
    '.sh': '🐚',
    '.bat': '🦇',
    '.png': '🖼️',
    '.jpg': '🖼️',
    '.gif': '🖼️',
    '.svg': '🎨',
    '.zip': '🗜️',
    '.tar': '🗜️',
    '.gz': '🗜️',
  };

  return icons[ext] || '📄';
}

/**
 * Create a singleton watcher instance
 */
let globalWatcher: FileWatcher | null = null;

export function getGlobalWatcher(): FileWatcher {
  if (!globalWatcher) {
    globalWatcher = new FileWatcher();
  }
  return globalWatcher;
}

export default FileWatcher;
