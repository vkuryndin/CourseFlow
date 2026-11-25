import { readFileSync, writeFileSync } from 'node:fs';

const version = readFileSync(new URL('../../VERSION', import.meta.url), 'utf8').trim();
if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(version)) {
  throw new Error(`Invalid release version in VERSION: ${version}`);
}
const check = process.argv.includes('--check');
for (const name of ['package.json', 'package-lock.json']) {
  const path = new URL(`../${name}`, import.meta.url);
  const raw = readFileSync(path, 'utf8');
  const data = JSON.parse(raw);
  const root = name === 'package-lock.json' ? data.packages[''] : data;
  if (data.version === version && root.version === version) continue;
  if (check) throw new Error(`${name} is out of sync with VERSION (${version}). Run node frontend/scripts/sync-version.mjs from the repository root.`);
  data.version = version;
  root.version = version;
  const output = JSON.stringify(data, null, 2) + '\n';
  writeFileSync(path, raw.includes('\r\n') ? output.replaceAll('\n', '\r\n') : output);
}
console.log(`CourseFlow version: ${version}`);
