// Additional structural checks. The existing architecture.test.mjs is retained verbatim.
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const ts=require('typescript');
const root=fs.realpathSync(path.resolve(__dirname,'..')),src=path.join(root,'src');
const configPath=path.join(root,'tsconfig.json');
const config=ts.readConfigFile(configPath,ts.sys.readFile);
assert.ok(!config.error,'Cannot read frontend tsconfig.json');
const options=ts.parseJsonConfigFileContent(config.config,ts.sys,root).options;
const canonical=p=>{const normalized=path.normalize(p);return ts.sys.useCaseSensitiveFileNames?normalized:normalized.toLowerCase()};
const walk=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(d,e.name)):[path.join(d,e.name)]);
const rel=p=>path.relative(src,p).split(path.sep).join('/');
const files=walk(src).filter(p=>/\.tsx?$/.test(p));
const ast=p=>ts.createSourceFile(p,fs.readFileSync(p,'utf8'),ts.ScriptTarget.Latest,true,p.endsWith('.tsx')?ts.ScriptKind.TSX:ts.ScriptKind.TS);
function imports(s){const result=[];function visit(n){if((ts.isImportDeclaration(n)||ts.isExportDeclaration(n))&&n.moduleSpecifier&&ts.isStringLiteral(n.moduleSpecifier))result.push(n.moduleSpecifier.text);if(ts.isCallExpression(n)&&n.expression.kind===ts.SyntaxKind.ImportKeyword&&n.arguments[0]&&ts.isStringLiteral(n.arguments[0]))result.push(n.arguments[0].text);ts.forEachChild(n,visit)}visit(s);return result}
const indexedFiles=new Map([...walk(src),path.join(root,'package.json')].map(p=>[canonical(p),p]));
function resolve(f,s){
  const resolved=ts.resolveModuleName(s,f,options,ts.sys).resolvedModule?.resolvedFileName;
  const p=path.resolve(path.dirname(f),s);
  const candidates=[resolved,p,p+'.ts',p+'.tsx',p+'.json',p+'.css',path.join(p,'index.ts'),path.join(p,'index.tsx')].filter(Boolean);
  for(const candidate of candidates){
    const known=indexedFiles.get(canonical(candidate));
    if(known&&ts.sys.fileExists(known))return known;
    if(ts.sys.fileExists(candidate))return path.normalize(candidate);
  }
  return undefined;
}
const edges=new Map(files.map(f=>[f,imports(ast(f)).filter(s=>s.startsWith('.')).map(s=>({s,to:resolve(f,s)}))]));
const moduleName=p=>p.split('/')[1].replace(/\.tsx?$/,'');
const approved=new Set(['dashboard:teaching','monitoring:system-info','schedule:learning','catalog:teacher-search']);
test('Every local import and re-export resolves; architectural layers and module entrypoints are respected',()=>{const failures=[];for(const [f,es] of edges)for(const {s,to} of es){const a=rel(f);if(!to){failures.push(`${a} -> ${s}: unresolved (frontend root: ${root})`);continue}const b=rel(to);const la=a.split('/')[0],lb=b.split('/')[0];let valid=la==='main.tsx'?lb==='app'||b==='style.css':la==='app'?['app','shared','modules','assets'].includes(lb):la==='shared'?['shared','assets'].includes(lb):la==='modules'?['shared','assets'].includes(lb)||lb==='modules'&&(moduleName(a)===moduleName(b)||approved.has(moduleName(a)+':'+moduleName(b))&&a.split('/').length===2&&b.split('/').length===2)||b==='../../package.json'||to===path.join(root,'package.json'):true;if(la==='app'&&lb==='modules'&&b.split('/').length>2)valid=false;if(!valid)failures.push(`${a} -> ${b}`)}assert.deepEqual(failures,[])});
test('No circular local module dependencies',()=>{const active=new Set(),done=new Set();function visit(f,stack=[]){assert.ok(!active.has(f),stack.concat(f).map(rel).join(' -> '));if(done.has(f))return;active.add(f);for(const e of edges.get(f)||[])if(edges.has(e.to))visit(e.to,stack.concat(f));active.delete(f);done.add(f)}for(const f of files)visit(f)});
test('Browser HTTP transport remains centralized in shared/api.ts',()=>{const errors=[];for(const f of files){if(rel(f)==='shared/api.ts')continue;const s=ast(f);if(imports(s).some(n=>n==='axios'||n.startsWith('axios/')))errors.push(rel(f)+': axios');function visit(n){if(ts.isCallExpression(n)){const name=n.expression.getText(s);if(/^(?:fetch|(?:window|globalThis|self)\.fetch|navigator\.sendBeacon)$/.test(name))errors.push(rel(f)+': '+name)}if(ts.isNewExpression(n)&&/^(?:(?:window|globalThis|self)\.)?(?:XMLHttpRequest|WebSocket|EventSource)$/.test(n.expression.getText(s)))errors.push(rel(f)+': '+n.expression.getText(s));ts.forEachChild(n,visit)}visit(s)}assert.deepEqual(errors,[])});
test('Executable test and source file names are functional, not patch-version based',()=>{const names=[...walk(src),...walk(path.join(root,'tests')),...walk(path.join(root,'e2e'))].filter(p=>/\.(?:tsx?|css)$/.test(p));assert.deepEqual(names.filter(p=>/^(?:ui-\d+|.+-12\d|.+-110|.+-102)\./i.test(path.basename(p))).map(p=>path.relative(root,p)),[])});
