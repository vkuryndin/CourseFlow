import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import ts from 'typescript';

const root = fileURLToPath(new URL('../', import.meta.url));
const src = path.join(root, 'src');
const api = 'shared/api.ts';
const config = ts.readConfigFile(path.join(root, 'tsconfig.json'), ts.sys.readFile);
assert.equal(config.error, undefined, 'Cannot read tsconfig.json');
const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, root);
assert.equal(parsed.errors.length, 0, 'Invalid tsconfig.json');
const slash = value => value.split(path.sep).join('/');
const relative = file => slash(path.relative(src, file));
const exists = file => { try { return statSync(file).isFile(); } catch { return false; } };
const inside = (directory, file) => {
  const r = path.relative(directory, file);
  return r === '' || (!path.isAbsolute(r) && r !== '..' && !r.startsWith(`..${path.sep}`));
};
const codeFile = file => /\.[cm]?[jt]sx?$/i.test(file);
function walk(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) return walk(file);
    return entry.isFile() && codeFile(file) ? [file] : [];
  });
}
const files = walk(src).sort();
const trees = new Map(files.map(file => [file, ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true)]));
const fileSet = new Set(files.map(file => path.normalize(file)));
const imports = [];
const unsupported = [];
const graph = new Map(files.map(file => [relative(file), new Set()]));

