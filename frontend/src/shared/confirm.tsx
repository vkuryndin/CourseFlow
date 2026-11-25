import {createContext,useContext,useRef,useState,type ReactNode} from 'react';
import {Button,Dialog,DialogActions,DialogContent,DialogContentText,DialogTitle,Typography} from '@mui/material';
const Context=createContext<(message:string)=>Promise<boolean>>(async message=>window.confirm(message));
export const useDeleteConfirm=()=>useContext(Context);
export function ConfirmProvider({children}:{children:ReactNode}){
 const [message,M]=useState(''),resolve=useRef<((ok:boolean)=>void)|null>(null),highlight=useRef<HTMLElement|null>(null);
 function finish(ok:boolean){const fn=resolve.current;resolve.current=null;highlight.current?.classList.remove('delete-target');highlight.current=null;M('');fn?.(ok)}
 function ask(text:string){if(resolve.current)return Promise.resolve(false);highlight.current=(document.activeElement?.closest('tr,.tree-label')||document.querySelector('.tree-label.selected')) as HTMLElement|null;highlight.current?.classList.add('delete-target');M(text);return new Promise<boolean>(done=>{resolve.current=done})}
 return <Context.Provider value={ask}>{children}<Dialog open={!!message} onClose={()=>finish(false)} maxWidth="sm" fullWidth><DialogTitle>{message.startsWith('Удалить')?'Подтверждение удаления':'Подтверждение действия'}</DialogTitle><DialogContent><DialogContentText sx={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{message}</DialogContentText><Typography mt={2} color="text.secondary" variant="body2">Проверьте, что выбран нужный объект.</Typography></DialogContent><DialogActions><Button autoFocus onClick={()=>finish(false)}>Отмена</Button><Button color="error" variant="contained" onClick={()=>finish(true)}>{message.startsWith('Удалить')?'Удалить':'Подтвердить'}</Button></DialogActions></Dialog></Context.Provider>
}
