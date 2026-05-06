import { execSync, spawnSync } from 'node:child_process';
import path from 'node:path';

const staticExtensions = new Set([
  '.css',
  '.scss',
  '.sass',
  '.less',
  '.svg',
  '.png',
  '.jpg',
  '.jpeg',
  '.gif',
  '.webp',
  '.ico',
  '.woff',
  '.woff2',
  '.ttf',
  '.eot',
  '.otf',
  '.md',
  '.mdx',
  '.txt',
  '.map',
  '.json',
  '.webmanifest',
  '.mp4',
  '.webm',
  '.mp3',
  '.wav',
]);

const markupExtensions = new Set(['.ts', '.tsx', '.js', '.jsx', '.html']);

const configDependencyPatterns = [
  /^frontend\/package\.json$/,
  /^frontend\/package-lock\.json$/,
  /^frontend\/pnpm-lock\.yaml$/,
  /^frontend\/yarn\.lock$/,
  /^frontend\/tsconfig(\..+)?\.json$/,
  /^frontend\/vite\.config\.(ts|js|mjs|cjs)$/,
  /^frontend\/eslint\.config\.(js|mjs|cjs)$/,
  /^frontend\/prettier\.config\.(js|cjs|mjs|json)$/,
  /^frontend\/postcss\.config\.(js|cjs|mjs)$/,
  /^frontend\/tailwind\.config\.(js|cjs|mjs|ts)$/,
  /^frontend\/\.env(\..+)?$/,
  /^frontend\/\.npmrc$/,
  /^frontend\/\.nvmrc$/,
  /^frontend\/\.browserslistrc$/,
];

const typeApiArchPatterns = [
  /^frontend\/src\/types\//,
  /^frontend\/src\/schemas\//,
  /^frontend\/src\/api\//,
  /^frontend\/src\/context\//,
  /^frontend\/src\/hooks\//,
  /^frontend\/src\/lib\//,
  /^frontend\/src\/layouts\//,
  /^frontend\/src\/config\//,
  /^frontend\/src\/constants\//,
  /^frontend\/src\/errors\//,
  /^frontend\/src\/router\.tsx$/,
  /^frontend\/src\/main\.tsx$/,
  /^frontend\/src\/App\.tsx$/,
  /^frontend\/src\/vite-env\.d\.ts$/,
];

const staticDirs = ['frontend/public/', 'frontend/src/assets/'];

function getGitRoot() {
  try {
    return execSync('git rev-parse --show-toplevel', {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    }).trim();
  } catch {
    return null;
  }
}

function listChangedFiles(rootDir) {
  const commands = [
    'git diff --name-only --diff-filter=ACMR',
    'git diff --name-only --cached --diff-filter=ACMR',
    'git ls-files --others --exclude-standard',
  ];

  const files = new Set();

  for (const command of commands) {
    try {
      const output = execSync(command, {
        cwd: rootDir,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
      }).trim();

      if (!output) continue;
      output
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean)
        .forEach((line) => {
          files.add(line.replace(/\\/g, '/'));
        });
    } catch {
      // Ignore git errors and continue with other commands.
    }
  }

  return [...files];
}

function isTypeApiArchitectureChange(filePath) {
  if (filePath.endsWith('.d.ts')) return true;
  return typeApiArchPatterns.some((pattern) => pattern.test(filePath));
}

function isConfigOrDependencyChange(filePath) {
  return configDependencyPatterns.some((pattern) => pattern.test(filePath));
}

function isStaticChange(filePath) {
  if (staticDirs.some((dir) => filePath.startsWith(dir))) return true;
  const ext = path.extname(filePath).toLowerCase();
  return staticExtensions.has(ext);
}

function isBehaviorJsonChange(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  if (ext !== '.json') return false;
  if (!filePath.startsWith('frontend/src/')) return false;
  return !staticDirs.some((dir) => filePath.startsWith(dir));
}

function isMarkupOrLogicChange(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  return markupExtensions.has(ext);
}

function runCommand(command, cwd) {
  const result = spawnSync(command, {
    cwd,
    stdio: 'inherit',
    shell: true,
  });

  if (result.status !== 0) {
    console.error(`[hook] Command failed: ${command}`);
    process.exit(2);
  }
}

const rootDir = getGitRoot();
if (!rootDir) process.exit(0);

const changedFiles = listChangedFiles(rootDir);
const frontendFiles = changedFiles.filter((file) => file.startsWith('frontend/'));

if (frontendFiles.length === 0) process.exit(0);

let needsLint = false;
let needsTypecheck = false;

for (const file of frontendFiles) {
  if (isConfigOrDependencyChange(file)) {
    needsLint = true;
    needsTypecheck = true;
    continue;
  }

  if (isTypeApiArchitectureChange(file)) {
    needsLint = true;
    needsTypecheck = true;
    continue;
  }

  if (isBehaviorJsonChange(file)) {
    needsLint = true;
    continue;
  }

  if (isStaticChange(file)) {
    continue;
  }

  if (isMarkupOrLogicChange(file)) {
    needsLint = true;
    continue;
  }

  needsLint = true;
}

if (!needsLint && !needsTypecheck) process.exit(0);

const frontendDir = path.join(rootDir, 'frontend');

if (needsLint) {
  console.log('[hook] Running frontend lint...');
  runCommand('npm run lint', frontendDir);
}

if (needsTypecheck) {
  console.log('[hook] Running frontend type-check...');
  runCommand('npm run type-check', frontendDir);
}
