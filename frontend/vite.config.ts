import {defineConfig,loadEnv} from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig(({mode})=>{const env=loadEnv(mode,process.cwd(),'');const target=env.COURSEFLOW_BACKEND_URL||'http://localhost:8080';return {plugins:[react()],server:{port:5173,strictPort:true,proxy:{'/api':{target,changeOrigin:true},'/actuator':{target,changeOrigin:true}}}}});