function visit(node, fn) { fn(node); ts.forEachChild(node, child => visit(child, fn)); }
function location(tree, node) {
  const { line } = tree.getLineAndCharacterOfPosition(node.getStart(tree));
  return `${relative(tree.fileName)}:${line + 1}`;
}
function literal(node) {
  return node && (ts.isStringLiteralLike(node) || ts.isNoSubstitutionTemplateLiteral(node)) ? node.text : undefined;
}
function importReference(tree, node, expression) {
  const specifier = literal(expression);
  const loc = location(tree, node);
  if (specifier === undefined) {
    unsupported.push(`${loc}: non-literal module reference cannot be checked`);
    return;
  }
  const sourceFile = tree.fileName;
  const resolved = ts.resolveModuleName(specifier, sourceFile, parsed.options, ts.sys).resolvedModule;
  let target = resolved && path.normalize(resolved.resolvedFileName);
  const clean = specifier.split(/[?#]/, 1)[0];
  // Vite static assets are not TypeScript modules. Check their actual path separately.
  if (!target && clean.startsWith('.')) {
    const asset = path.resolve(path.dirname(sourceFile), clean);
    if (exists(asset)) target = asset;
  }
  if (!target && clean.startsWith('/src/')) {
    const candidate = path.resolve(root, `.${clean}`);
    if (exists(candidate)) target = candidate;
  }
  if (!target && (clean.startsWith('.') || clean.startsWith('/') || clean.startsWith('@/'))) {
    unsupported.push(`${loc}: unresolved project import ${specifier}`);
  }
  const local = target && inside(src, target);
  const entry = { from: relative(sourceFile), specifier, loc, target, local, to: local ? relative(target) : undefined };
  imports.push(entry);
  if (local && fileSet.has(target)) graph.get(entry.from).add(entry.to);
}
for (const tree of trees.values()) {
  visit(tree, node => {
    if ((ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) && node.moduleSpecifier) {
      importReference(tree, node, node.moduleSpecifier);
    } else if (ts.isImportEqualsDeclaration(node) && ts.isExternalModuleReference(node.moduleReference)) {
      importReference(tree, node, node.moduleReference.expression);
    } else if (ts.isImportTypeNode(node) && ts.isLiteralTypeNode(node.argument)) {
      importReference(tree, node, node.argument.literal);
    } else if (ts.isCallExpression(node)) {
      const expression = node.expression;
      if (expression.kind === ts.SyntaxKind.ImportKeyword || (ts.isIdentifier(expression) && expression.text === 'require')) {
        importReference(tree, node, node.arguments[0]);
      } else if (ts.isPropertyAccessExpression(expression) && /^glob(?:Eager)?$/.test(expression.name.text)
          && expression.expression.getText(tree) === 'import.meta') {
        unsupported.push(`${location(tree, node)}: import.meta.glob must be added to the architecture scanner before use`);
      }
    }
  });
}
const fail = violations => assert.deepEqual([...new Set(violations)].sort(), []);
const zone = name => name.includes('/') ? name.split('/')[0] : 'root';
// A feature may grow from modules/name.tsx into modules/name/... without crossing a feature boundary.
const feature = name => name.split('/')[1]?.replace(/\.[cm]?[jt]sx?$/i, '');
const allowedModuleAuth = new Set([
  'modules/catalog.tsx', 'modules/dashboard.tsx', 'modules/editor.tsx', 'modules/identity.tsx',
  'modules/learning.tsx', 'modules/schedule.tsx', 'modules/teaching.tsx',
]);
const allowedModuleEdges = new Set([
  'modules/catalog.tsx -> modules/teacher-search.tsx',
  'modules/dashboard.tsx -> modules/teaching.tsx',
  'modules/monitoring.tsx -> modules/system-info.tsx',
  'modules/schedule.tsx -> modules/learning.tsx',
]);

test('Frontend architecture: app, modules, shared and assets respect layer boundaries', () => {
  const violations = [...unsupported];
  for (const tree of trees.values()) {
    const name = relative(tree.fileName);
    for (const diagnostic of tree.parseDiagnostics ?? []) {
      violations.push(`${name}: ${ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n')}`);
    }
    if (!(name === 'main.tsx' || ['app', 'modules', 'shared'].includes(zone(name)))) {
      violations.push(`Unexpected source location: ${name}`);
    }
  }
  for (const required of ['main.tsx', 'app/App.tsx', 'app/auth.tsx', api]) {
    if (!graph.has(required)) violations.push(`Required architecture entry not scanned: ${required}`);
  }
  for (const edge of imports) {
    const { from, to, local, target, loc, specifier } = edge;
    if (!local) {
      // The system information page displays the version from package.json.
      if (target && !inside(path.join(root, 'node_modules'), target)
          && !(from === 'modules/system-info.tsx' && target === path.join(root, 'package.json'))
          && !target.includes(`${path.sep}node_modules${path.sep}`)) {
        // Bare packages may resolve outside the project in a workspace; local relative imports may not.
        if (specifier.startsWith('.') || specifier.startsWith('/')) violations.push(`${loc}: source imports outside src: ${specifier}`);
      }
      continue;
    }
    const origin = zone(from), destination = zone(to);
    let permitted = false;
    if (origin === 'root') permitted = from === 'main.tsx' && (destination === 'app' || to === 'style.css');
    if (origin === 'app') permitted = ['app', 'modules', 'shared', 'assets'].includes(destination)
      && !(from === 'app/auth.tsx' && destination === 'modules');
    if (origin === 'shared') permitted = ['shared', 'assets'].includes(destination);
    if (origin === 'modules') permitted = ['shared', 'assets'].includes(destination)
      || (to === 'app/auth.tsx' && allowedModuleAuth.has(from))
      || (destination === 'modules' && (feature(from) === feature(to) || allowedModuleEdges.has(`${from} -> ${to}`)));
    if (destination === 'assets' && codeFile(target)) permitted = false;
    if (!permitted) violations.push(`${loc}: forbidden architecture edge ${from} -> ${to}`);
  }
  fail(violations);
});

test('Frontend architecture: no cycles in local imports, re-exports or literal dynamic imports', () => {
  const violations = [...unsupported];
  const active = new Set();
  const complete = new Set();
  const stack = [];
  function dfs(file) {
    if (active.has(file)) {
      violations.push(`Cycle: ${[...stack.slice(stack.indexOf(file)), file].join(' -> ')}`);
      return;
    }
    if (complete.has(file)) return;
    active.add(file); stack.push(file);
    for (const dependency of graph.get(file) ?? []) dfs(dependency);
    stack.pop(); active.delete(file); complete.add(file);
  }
  for (const file of graph.keys()) dfs(file);
  fail(violations);
});

const httpPackages = [
  'axios', 'ky', 'ofetch', 'got', 'superagent', 'node-fetch', 'cross-fetch', 'isomorphic-fetch',
  'undici', 'wretch', 'fetch-retry', 'graphql-request', 'apollo-boost', '@apollo/client',
  'http', 'https', 'http2', 'node:http', 'node:https', 'node:http2', 'ws', 'socket.io-client',
];
const transport = new Set(['fetch', 'XMLHttpRequest', 'WebSocket', 'EventSource', 'sendBeacon']);
function transportIdentifier(node) {
  if (!ts.isIdentifier(node) || !transport.has(node.text)) return false;
  const parent = node.parent;
  if (ts.isPropertyAccessExpression(parent) && parent.name === node) {
    return true; // Also rejects direct transport calls through a local alias of window/globalThis.
  }
  // Detect aliases of browser transports as well as direct calls and constructors.
  return true;
}

test('Frontend architecture: browser HTTP transports are centralized in shared/api.ts', () => {
  const violations = [...unsupported];
  for (const edge of imports) {
    if (edge.from !== api && httpPackages.some(pkg => edge.specifier === pkg || edge.specifier.startsWith(`${pkg}/`))) {
      violations.push(`${edge.loc}: direct HTTP client dependency ${edge.specifier}; use shared/api.ts`);
    }
  }
  for (const tree of trees.values()) {
    if (relative(tree.fileName) === api) continue;
    visit(tree, node => {
      if (transportIdentifier(node)) violations.push(`${location(tree, node)}: direct browser transport ${node.text}`);
      if (ts.isElementAccessExpression(node) && transport.has(literal(node.argumentExpression))) {
        violations.push(`${location(tree, node)}: direct browser transport ${node.getText(tree)}`);
      }
      if (ts.isBindingElement(node) && transport.has((node.propertyName ?? node.name).getText(tree))) {
        violations.push(`${location(tree, node)}: destructured browser transport`);
      }
    });
  }
  if (!imports.some(edge => edge.from === api && edge.specifier === 'axios')) {
    violations.push('The central API client must own the Axios import');
  }
  fail(violations);
});
