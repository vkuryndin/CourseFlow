import {useEffect,useState} from 'react';
import {Autocomplete,Box,Button,CircularProgress,TextField} from '@mui/material';
import {Data,useGet} from './api';
import {usePaged,uniqueRows} from './infinite';
export type SearchItem={id:number;[key:string]:unknown};
type Props<T extends SearchItem>={path:string;label:string;value:T|null;onChange:(value:T|null)=>void;name:(item:T)=>string;params?:Data;placeholder?:string;loadingText?:string;emptyText?:string;errorText?:string;disabled?:boolean;required?:boolean;error?:boolean;helperText?:string;excludeIds?:readonly number[];fullWidth?:boolean};
export function ServerSearch<T extends SearchItem>({path,label,value,onChange,name,params={},placeholder='Все',loadingText='Загрузка…',emptyText='Ничего не найдено',errorText='Не удалось загрузить список',disabled,required,error,helperText,excludeIds=[],fullWidth}:Props<T>){
  const [open,setOpen]=useState(false),[input,setInput]=useState(''),[term,setTerm]=useState(''),[search,setSearch]=useState('');
  const selectedLabel=value?name(value):'';
  useEffect(()=>{setInput(selectedLabel);setTerm('');setSearch('')},[value?.id,selectedLabel]);
  useEffect(()=>{const t=setTimeout(()=>setSearch(term.trim()),350);return()=>clearTimeout(t)},[term]);
  const waiting=term.trim()!==search,query=usePaged<T>(path,{...params,q:search},open&&!waiting);
  const results=waiting?[]:uniqueRows(query.data?.pages.flatMap(p=>p.content)??[],t=>t.id).filter(t=>!excludeIds.includes(t.id));
  const opts=value&&!results.some(t=>t.id===value.id)?[value,...results]:results;
  const more=()=>{if(!waiting&&query.hasNextPage&&!query.isFetching&&!query.isFetchNextPageError)void query.fetchNextPage({cancelRefetch:false})};
  const failed=!waiting&&query.isError;
  // If excluded options leave an empty page or a list too short to scroll, continue until usable options exist.
  useEffect(()=>{if(open&&results.length<8&&query.hasNextPage&&!query.isFetching&&!query.isFetchNextPageError&&!waiting)more()},[open,results.length,query.hasNextPage,query.isFetching,query.isFetchNextPageError,waiting]);
  return <Box sx={{minWidth:fullWidth?0:{xs:'100%',sm:250},width:fullWidth?'100%':undefined}}><Autocomplete<T> size="small" disabled={disabled} open={open} value={value} inputValue={input} options={opts}
    onOpen={()=>setOpen(true)} onClose={()=>{setOpen(false);setTerm('');setSearch('');setInput(selectedLabel)}}
    onInputChange={(_,text,reason)=>{setInput(text);if(reason==='input')setTerm(text);else if(reason==='clear'){setTerm('');setSearch('')}}}
    onChange={(_,item)=>{onChange(item);setInput(item?name(item):'');setTerm('');setSearch('')}} getOptionLabel={name} getOptionKey={t=>t.id} isOptionEqualToValue={(a,b)=>a.id===b.id}
    filterOptions={()=>results} loading={waiting||query.isPending||query.isFetchingNextPage} loadingText={loadingText} noOptionsText={failed?errorText:emptyText} clearText="Очистить выбор" openText="Открыть" closeText="Закрыть"
    ListboxProps={{style:{maxHeight:280},onScroll:event=>{const el=event.currentTarget;if(el.scrollHeight-el.scrollTop-el.clientHeight<70)more()}}}
    onHighlightChange={(_,item,reason)=>{if(reason==='keyboard'&&item&&results.findIndex(t=>t.id===item.id)>=results.length-3)more()}}
    renderInput={p=><TextField {...p} label={label} placeholder={placeholder} required={required} error={error||failed} helperText={helperText||(failed?errorText:undefined)} inputProps={{...p.inputProps,maxLength:120,title:selectedLabel||undefined}} InputProps={{...p.InputProps,endAdornment:<>{open&&(waiting||query.isFetching)&&<CircularProgress size={16} color="inherit"/>}{p.InputProps.endAdornment}</>}}/>}/>
  {failed&&<Button size="small" onMouseDown={e=>e.preventDefault()} onClick={()=>{setOpen(true);if(query.isFetchNextPageError)void query.fetchNextPage({cancelRefetch:false});else void query.refetch()}}>Повторить загрузку</Button>}</Box>
}
type NamedItem=SearchItem&{name?:string;displayName?:string;title?:string};
export function NamedSearch({path,id,onChange,label,field='name',params,disabled,required,error,helperText,excludeIds,fullWidth,placeholder}:{path:string;id:string|number|null|undefined;onChange:(id:string|number)=>void;label:string;field?:'name'|'displayName'|'title';params?:Data;disabled?:boolean;required?:boolean;error?:boolean;helperText?:string;excludeIds?:readonly number[];fullWidth?:boolean;placeholder?:string}){
  const [picked,P]=useState<NamedItem|null>(null);const detail=useGet<NamedItem>(`${path}/${id}`,{},!!id&&String(picked?.id)!==String(id));
  const value=!id?null:String(picked?.id)===String(id)?picked:detail.data&&String(detail.data.id)===String(id)?detail.data:null;
  return <ServerSearch<NamedItem> path={path} label={label} name={item=>String(item[field]??'')} value={value} onChange={v=>{P(v);onChange(v?.id??'')}} params={params} disabled={disabled} required={required} error={error||detail.isError} helperText={helperText||(detail.isError?'Не удалось получить выбранный объект':undefined)} excludeIds={excludeIds} fullWidth={fullWidth} placeholder={placeholder}/>;
}
