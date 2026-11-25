// Run after npm run build. Measures static startup dependencies and verifies
// that expensive features have not leaked into anonymous entry routes.
const fs = require('node:fs');
const path = require('node:path');
const { gzipSync } = require('node:zlib');
const assert = require('node:assert/strict');
const dist = path.resolve(__dirname, '../dist');
const manifestPath = path.join(dist, '.vite', 'manifest.json');
const baseline = { raw: 2036970, gzip: 611680 }; // v1.2.7.1 Vite log, decimal kB

function closure(manifest, roots) {
    const seen = new Set();
    function visit(key) {
        if (seen.has(key)) return;
        assert.ok(manifest[key], `Missing manifest entry: ${key}`);
        seen.add(key);
        for (const dependency of manifest[key].imports || []) visit(dependency);
    }
    roots.forEach(visit);
    return seen;
}
function filesFor(manifest, roots) {
    return [...new Set([...closure(manifest, roots)].map(k => manifest[k].file))]
        .filter(f => /\.m?js$/.test(f));
}
function measure(files, directory) {
    return files.reduce((sum, file) => {
        const bytes = fs.readFileSync(path.join(directory, file));
        return { raw: sum.raw + bytes.length, gzip: sum.gzip + gzipSync(bytes).length };
    }, { raw: 0, gzip: 0 });
}
function analyze(manifest) {
    const entry = Object.keys(manifest).find(k => manifest[k].isEntry && /(?:^|\/)index\.html$/.test(k));
    assert.ok(entry, 'Missing index.html entry');
    const lookup = source => {
        const key = Object.keys(manifest).find(k => k === source || manifest[k].src === source);
        assert.ok(key, `Missing expected lazy module: ${source}`);
        assert.ok(manifest[key].isDynamicEntry, `${source} is not a dynamic entry`);
        return key;
    };
    const catalog = lookup('src/modules/catalog.tsx');
    const identity = lookup('src/modules/identity.tsx');
    const expensive = [
        'src/modules/schedule.tsx',
        'src/modules/editor.tsx',
        'src/modules/monitoring.tsx',
        'src/shared/rich-editor.tsx',
        'src/modules/dashboard/admin-dashboard.tsx',
    ].map(lookup);
    const groups = {
        shell: filesFor(manifest, [entry]),
        catalog: filesFor(manifest, [entry, catalog]),
        login: filesFor(manifest, [entry, identity]),
        total: [...new Set(Object.values(manifest).map(m => m.file))].filter(f => /\.m?js$/.test(f)),
    };
    for (const name of ['shell', 'catalog', 'login']) {
        for (const key of expensive) {
            assert.ok(!groups[name].includes(manifest[key].file), `${key} leaked into ${name}`);
        }
    }
    return groups;
}
const kB = n => (n / 1000).toFixed(2);
function main() {
    assert.ok(fs.existsSync(manifestPath), 'Run npm run build first (manifest required).');
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    const groups = analyze(manifest);
    const rows = Object.entries(groups).map(([name, files]) => {
        const size = measure(files, dist);
        return { group: name, files: files.length, 'JS kB': kB(size.raw), 'gzip kB': kB(size.gzip), 'gzip vs old single bundle': ((size.gzip / baseline.gzip - 1) * 100).toFixed(1) + '%' };
    });
    console.table(rows);
    console.log(`Baseline v1.2.7.1: ${kB(baseline.raw)} kB JS / ${kB(baseline.gzip)} kB gzip.`);
    console.log('Largest JavaScript files:');
    console.table(groups.total.map(file => ({file, bytes:fs.statSync(path.join(dist,file)).size})).sort((a,b)=>b.bytes-a.bytes).slice(0,10).map(x=>({file:x.file,'kB':kB(x.bytes)})));
    console.log('Lazy-feature manifest checks passed. Sizes are disk/gzip estimates, not browser timing.');
}
module.exports = { closure, filesFor, analyze, measure };
if (require.main === module) main();
