import {Typography} from '@mui/material';
import DOMPurify from 'dompurify';
export const cleanHtml=(html:string)=>DOMPurify.sanitize(html,{USE_PROFILES:{html:true},FORBID_TAGS:['style','form','iframe','object','embed'],FORBID_ATTR:['style']});
export function SafeHtml({value}:{value:string}){return /<\/?[a-z][\s\S]*>/i.test(value||'')?<div className="rich-content" dangerouslySetInnerHTML={{__html:cleanHtml(value)}}/>:<Typography sx={{whiteSpace:'pre-wrap'}}>{value}</Typography>}
