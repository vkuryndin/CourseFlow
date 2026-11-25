import {describe,it,expect} from 'vitest';
import {nextPage,uniqueRows,rowKey} from '../src/shared/paging-utils';
const page=(content:any[],index=0,totalPages=2)=>({content,page:index,size:20,totalElements:totalPages*20,totalPages,first:index===0,last:index+1>=totalPages});
describe('Pagination',()=>{
it('requests next backend page and stops on the final page',()=>{expect(nextPage(page([],0))).toBe(1);expect(nextPage(page([],1))).toBeUndefined();expect(nextPage(page([],0,0))).toBeUndefined()});
it('keeps prior records and removes duplicate IDs',()=>expect(uniqueRows([{id:1,title:'old'},{id:2},{id:1,title:'new'}],rowKey)).toEqual([{id:1,title:'new'},{id:2}]));
});
