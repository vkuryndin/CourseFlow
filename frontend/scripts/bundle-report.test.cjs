const {test} = require('node:test');
const assert = require('node:assert/strict');
const {closure, filesFor, analyze} = require('./bundle-report.cjs');

const lazySources = ['src/modules/catalog.tsx','src/modules/identity.tsx','src/modules/schedule.tsx','src/modules/editor.tsx','src/modules/monitoring.tsx','src/shared/rich-editor.tsx','src/modules/dashboard/admin-dashboard.tsx'];
function fixture() {
    const m = {'index.html':{file:'assets/index.js',isEntry:true,imports:['common'],dynamicImports:lazySources},common:{file:'assets/common.js'}};
    lazySources.forEach((key,i)=>m[key]={file:`assets/lazy-${i}.js`,src:key,isDynamicEntry:true,imports:['common']});
    return m;
}
test('static closure ignores dynamic imports and counts shared chunks once',()=>{
    const m=fixture();
    assert.deepEqual([...closure(m,['index.html'])],['index.html','common']);
    assert.deepEqual(filesFor(m,['index.html',lazySources[0]]),['assets/index.js','assets/common.js','assets/lazy-0.js']);
});
test('bundle audit rejects expensive route leaking into anonymous startup',()=>{
    const m=fixture();
    assert.equal(analyze(m).catalog.length,3);
    m[lazySources[0]].imports.push(lazySources[2]);
    assert.throws(()=>analyze(m),/leaked into catalog/);
});
test('bundle audit rejects a missing dynamic entry',()=>{
    const m=fixture();
    delete m[lazySources[4]];
    assert.throws(()=>analyze(m),/Missing expected lazy module/);
});
