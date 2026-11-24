import {test,expect} from '@playwright/test';
const future=new Date(Date.now()+3600000).toISOString();
const session={accessToken:'mock-token',tokenType:'Bearer',expiresAt:future,userId:1,displayName:'Admin',roles:['ADMIN'],passwordChangeRequired:false};
const snapshot={collectedAt:'2026-11-24T12:00:00Z',cacheSeconds:60,
 application:{name:'courseflow',version:'0.1.0-SNAPSHOT',springBootVersion:'4.1.2',hibernateVersion:'7.x',activeProfiles:['dev'],defaultProfiles:['default'],schemaManagement:'update',buildTime:null,gitCommit:null},
 runtime:{hostName:'test-host',javaVersion:'17',javaVendor:'OpenJDK',jvmName:'OpenJDK',osName:'Linux',osVersion:'6',architecture:'amd64',availableProcessors:4,totalMemoryBytes:8589934592,freeMemoryBytes:4294967296,jvmStartedAt:'2026-11-24T11:00:00Z',uptimeSeconds:3600,timeZone:'Asia/Yerevan'},
 database:{status:'CONNECTED',product:'PostgreSQL',version:'18.1',driver:'PostgreSQL JDBC Driver',driverVersion:'42.7.8',serverAddress:'192.0.2.10',serverPort:5432,databaseName:'courseflow',databaseUser:'appuser',schema:'public',sizeBytes:1048576,tableCount:22,queryTimeMillis:4},
 pool:{name:'HikariPool-1',minimumIdle:2,maximumSize:10,connectionTimeoutMillis:5000,active:3,idle:7,pending:0},
 mail:{host:'smtp.example.invalid',port:587,startTlsEnabled:true,startTlsRequired:true,sslEnabled:false,status:'NOT_CHECKED'}};
test('administrator sees actual system info from API; existing tabs remain',async({page})=>{
 await page.addInitScript(s=>{sessionStorage.setItem('courseflow.session',JSON.stringify(s));sessionStorage.setItem('courseflow.role','ADMIN')},session);
 await page.route('**/actuator/**',route=>route.fulfill({json:{names:[],status:'UP'}}));
 await page.route('**/api/system/status',route=>route.fulfill({json:{application:'CourseFlow',status:'UP',database:'CONNECTED'}}));
 await page.route('**/api/system/info',route=>route.fulfill({json:{snapshot,serverEndpoint:{localAddress:'127.0.0.1',localPort:8080},accessTokenLifetimeSeconds:3600}}));
 await page.goto('/admin/monitoring');
 await page.getByRole('tab',{name:'Информация о системе'}).click();
 await expect(page.getByText('192.0.2.10',{exact:true})).toBeVisible();
 await expect(page.getByText('appuser',{exact:true})).toBeVisible();
 await expect(page.getByText('Не выполнялась',{exact:true})).toBeVisible();
 await expect(page.getByRole('tab',{name:'Метрики CourseFlow'})).toBeVisible();
 await page.getByRole('tab',{name:'Все метрики'}).click();
 await expect(page.getByLabel('Поиск метрики')).toBeVisible();
});
test('student cannot open admin monitoring',async({page})=>{
 await page.addInitScript(s=>{sessionStorage.setItem('courseflow.session',JSON.stringify({...s,roles:['STUDENT']}));sessionStorage.setItem('courseflow.role','STUDENT')},session);
 await page.goto('/admin/monitoring');
 await expect(page).toHaveURL(/\/403$/);
});
