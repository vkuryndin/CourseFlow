import {useEffect,useState} from 'react';
import {Box,Button,Stack,Typography} from '@mui/material';
import {useEditor,EditorContent} from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import Image from '@tiptap/extension-image';
import Table from '@tiptap/extension-table';
import TableRow from '@tiptap/extension-table-row';
import TableHeader from '@tiptap/extension-table-header';
import TableCell from '@tiptap/extension-table-cell';
import Underline from '@tiptap/extension-underline';
import DOMPurify from 'dompurify';
import {safeUrl} from './api';
export const cleanHtml=(html:string)=>DOMPurify.sanitize(html,{USE_PROFILES:{html:true},FORBID_TAGS:['style','form','iframe','object','embed'],FORBID_ATTR:['style']});
export function SafeHtml({value}:{value:string}){return /<\/?[a-z][\s\S]*>/i.test(value||'')?<div className="rich-content" dangerouslySetInnerHTML={{__html:cleanHtml(value)}}/>:<Typography sx={{whiteSpace:'pre-wrap'}}>{value}</Typography>}
export function RichEditor({value,onChange}:{value:string;onChange:(v:string)=>void}){const [preview,P]=useState(false);const editor=useEditor({extensions:[StarterKit,Underline,Link.configure({openOnClick:false}),Image,Table,TableRow,TableHeader,TableCell],content:cleanHtml(value||''),onUpdate:({editor})=>onChange(cleanHtml(editor.getHTML()))});useEffect(()=>{if(editor&&editor.getHTML()!==value&&value==='')editor.commands.clearContent(false)},[value,editor]);if(!editor)return null;return <Box><Typography variant="body2">Содержание урока</Typography><Stack direction="row" flexWrap="wrap" gap={0.5} className="toolbar">{[['Жирный',()=>editor.chain().focus().toggleBold().run()],['Курсив',()=>editor.chain().focus().toggleItalic().run()],['Подчеркнуть',()=>editor.chain().focus().toggleUnderline().run()],['H2',()=>editor.chain().focus().toggleHeading({level:2}).run()],['Список',()=>editor.chain().focus().toggleBulletList().run()],['1. Список',()=>editor.chain().focus().toggleOrderedList().run()],['Цитата',()=>editor.chain().focus().toggleBlockquote().run()],['Код',()=>editor.chain().focus().toggleCodeBlock().run()],['Таблица',()=>editor.chain().focus().insertTable({rows:3,cols:3,withHeaderRow:true}).run()],['Ссылка',()=>{const u=safeUrl(prompt('URL')||'');if(u)editor.chain().focus().setLink({href:u}).run()}],['Изображение',()=>{const u=safeUrl(prompt('URL изображения')||'');if(u)editor.chain().focus().setImage({src:u}).run()}],['Отменить',()=>editor.chain().focus().undo().run()]] .map(([t,f])=><Button size="small" key={t as string} onClick={f as ()=>void}>{t as string}</Button>)}<Button onClick={()=>P(!preview)}>{preview?'Редактор':'Предпросмотр'}</Button></Stack>{preview?<SafeHtml value={value}/>:<EditorContent editor={editor} className="editor"/>}<Typography variant="caption">HTML: {(value||'').length} / 20 000 символов</Typography></Box>}
